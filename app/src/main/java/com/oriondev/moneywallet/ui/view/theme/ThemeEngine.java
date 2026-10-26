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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import androidx.annotation.UiThread;
import androidx.core.graphics.ColorUtils;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible to dynamically theme the user interface at runtime.
 */
public class ThemeEngine implements ITheme {

    private static final String FILE_NAME = "theme.config";
    private static final String COLOR_PRIMARY = "color_primary";
    private static final String COLOR_PRIMARY_DARK = "color_primary_dark";
    private static final String COLOR_ACCENT = "color_accent";
    private static final String MODE = "mode";

    private static final int DEFAULT_COLOR_PRIMARY = Color.parseColor("#006C4C");
    private static final int DEFAULT_COLOR_PRIMARY_DARK = Color.parseColor("#005236");
    private static final int DEFAULT_COLOR_ACCENT = Color.parseColor("#3D6373");
    private static final Mode DEFAULT_MODE = Mode.LIGHT;

    private static final int[] DEFAULT_TEXT_COLOR_PRIMARY = new int[] {
            Color.parseColor("#171D1A"),
            Color.parseColor("#DEE4DE"),
            Color.parseColor("#DEE4DE")
    };

    private static final int[] DEFAULT_TEXT_COLOR_PRIMARY_INVERSE = new int[] {
            Color.parseColor("#ECF2EC"),
            Color.parseColor("#171D1A"),
            Color.parseColor("#171D1A")
    };

    private static final int[] DEFAULT_TEXT_COLOR_SECONDARY = new int[] {
            Color.parseColor("#404943"),
            Color.parseColor("#BFC9C2"),
            Color.parseColor("#BFC9C2")
    };

    private static final int[] DEFAULT_TEXT_COLOR_SECONDARY_INVERSE = new int[] {
            Color.parseColor("#BFC9C2"),
            Color.parseColor("#404943"),
            Color.parseColor("#404943")
    };

    private static final int[] DEFAULT_ICON_COLOR = new int[] {
            Color.parseColor("#404943"),
            Color.parseColor("#BFC9C2"),
            Color.parseColor("#BFC9C2")
    };

    private static final int[] DEFAULT_HINT_TEXT_COLOR = new int[] {
            Color.parseColor("#707973"),
            Color.parseColor("#89938C"),
            Color.parseColor("#89938C")
    };

    private static final int[] DEFAULT_COLOR_CARD_BACKGROUND = new int[] {
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#1B211E"),
            Color.parseColor("#0A0F0D")
    };

    private static final int[] DEFAULT_COLOR_WINDOW_FOREGROUND = new int[] {
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#171D1A"),
            Color.parseColor("#000000")
    };

    private static final int[] DEFAULT_COLOR_WINDOW_BACKGROUND = new int[] {
            Color.parseColor("#F5FBF5"),
            Color.parseColor("#0F1512"),
            Color.parseColor("#000000")
    };

    private static final int[] DEFAULT_COLOR_RIPPLE = new int[] {
            Color.parseColor("#1f171D1A"),
            Color.parseColor("#33DEE4DE"),
            Color.parseColor("#33DEE4DE")
    };

    private static final int[] DEFAULT_DIALOG_BACKGROUND_COLOR = new int[] {
            Color.parseColor("#E3EAE4"),
            Color.parseColor("#252B28"),
            Color.parseColor("#1B211E")
    };

    private static final int[] DRAWER_BACKGROUND_COLOR = new int[] {
            Color.parseColor("#EFF5EF"),
            Color.parseColor("#171D1A"),
            Color.parseColor("#0A0F0D")
    };

    private static final int[] DRAWER_ICON_COLOR = new int[] {
            Color.parseColor("#404943"),
            Color.parseColor("#BFC9C2"),
            Color.parseColor("#BFC9C2")
    };

    private static final int[] DRAWER_TEXT_COLOR = new int[] {
            Color.parseColor("#171D1A"),
            Color.parseColor("#DEE4DE"),
            Color.parseColor("#DEE4DE")
    };

    private static final int[] DRAWER_SELECTED_ITEM_COLOR = new int[] {
            Color.parseColor("#E9EFE9"),
            Color.parseColor("#303633"),
            Color.parseColor("#252B28")
    };

    private static final int[] DEFAULT_COLOR_SURFACE_CONTAINER = new int[] {
            Color.parseColor("#E9EFE9"), // surface-container (light)
            Color.parseColor("#1B211E"), // surface-container (dark)
            Color.parseColor("#131917")  // surface-container (deep dark)
    };

