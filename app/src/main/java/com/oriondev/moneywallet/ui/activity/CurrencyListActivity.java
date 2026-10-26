/*
 * Copyright (c) 2018.
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

package com.oriondev.moneywallet.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.annotation.StringRes;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.ui.activity.base.SinglePanelSimpleListActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter;
import com.oriondev.moneywallet.ui.adapter.recycler.CurrencyCursorAdapter;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.ui.view.theme.ITheme;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.utils.CurrencyManager;

/**
 * Created by andrea on 03/02/18.
 */
public class CurrencyListActivity extends SinglePanelSimpleListActivity implements CurrencyCursorAdapter.CurrencyActionListener {

    public static final String ACTIVITY_MODE = "CurrencyListActivity::ActivityMode";
    public static final String RESULT_CURRENCY = "CurrencyListActivity::Result::SelectedCurrency";

    public static final int CURRENCY_MANAGER = 0;
    public static final int CURRENCY_PICKER = 1;

    private int mActivityMode;
    private String mSearchQuery;

    @Override
    protected void onPrepareRecyclerView(AdvancedRecyclerView recyclerView) {
        Intent intent = getIntent();
        if (intent != null) {
            mActivityMode = intent.getIntExtra(ACTIVITY_MODE, CURRENCY_MANAGER);
        } else {
            mActivityMode = CURRENCY_MANAGER;
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setEmptyText(R.string.message_no_currency_found);
    }

    @Override
    protected AbstractCursorAdapter onCreateAdapter() {
        return new CurrencyCursorAdapter(this);
    }

    @Override
    @StringRes
    protected int getActivityTitleRes() {
        return R.string.title_activity_currency_list;
    }

    @Override
    @MenuRes
    protected int onInflateMenu() {
        return R.menu.menu_currency_list;
    }

    @Override
    protected void onMenuCreated(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null && searchItem.getActionView() instanceof SearchView) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            searchView.setQueryHint(getString(R.string.hint_search_currency));
            // the search box sits on the colored toolbar: use readable colors on that background
            ITheme theme = ThemeEngine.getTheme();
            int textColor = theme.getBestTextColor(theme.getColorPrimary());
            int hintColor = theme.getBestHintColor(theme.getColorPrimary());
            android.widget.EditText editText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (editText != null) {
                editText.setTextColor(textColor);
                editText.setHintTextColor(hintColor);
            }
            android.widget.ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
            if (closeButton != null) {
                closeButton.setColorFilter(textColor);
            }
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    String query = newText != null ? newText.trim() : null;
                    if (!TextUtils.equals(query, mSearchQuery)) {
                        mSearchQuery = query;
                        reloadList();
                    }
                    return true;
                }

            });
        }
    }

    @Override
    protected boolean isFloatingActionButtonEnabled() {
        return mActivityMode == CURRENCY_MANAGER;
    }

    @Override
    protected void onFloatingActionButtonClick() {
        Intent intent = new Intent(this, NewEditCurrencyActivity.class);
        intent.putExtra(NewEditCurrencyActivity.MODE, NewEditCurrencyActivity.Mode.NEW_ITEM);
        startActivity(intent);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri uri = DataContentProvider.CONTENT_CURRENCIES;
        String[] projection = new String[] {
                Contract.Currency.ISO,
                Contract.Currency.NAME,
                Contract.Currency.SYMBOL,
                Contract.Currency.DECIMALS,
                Contract.Currency.FAVOURITE
        };
        // favourite currencies first, then alphabetically; optionally filtered by the search box
        String sortBy = Contract.Currency.FAVOURITE + " DESC, " + Contract.Currency.NAME + " ASC";
        String selection = null;
        String[] selectionArgs = null;
        if (!TextUtils.isEmpty(mSearchQuery)) {
            selection = Contract.Currency.NAME + " LIKE ? OR " + Contract.Currency.ISO + " LIKE ? OR " + Contract.Currency.SYMBOL + " LIKE ?";
            String like = "%" + mSearchQuery + "%";
            selectionArgs = new String[] {like, like, like};
        }
        return new CursorLoader(this, uri, projection, selection, selectionArgs, sortBy);
    }

    @Override
    public void onCurrencyClick(String iso) {
        if (mActivityMode == CURRENCY_MANAGER) {
            Intent intent = new Intent(this, NewEditCurrencyActivity.class);
            intent.putExtra(NewEditCurrencyActivity.MODE, NewEditCurrencyActivity.Mode.EDIT_ITEM);
            intent.putExtra(NewEditCurrencyActivity.ISO, iso);
            startActivity(intent);
        } else if (mActivityMode == CURRENCY_PICKER) {
            CurrencyUnit currency = CurrencyManager.getCurrency(iso);
            Intent intent = new Intent();
            intent.putExtra(RESULT_CURRENCY, currency);
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public void onCurrencyFavourite(String iso, boolean newValue) {
        // TODO handle it properly: if we update the database directly than the uri will be notify
        // TODO to be changed and the content provider will re-query the database. At the end the
        // TODO adapter will be refreshed and the new item will be at the top of the list.
        // TODO we need a wrapper to keep the data static until a refresh occurs.
    }
}