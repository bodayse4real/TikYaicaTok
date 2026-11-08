package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.FrameLayout;

/**
 * Offline Game Activity - shows when TikTok has no internet connection.
 *
 * Displays "Flappy TikTok" mini-game so users have something to do
 * while waiting for connection or as a fun Easter egg.
 */
public final class OfflineGameActivity extends Activity {

    private OfflineGame gameView;
    private ConnectivityManager connectivityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        // Create frame layout and add game
        FrameLayout container = new FrameLayout(this);
        gameView = new OfflineGame(this);
        container.addView(gameView);

        setContentView(container);

        // Customize appearance
        getWindow().setBackgroundDrawable(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) {
            gameView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) {
            gameView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameView != null) {
            gameView.stop();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Allow back button to exit game
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Check if device has internet connection
     */
    public static boolean hasInternetConnection(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Throwable ignored) {
            return true; // Assume connected if we can't check
        }
    }
}
