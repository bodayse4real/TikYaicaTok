package cat.narezany.margyt;

import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The gradient on a supporter's name, and the banner on their profile.
 *
 * Both come from the server with the badges, and both are only ever shown for
 * accounts that still hold the supporter badge: the server drops them from
 * the list the moment the badge goes.
 *
 * A name carries its gradient the same way it carries a badge, as one
 * invisible character. The view that draws a name never sees whose it is, so
 * the character is the answer: it stands for a place in this run's table.
 * Names are stripped of private-use characters before the mod adds its own,
 * so pasting somebody else's into your name does nothing.
 */
public final class Looks {

    /** As many gradients as can be on screen at once, by a wide margin. */
    static final int MOST = 64;

    private Looks() {}

    private static volatile Map<String, int[]> gradients = new HashMap<String, int[]>();
    private static volatile Map<String, String> banners = new HashMap<String, String>();
    private static volatile Map<String, Integer> shades = new HashMap<String, Integer>();

    /** index in this run's table -> uid */
    private static final String[] slots = new String[MOST];
    private static int taken;

    // ------------------------------------------------------------- the data

    static void learn(JSONObject root) {
        try {
            Map<String, int[]> painted = new HashMap<String, int[]>();
            JSONObject said = root.optJSONObject("gradients");
            if (said != null) {
                java.util.Iterator<String> keys = said.keys();
                while (keys.hasNext()) {
                    String uid = keys.next();
                    JSONArray list = said.optJSONArray(uid);
                    if (list == null || list.length() < 2) continue;
                    int[] colours = new int[list.length()];
                    boolean fine = true;
                    for (int i = 0; i < colours.length; i++) {
                        colours[i] = colour(list.optString(i, ""));
                        if (colours[i] == 0) fine = false;
                    }
                    if (fine) painted.put(uid, colours);
                }
            }
            gradients = painted;

            Map<String, String> pictures = new HashMap<String, String>();
            JSONObject told = root.optJSONObject("banners");
            if (told != null) {
                java.util.Iterator<String> keys = told.keys();
                while (keys.hasNext()) {
                    String uid = keys.next();
                    String version = told.optString(uid, "");
                    if (version.length() > 0) pictures.put(uid, version);
                }
            }
            banners = pictures;

            Map<String, Integer> dark = new HashMap<String, Integer>();
            JSONObject dimmed = root.optJSONObject("banner_dim");
            if (dimmed != null) {
                java.util.Iterator<String> keys = dimmed.keys();
                while (keys.hasNext()) {
                    String uid = keys.next();
                    int how = dimmed.optInt(uid, 0);
                    if (how > 0) dark.put(uid, Integer.valueOf(how));
                }
            }
            shades = dark;
        } catch (Throwable error) {
            Diary.note("looks: " + error);
        }
    }

    private static int colour(String hex) {
        try {
            if (hex == null || hex.length() != 6) return 0;
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean isMe(String uid) {
        if (uid == null) return false;
        String myId = Account.id();
        if (myId != null && uid.equals(myId)) return true;
        if (uid.equals(Badges.CREATOR_UID) || "7641005374748869648".equals(uid)) return true;
        return false;
    }

    public static int[] of(String uid) {
        if (isMe(uid)) {
            int[] loc = Gradient.local();
            if (loc != null) return loc;
        }
        return uid == null ? null : gradients.get(uid);
    }

    /** The address of somebody's banner, or nothing. */
    public static String banner(String uid) {
        String version = uid == null ? null : banners.get(uid);
        if (version == null) return null;
        return Badges.SERVER + "/banner/" + uid + "?v=" + version;
    }

    /** Which version of somebody's banner is current, for the cache to key on. */
    public static String bannerVersion(String uid) {
        return uid == null ? null : banners.get(uid);
    }

    /** How dark the owner asked their banner to be drawn, nought to ninety. */
    public static int bannerDim(String uid) {
        Integer how = uid == null ? null : shades.get(uid);
        return how == null ? 0 : how.intValue();
    }

    public static boolean hasBanner(String uid) {
        if (isMe(uid) && Banner.hasLocalBanner(null)) {
            return true;
        }
        return uid != null && banners.containsKey(uid);
    }

    // ------------------------------------------------------ the mark a name carries

    private static char first() {
        return Badges.looksFirst();
    }

    /** The character that stands for this account's gradient, if it has one. */
    public static synchronized String mark(String uid) {
        if (uid == null) return "";
        boolean isMine = isMe(uid) && Gradient.local() != null;
        if (!gradients.containsKey(uid) && !isMine) return "";
        for (int i = 0; i < taken; i++) {
            if (uid.equals(slots[i])) return String.valueOf((char) (first() + i));
        }
        if (taken == MOST) return "";
        slots[taken] = uid;
        return String.valueOf((char) (first() + taken++));
    }

    public static boolean isMark(char c) {
        return c >= first() && c < first() + MOST;
    }

    public static int[] colours(char c) {
        int at = c - first();
        if (at < 0 || at >= taken) return null;
        String uid = slots[at];
        if (isMe(uid)) {
            int[] loc = Gradient.local();
            if (loc != null) return loc;
        }
        int[] found = gradients.get(uid);
        return found != null && found.length >= 2 ? found : null;
    }

    // ------------------------------------------------------------ the drawing

    /** A tag of its own, so nothing here is mistaken for another part's. */
    private static final int PAINTED = 0x4D61726C;

    public static void paint(final TextView view, final int[] colours) {
        paint(view, colours, null);
    }

    public static void paint(final TextView view, final int[] colours, final CharSequence currentText) {
        if (view == null || colours == null || colours.length < 2) return;
        try {
            CharSequence text = currentText != null && currentText.length() > 0 ? currentText : view.getText();
            float width = 0;
            if (text != null && text.length() > 0) {
                width = view.getPaint().measureText(text.toString());
            }
            if (width <= 0) width = view.getWidth();
            if (width <= 0) {
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        paint(view, colours, null);
                    }
                });
                return;
            }
            view.getPaint().setShader(new LinearGradient(
                    0, 0, width, 0, colours, null, Shader.TileMode.CLAMP));
            view.setTag(PAINTED, Boolean.TRUE);
            view.invalidate();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Take a gradient off a view the mod painted earlier.
     *
     * Lists hand the same view to the next person down, so a gradient left on
     * one would follow somebody who never had it. Only views this painted are
     * touched, so TikTok's own shaders are left alone.
     */
    public static void clear(TextView view) {
        if (view == null) return;
        try {
            if (view.getTag(PAINTED) == null) return;
            view.getPaint().setShader(null);
            view.setTag(PAINTED, null);
        } catch (Throwable ignored) {
        }
    }
}
