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

package com.oriondev.moneywallet.utils;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.ExchangeRate;
import com.oriondev.moneywallet.model.Money;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import java.util.Map;

/**
 * Small helper that converts amounts (stored as long values in the minor unit of a
 * currency) between currencies, taking care of the different number of decimals.
 */
public class CurrencyConverter {

    private CurrencyConverter() {
        // static helper
    }

    /**
     * Convert an amount from one currency to another using the given exchange rate.
     * @param from currency of the amount.
     * @param to currency of the result.
     * @param money amount expressed in the minor unit of 'from'.
     * @param rate value of 1 unit of 'from' expressed in 'to'.
     * @return the converted amount expressed in the minor unit of 'to'.
     */
    public static long convert(@NonNull CurrencyUnit from, @NonNull CurrencyUnit to, long money, double rate) {
        if (TextUtils.equals(from.getIso(), to.getIso())) {
            return money;
        }
        double value = money / Math.pow(10d, from.getDecimals());
        double converted = value * rate;
        return Math.round(converted * Math.pow(10d, to.getDecimals()));
    }

    /**
     * Convert an amount using the exchange rate currently stored in the local cache.
     * @return the converted amount or null if no exchange rate is known.
     */
    @Nullable
    public static Long convert(@Nullable CurrencyUnit from, @Nullable CurrencyUnit to, long money) {
        if (from == null || to == null) {
            return null;
        }
        if (TextUtils.equals(from.getIso(), to.getIso())) {
            return money;
        }
        Double rate = getRate(from, to);
        if (rate == null) {
            return null;
        }
        return convert(from, to, money, rate);
    }

    /**
     * @return the cached exchange rate (1 'from' = x 'to') or null if unknown.
     */
    @Nullable
    public static Double getRate(@Nullable CurrencyUnit from, @Nullable CurrencyUnit to) {
        if (from == null || to == null) {
            return null;
        }
        if (TextUtils.equals(from.getIso(), to.getIso())) {
            return 1d;
        }
        ExchangeRate exchangeRate = CurrencyManager.getExchangeRate(from, to);
        return exchangeRate != null ? exchangeRate.getRate() : null;
    }

    /**
     * Collapse a multi-currency amount into a single currency.
     * @param money multi-currency amount.
     * @param target currency of the result.
     * @return a Money with a single currency, or null if at least one rate is missing.
     */
    @Nullable
    public static Money convertToSingleCurrency(@Nullable Money money, @Nullable CurrencyUnit target) {
        if (money == null || target == null) {
            return null;
        }
        long total = 0L;
        for (Map.Entry<String, Long> entry : money.getCurrencyMoneys().entrySet()) {
            CurrencyUnit from = CurrencyManager.getCurrency(entry.getKey());
            Long converted = convert(from, target, entry.getValue());
            if (converted == null) {
                return null;
            }
            total += converted;
        }
        return new Money(target.getIso(), total);
    }

    /**
     * Try to collapse a multi-currency amount into the user base currency. If the amount
     * is already single-currency, if no base currency is configured or if a rate is missing,
     * the original amount is returned untouched.
     */
    @NonNull
    public static Money toBaseCurrencyIfMixed(@NonNull Money money) {
        if (money.getNumberOfCurrencies() <= 1) {
            return money;
        }
        CurrencyUnit base = getBaseCurrency();
        if (base == null) {
            return money;
        }
        Money converted = convertToSingleCurrency(money, base);
        return converted != null ? converted : money;
    }

    /**
     * @return the user selected base ("global") currency, falling back to the default
     * currency of the device locale when nothing is configured.
     */
    @Nullable
    public static CurrencyUnit getBaseCurrency() {
        String iso = PreferenceManager.getBaseCurrency();
        CurrencyUnit unit = iso != null ? CurrencyManager.getCurrency(iso) : null;
        if (unit == null) {
            unit = CurrencyManager.getDefaultCurrency();
        }
        return unit;
    }
}
