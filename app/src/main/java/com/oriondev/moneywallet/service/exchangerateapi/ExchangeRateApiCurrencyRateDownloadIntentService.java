/*
 * Copyright (c) 2026.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.service.exchangerateapi;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.service.AbstractCurrencyRateDownloadIntentService;
import com.oriondev.moneywallet.utils.CurrencyManager;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;

/**
 * Downloads the daily exchange rates from the open (key-less) endpoint of ExchangeRate-API
 * (https://www.exchangerate-api.com/docs/free). All rates are relative to USD, exactly like
 * the OpenExchangeRates provider, so the two providers can share the same local cache.
 */
public class ExchangeRateApiCurrencyRateDownloadIntentService extends AbstractCurrencyRateDownloadIntentService {

    private static final String ENDPOINT = "https://open.er-api.com/v6/latest/USD";

    public ExchangeRateApiCurrencyRateDownloadIntentService() {
        super("ExchangeRateApiCurrencyRateDownloadIntentService");
    }

    @Override
    protected void updateExchangeRates() throws Exception {
        setCurrentProgress(getString(R.string.notification_title_download_exchange_rates), 0);
        JSONObject response = downloadLatestExchangeRates();
        if ("success".equals(response.optString("result"))) {
            storeResponse(response);
        } else {
            throw new IOException(response.optString("error-type", "unknown"));
        }
    }

    private JSONObject downloadLatestExchangeRates() throws Exception {
        InputStream inputStream = null;
        try {
            URL url = new URL(ENDPOINT);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(10000 /* milliseconds */);
            connection.setConnectTimeout(15000 /* milliseconds */);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.connect();
            inputStream = connection.getInputStream();
            return new JSONObject(IOUtils.toString(inputStream, "UTF-8"));
        } catch (IOException e) {
            throw new IOException(getString(R.string.message_error_network_connection));
        } catch (JSONException e) {
            throw new JSONException(getString(R.string.message_error_invalid_response));
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private void storeResponse(JSONObject response) throws Exception {
        long timestamp = response.optLong("time_last_update_unix", System.currentTimeMillis() / 1000L);
        JSONObject rates = response.getJSONObject("rates");
        Collection<CurrencyUnit> currencies = CurrencyManager.getCurrencies();
        float progress = 35;
        float offset = (100f - progress) / Math.max(1, currencies.size());
        for (CurrencyUnit currency : currencies) {
            progress += offset;
            setCurrentProgress(getString(R.string.notification_content_caching_data), (int) progress);
            if (rates.has(currency.getIso())) {
                double rate = rates.getDouble(currency.getIso());
                storeExchangeRate(currency, rate, timestamp);
            }
        }
    }
}