    private static final int[] DEFAULT_COLOR_OUTLINE_VARIANT = new int[] {
            Color.parseColor("#BFC9C2"),
            Color.parseColor("#404943"),
            Color.parseColor("#404943")
    };

    private static final int[] DEFAULT_COLOR_ERROR = new int[] {
            Color.parseColor("#BA1A1A"),
            Color.parseColor("#FFB4AB"),
            Color.parseColor("#FFB4AB")
    };

    private static final int INDEX_MODE_LIGHT = 0;
    private static final int INDEX_MODE_DARK = 1;
    private static final int INDEX_MODE_DEEP_DARK = 2;

    private static final List<ThemeObserver> mThemeObserverList = new ArrayList<>();

    private static ThemeEngine sInstance;

    public static void initialize(Context context) {
        if (sInstance == null) {
            sInstance = new ThemeEngine(context);
        }
    }

    public static void registerObserver(ThemeObserver observer) {
        mThemeObserverList.add(observer);
    }

    public static void unregisterObserver(ThemeObserver observer) {
        mThemeObserverList.remove(observer);
    }

    @UiThread
    public static void setColorPrimary(int colorPrimary) {
        if (sInstance != null) {
            if (colorPrimary != sInstance.getColorPrimary()) {
                sInstance.mPreferences.edit().putInt(COLOR_PRIMARY, colorPrimary).apply();
                sInstance.mPreferences.edit().putInt(COLOR_PRIMARY_DARK, Util.darkenColor(colorPrimary)).apply();
                notifyObservers();
            }
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    @UiThread
    public static void setColorAccent(int colorAccent) {
        if (sInstance != null) {
            if (colorAccent != sInstance.getColorAccent()) {
                sInstance.mPreferences.edit().putInt(COLOR_ACCENT, colorAccent).apply();
                notifyObservers();
            }
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    @UiThread
    public static void setMode(Mode mode) {
        if (sInstance != null && mode != null) {
            if (mode != sInstance.getMode()) {
                sInstance.mPreferences.edit().putInt(MODE, mode.getIndex()).apply();
                notifyObservers();
            }
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    @UiThread
    private static void notifyObservers() {
        if (sInstance != null) {
            for (ThemeObserver observer : mThemeObserverList) {
                observer.onThemeChanged(sInstance);
            }
        }
    }

    public static ITheme getTheme() {
        if (sInstance != null) {
            return sInstance;
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    public static void applyTheme(View view, boolean propagate) {
        if (view instanceof ThemeConsumer) {
            ((ThemeConsumer) view).onApplyTheme(sInstance);
        }
        if (propagate && view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                applyTheme(((ViewGroup) view).getChildAt(i), true);
            }
        }
    }

    private final SharedPreferences mPreferences;

    private ThemeEngine(Context context) {
        mPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public int getColorPrimary() {
        return noAlpha(mPreferences.getInt(COLOR_PRIMARY, DEFAULT_COLOR_PRIMARY));
    }

    @Override
    public int getColorPrimaryDark() {
        return noAlpha(mPreferences.getInt(COLOR_PRIMARY_DARK, DEFAULT_COLOR_PRIMARY_DARK));
    }

    @Override
    public int getColorAccent() {
        return noAlpha(mPreferences.getInt(COLOR_ACCENT, DEFAULT_COLOR_ACCENT));
    }

    private int noAlpha(int color) {
        return ColorUtils.setAlphaComponent(color, 255);
    }

    @Override
    public Mode getMode() {
        switch (mPreferences.getInt(MODE, DEFAULT_MODE.getIndex())) {
            case INDEX_MODE_LIGHT:
                return Mode.LIGHT;
            case INDEX_MODE_DARK:
                return Mode.DARK;
            case INDEX_MODE_DEEP_DARK:
                return Mode.DEEP_DARK;
            default:
                // the stored value has been manually altered
                return Mode.LIGHT;
        }
    }

    @Override
    public boolean isDark() {
        return getMode() != Mode.LIGHT;
    }

    @Override
    public int getTextColorPrimary() {
        return DEFAULT_TEXT_COLOR_PRIMARY[getMode().getIndex()];
    }

    @Override
    public int getTextColorSecondary() {
        return DEFAULT_TEXT_COLOR_SECONDARY[getMode().getIndex()];
    }

    @Override
    public int getTextColorPrimaryInverse() {
        return DEFAULT_TEXT_COLOR_PRIMARY_INVERSE[getMode().getIndex()];
    }

    @Override
    public int getTextColorSecondaryInverse() {
        return DEFAULT_TEXT_COLOR_SECONDARY_INVERSE[getMode().getIndex()];
    }

    @Override
    public int getColorCardBackground() {
        return DEFAULT_COLOR_CARD_BACKGROUND[getMode().getIndex()];
    }

    @Override
    public int getColorWindowForeground() {
        return DEFAULT_COLOR_WINDOW_FOREGROUND[getMode().getIndex()];
    }

    @Override
    public int getColorWindowBackground() {
        return DEFAULT_COLOR_WINDOW_BACKGROUND[getMode().getIndex()];
    }

    @Override
    public int getColorRipple() {
        return DEFAULT_COLOR_RIPPLE[getMode().getIndex()];
    }

    @Override
    public int getIconColor() {
        return DEFAULT_ICON_COLOR[getMode().getIndex()];
    }

    @Override
    public int getHintTextColor() {
        return DEFAULT_HINT_TEXT_COLOR[getMode().getIndex()];
    }

    @Override
    public int getErrorColor() {
        return DEFAULT_COLOR_ERROR[getMode().getIndex()];
    }

    @Override
    public int getColorPrimaryContainer() {
        // M3 primary-container: light tone (~90) in light mode, deep tone (~30) in dark modes,
        // derived from the (user selectable) primary color.
        int primary = getColorPrimary();
        return isDark() ? Util.blendColors(primary, Color.BLACK, 0.55f) : Util.blendColors(primary, Color.WHITE, 0.78f);
    }

    @Override
    public int getColorOnPrimaryContainer() {
        int primary = getColorPrimary();
        return isDark() ? Util.blendColors(primary, Color.WHITE, 0.80f) : Util.blendColors(primary, Color.BLACK, 0.70f);
    }

    @Override
    public int getColorSurfaceContainer() {
        return DEFAULT_COLOR_SURFACE_CONTAINER[getMode().getIndex()];
    }

    @Override
    public int getColorOutlineVariant() {
        return DEFAULT_COLOR_OUTLINE_VARIANT[getMode().getIndex()];
    }

    @Override
    public int getDialogBackgroundColor() {
        return DEFAULT_DIALOG_BACKGROUND_COLOR[getMode().getIndex()];
    }

    @Override
    public int getDrawerBackgroundColor() {
        return DRAWER_BACKGROUND_COLOR[getMode().getIndex()];
    }

    @Override
    public int getDrawerIconColor() {
        return DRAWER_ICON_COLOR[getMode().getIndex()];
    }

    @Override
    public int getDrawerSelectedIconColor() {
        return getColorPrimary();
    }

    @Override
    public int getDrawerTextColor() {
        return DRAWER_TEXT_COLOR[getMode().getIndex()];
    }

    @Override
    public int getDrawerSelectedTextColor() {
        return getColorPrimary();
    }

    @Override
    public int getDrawerSelectedItemColor() {
        return DRAWER_SELECTED_ITEM_COLOR[getMode().getIndex()];
    }

    @Override
    public int getBestColor(int background) {
        return Util.isColorLight(background) ? Color.BLACK : Color.WHITE;
    }

    @Override
    public int getBestTextColor(int background) {
        int index = Util.isColorLight(background) ? INDEX_MODE_LIGHT : INDEX_MODE_DARK;
        return DEFAULT_TEXT_COLOR_PRIMARY[index];
    }

    @Override
    public int getBestHintColor(int background) {
        int index = Util.isColorLight(background) ? INDEX_MODE_LIGHT : INDEX_MODE_DARK;
        return DEFAULT_HINT_TEXT_COLOR[index];
    }

    @Override
    public int getBestIconColor(int background) {
        int index = Util.isColorLight(background) ? INDEX_MODE_LIGHT : INDEX_MODE_DARK;
        return DEFAULT_ICON_COLOR[index];
    }

    private int getColorByMode(int colorLight, int colorDark) {
        switch (mPreferences.getInt(MODE, DEFAULT_MODE.getIndex())) {
            case INDEX_MODE_LIGHT:
                return colorLight;
            default:
                return colorDark;
        }
    }

    public enum Mode {

        LIGHT(INDEX_MODE_LIGHT),
        DARK(INDEX_MODE_DARK),
        DEEP_DARK(INDEX_MODE_DEEP_DARK);

        private final int mIndex;

        /*package-local*/ Mode(int index) {
            mIndex = index;
        }

        /*package-local*/ int getIndex() {
            return mIndex;
        }
    }

    public interface ThemeObserver {

        void onThemeChanged(ITheme theme);
    }

    public interface ThemeConsumer {

        void onApplyTheme(ITheme theme);
    }
}