package com.oriondev.moneywallet.next.data.currency

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oriondev.moneywallet.next.WalletApp
import com.oriondev.moneywallet.next.data.db.ExchangeRateDao
import com.oriondev.moneywallet.next.data.db.ExchangeRateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Key-less daily rates from the open ExchangeRate-API endpoint (all rates relative to USD).
 * Falls back to the frankfurter.app ECB feed when the first provider is unreachable.
 */
class ExchangeRateFetcher {
    suspend fun fetchUsdRates(): Pair<Map<String, Double>, Long> = withContext(Dispatchers.IO) {
        try {
            val json = get("https://open.er-api.com/v6/latest/USD")
            val o = JSONObject(json)
            if (o.optString("result") != "success") error(o.optString("error-type", "unknown"))
            val rates = o.getJSONObject("rates")
            val map = rates.keys().asSequence().associateWith { rates.getDouble(it) }
            sanitize(map) to o.optLong("time_last_update_unix", System.currentTimeMillis() / 1000) * 1000
        } catch (first: Exception) {
            val json = get("https://api.frankfurter.app/latest?from=USD")
            val o = JSONObject(json)
            val rates = o.getJSONObject("rates")
            val map = rates.keys().asSequence().associateWith { rates.getDouble(it) } + ("USD" to 1.0)
            // ECB publishes once per working day; stamp the rates with that date, not the fetch time
            val published = com.oriondev.moneywallet.next.core.Dates.parseSqlDate(o.optString("date")) ?: System.currentTimeMillis()
            sanitize(map) to published
        }
    }

    /** A zero, negative or non-finite rate would turn every conversion into garbage (1/0 = ∞). */
    internal fun sanitize(map: Map<String, Double>): Map<String, Double> {
        val clean = map.filterValues { it.isFinite() && it > 0.0 }
        if (clean["USD"]?.let { kotlin.math.abs(it - 1.0) > 1e-9 } == true) error("rates are not USD-based")
        if (clean.size < 2) error("no usable rates")
        return clean
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.requestMethod = "GET"
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}

class RatesRepository(private val dao: ExchangeRateDao, private val fetcher: ExchangeRateFetcher = ExchangeRateFetcher()) {
    val usdRates = dao.observeAll()

    /** Upserts the fetched rates; currencies the provider did not return keep their older rate and timestamp. */
    suspend fun refresh(): Boolean = try {
        val (map, ts) = fetcher.fetchUsdRates()
        dao.upsertAll(map.map { ExchangeRateEntity(it.key, it.value, ts) })
        true
    } catch (e: Exception) {
        false
    }
}

class RatesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WalletApp
        val ok = app.container.rates.refresh()
        if (ok) app.container.prefs.setRatesLastUpdate(System.currentTimeMillis())
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "rates-daily",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RatesWorker>(12, TimeUnit.HOURS).setConstraints(constraints).build(),
            )
        }

        fun refreshNow(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            // unique + KEEP: every cold start (widget, boot, alarm) would otherwise queue another fetch
            WorkManager.getInstance(context).enqueueUniqueWork(
                "rates-now", androidx.work.ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RatesWorker>().setConstraints(constraints).build(),
            )
        }
    }
}
