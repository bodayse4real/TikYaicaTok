package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Offline Game Settings - manages the offline game feature
 *
 * Allows users to enable/disable the offline mini-game from mod settings.
 * Game appears automatically when internet connection is lost.
 */
public final class OfflineGameSettings {

    private OfflineGameSettings() {}

    private static final String PREFS_NAME = "margyt_offline_game";
    private static final String KEY_ENABLED = "offline_game_enabled";
    private static final String KEY_SHOW_ON_NO_INTERNET = "show_on_no_internet";

    /**
     * Check if offline game feature is enabled
     */
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ENABLED, true); // Enabled by default
    }

    /**
     * Enable or disable the offline game feature
     */
    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /**
     * Check if game should automatically show when internet is lost
     */
    public static boolean shouldShowOnNoInternet(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SHOW_ON_NO_INTERNET, true); // Enabled by default
    }

    /**
     * Set whether game should show automatically on no internet
     */
    public static void setShouldShowOnNoInternet(Context context, boolean show) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SHOW_ON_NO_INTERNET, show).apply();
    }

    /**
     * Get game name for UI display
     */
    public static String getGameName() {
        return "Flappy TikTok";
    }

    /**
     * Get game description
     */
    public static String getGameDescription() {
        return "Play a fun mini-game when you're offline. Tap to jump, avoid obstacles!";
    }
}
