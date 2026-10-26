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

package com.oriondev.moneywallet.ui.view.theme;

/**
 * This interface implements all the colors of a theme.
 */
public interface ITheme {

    int getColorPrimary();

    int getColorPrimaryDark();

    int getColorAccent();

    ThemeEngine.Mode getMode();

    boolean isDark();

    int getTextColorPrimary();

    int getTextColorSecondary();

    int getTextColorPrimaryInverse();

    int getTextColorSecondaryInverse();

    int getColorCardBackground();

    int getColorWindowForeground();

    int getColorWindowBackground();

    int getColorRipple();

    int getIconColor();

    int getHintTextColor();

    int getErrorColor();

    /** M3 primary-container role derived from the primary color (FAB, drawer header, chips). */
    int getColorPrimaryContainer();

    /** M3 on-primary-container role (text/icons over {@link #getColorPrimaryContainer()}). */
    int getColorOnPrimaryContainer();

    /** M3 surface-container role (section headers, tonal panels). */
    int getColorSurfaceContainer();

    /** M3 outline-variant role (dividers, decorative strokes). */
    int getColorOutlineVariant();

    int getDialogBackgroundColor();

    int getDrawerBackgroundColor();

    int getDrawerIconColor();

    int getDrawerSelectedIconColor();

    int getDrawerTextColor();

    int getDrawerSelectedTextColor();

    int getDrawerSelectedItemColor();

    int getBestColor(int background);

    int getBestTextColor(int background);

    int getBestHintColor(int background);

    int getBestIconColor(int background);
}