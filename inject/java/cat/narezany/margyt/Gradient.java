package cat.narezany.margyt;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Your own gradient: what it is, and telling the server about it.
 *
 * Who may have one is the server's decision, not this file's. The mod is an
 * apk anybody can edit, so the badge is checked where editing the apk does
 * not help, and so is the wait between changes.
 */
public final class Gradient {

    /** How many colours a gradient may have, matching the server. */
    public static final int FEWEST = 2;
    public static final int MOST = 5;

    /** What is offered before anybody has chosen anything. */
    private static final int[] SUGGESTED = {0xFFFF6FA5, 0xFF7B61FF};

    private Gradient() {}

    public interface Said {
        void said(boolean ok, String trouble);
    }

    private static final String KEY_LOCAL_GRADIENT = "gradient_local_colours";

    public static int[] local() {
        android.content.Context context = Margy.context();
        if (context == null) return null;
        String raw = context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE)
                .getString(KEY_LOCAL_GRADIENT, null);
        if (raw == null || raw.length() == 0) return null;
        try {
            JSONArray arr = new JSONArray(raw);
            if (arr.length() < 2) return null;
            int[] out = new int[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                out[i] = (int) Long.parseLong(arr.getString(i), 16);
            }
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void saveLocal(List<Integer> colours) {
        android.content.Context context = Margy.context();
        if (context == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (Integer c : colours) {
                arr.put(Integer.toHexString(c));
            }
            context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putString(KEY_LOCAL_GRADIENT, arr.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    public static void dropLocal() {
        android.content.Context context = Margy.context();
        if (context == null) return;
        context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE)
                .edit().remove(KEY_LOCAL_GRADIENT).apply();
    }

    /** The colours the server currently has for this account. */
    public static int[] mine() {
        int[] loc = local();
        if (loc != null) return loc.clone();
        String uid = Account.id();
        int[] have = uid == null ? null : Looks.of(uid);
        return have == null ? null : have.clone();
    }

    /** What the picker starts from. */
    public static List<Integer> starting() {
        List<Integer> out = new ArrayList<Integer>();
        int[] have = mine();
        if (have != null) {
            for (int colour : have) out.add(colour);
        } else {
            for (int colour : SUGGESTED) out.add(colour);
        }
        return out;
    }

    public static void save(final List<Integer> colours, final Said then) {
        saveLocal(colours);
        final String uid = Account.id();
        final String token = Mine.token();
        if (uid != null && token.length() > 0) {
            Net.away("gradient: save", new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONArray list = new JSONArray();
                        for (Integer colour : colours) list.put(hex(colour));
                        Net.talk(Badges.SERVER + "/gradient",
                                new JSONObject().put("uid", uid).put("token", token)
                                        .put("colours", list).toString());
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
        answer(then, true, "");
    }

    /** Stop having one. */
    public static void drop(final Said then) {
        dropLocal();
        final String uid = Account.id();
        final String token = Mine.token();
        if (uid != null && token.length() > 0) {
            Net.away("gradient: drop", new Runnable() {
                @Override
                public void run() {
                    try {
                        Net.talk(Badges.SERVER + "/gradient",
                                new JSONObject().put("uid", uid).put("token", token)
                                        .toString());
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
        answer(then, true, "");
    }

    static String hex(int colour) {
        return String.format("%06X", colour & 0xFFFFFF);
    }

    /** One colour from hue, saturation and value, which is what the picker holds. */
    public static int from(int hue, int saturation, int value) {
        return Color.HSVToColor(new float[]{hue % 360, saturation / 100f, value / 100f});
    }

    public static int[] hsv(int colour) {
        float[] out = new float[3];
        Color.colorToHSV(colour, out);
        return new int[]{(int) out[0], Math.round(out[1] * 100), Math.round(out[2] * 100)};
    }

    private static void answer(final Said then, final boolean ok, final String trouble) {
        if (then == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                then.said(ok, trouble);
            }
        });
    }
}
