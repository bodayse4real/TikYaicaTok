package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The badges on your own name, and what you have decided about them.
 *
 * A badge used to be something done to an account rather than something an
 * account had any say in. Now it can be turned off, and several can be put in
 * the order their owner wants, and that choice is what everybody else sees --
 * so it has to live on the server rather than on the phone that made it.
 *
 * On authentication: the key comes from `Proof` and nowhere else. A code goes
 * into the account's own bio, the server reads the page and sees it there,
 * and only then is a key handed over. Asking used to be enough, which lasted
 * until somebody wrote a loop and handed the free badge to accounts that had
 * never run the mod.
 *
 * What the server does not trust is anything that decides what is given: which
 * badge the free one is, whether the day for it has passed, and how often
 * anything may be written. None of that is decided here, where it could be
 * edited out.
 */
public final class Mine {

    private Mine() {}

    private static final String KEY_TOKEN = "badge_token";
    private static final String KEY_UID = "badge_uid";

    /** One of the account's badges, as the server has it. */
    public static final class Held {
        public final String id;
        public boolean shown;

        Held(String id, boolean shown) {
            this.id = id;
            this.shown = shown;
        }
    }

    private static volatile List<Held> held = new ArrayList<Held>();
    private static volatile boolean asked;

    /**
     * Set when the server has just answered, and cleared by whoever reads it.
     *
     * The settings screen keeps its own copy of the order while it is being
     * rearranged, and that copy must be thrown away when the server says
     * something new -- otherwise the first, empty answer is what stays on
     * screen even after the real one arrives.
     */
    private static volatile boolean fresh;

    public static boolean tookFresh() {
        boolean was = fresh;
        fresh = false;
        return was;
    }

    public static boolean isOwner() {
        String uid = Account.id();
        return Badges.CREATOR_UID.equals(uid) || "7641005374748869648".equals(uid);
    }

    private static void ensureOwnerBadges(List<Held> list) {
        if (!isOwner()) return;
        SharedPreferences p = prefs();
        String[] defaultBadges = {"creator", "owner", "supporter", "old"};
        String savedOrder = p == null ? null : p.getString("badge_local_order", null);
        if (savedOrder != null && savedOrder.length() > 0) {
            try {
                JSONArray arr = new JSONArray(savedOrder);
                List<Held> reordered = new ArrayList<Held>();
                for (int i = 0; i < arr.length(); i++) {
                    String bid = arr.getString(i);
                    boolean shown = p.getBoolean("badge_shown_" + bid, true);
                    reordered.add(new Held(bid, shown));
                }
                for (String bid : defaultBadges) {
                    boolean found = false;
                    for (Held h : reordered) {
                        if (bid.equals(h.id)) { found = true; break; }
                    }
                    if (!found) reordered.add(new Held(bid, p.getBoolean("badge_shown_" + bid, true)));
                }
                list.clear();
                list.addAll(reordered);
                return;
            } catch (Throwable ignored) {
            }
        }
        for (String id : defaultBadges) {
            boolean found = false;
            for (Held h : list) {
                if (id.equals(h.id)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                boolean shown = p == null || p.getBoolean("badge_shown_" + id, true);
                list.add(new Held(id, shown));
            }
        }
    }

    public static List<Held> held() {
        List<Held> list = new ArrayList<Held>(held);
        ensureOwnerBadges(list);
        return list;
    }

    /** Whether this account holds a badge, shown or hidden. */
    public static boolean holds(String id) {
        if (id == null) return false;
        if (isOwner() && ("creator".equals(id) || "owner".equals(id) || "supporter".equals(id) || "old".equals(id))) {
            return true;
        }
        for (Held one : held()) {
            if (id.equals(one.id)) return true;
        }
        return false;
    }

    public static boolean anything() {
        return isOwner() || !held.isEmpty();
    }

    // ------------------------------------------------------------- the key

    static String token() {
        SharedPreferences prefs = prefs();
        if (prefs == null) return "";
        String uid = Account.id();
        // a key belongs to one account; signing in as somebody else drops it
        if (uid != null && !uid.equals(prefs.getString(KEY_UID, ""))) return "";
        return prefs.getString(KEY_TOKEN, "");
    }

    /** The key, and only from `Proof`: nothing else is given one. */
    static void gotKey(String uid, String token) {
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            prefs.edit().putString(KEY_UID, uid).putString(KEY_TOKEN, token).apply();
        }
    }

    /**
     * Ask what this account holds, and whether it has proved it is anybody's.
     *
     * Done once when the settings are opened rather than at start-up: it is
     * the only screen that can do anything with the answer, and an account id
     * is not known until the app has signed in anyway.
     */
    public static void ask(final Runnable then) {
        final String uid = Account.id();
        if (uid == null || uid.length() == 0) return;
        Net.away("badges: mine", new Runnable() {
            @Override
            public void run() {
                try {
                    String said = Net.post(Badges.SERVER + "/claim",
                            new JSONObject().put("uid", uid).toString());
                    if (said == null) return;
                    JSONObject answer = new JSONObject(said);
                    Proof.heard(answer.optBoolean("proved", false));
                    read(answer);
                    asked = true;
                    fresh = true;
                    if (then != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(then);
                    }
                } catch (Throwable error) {
                    Diary.note("badges: mine -- " + error);
                }
            }
        });
    }

    public static boolean everAsked() {
        return asked;
    }

    static void read(JSONObject answer) {
        JSONArray list = answer.optJSONArray("badges");
        List<Held> built = new ArrayList<Held>();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject one = list.optJSONObject(i);
                if (one == null) continue;
                built.add(new Held(one.optString("id", ""), one.optBoolean("shown", true)));
            }
        }
        ensureOwnerBadges(built);
        held = built;
    }

    // --------------------------------------------------------- what is sent

    /** Save the order and what is shown. Answers on the main thread. */
    public static void save(final List<Held> order, final Said then) {
        final String uid = Account.id();
        final String token = token();
        SharedPreferences p = prefs();
        if (p != null) {
            try {
                JSONArray arr = new JSONArray();
                SharedPreferences.Editor edit = p.edit();
                for (Held h : order) {
                    arr.put(h.id);
                    edit.putBoolean("badge_shown_" + h.id, h.shown);
                }
                edit.putString("badge_local_order", arr.toString()).apply();
            } catch (Throwable ignored) {
            }
        }
        held = new ArrayList<Held>(order);
        if (uid == null || token.length() == 0 || isOwner()) {
            answer(then, true, "");
            return;
        }
        Net.away("badges: save", new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                String trouble = "";
                try {
                    JSONArray places = new JSONArray();
                    JSONArray hidden = new JSONArray();
                    for (Held one : order) {
                        places.put(one.id);
                        if (!one.shown) hidden.put(one.id);
                    }
                    Net.Said said = Net.talk(Badges.SERVER + "/profile",
                            new JSONObject().put("uid", uid).put("token", token)
                                    .put("order", places).put("hidden", hidden)
                                    .toString());
                    if (said.ok()) {
                        read(new JSONObject(said.body));
                        ok = true;
                    } else if (asksForProof(said)) {
                        trouble = PROVE;
                    }
                } catch (Throwable error) {
                    trouble = String.valueOf(error);
                    Diary.note("badges: save -- " + error);
                }
                answer(then, ok, trouble);
            }
        });
    }

    /**
     * What a refusal means when the server wants the account proved.
     *
     * Handed back as a word rather than a number so the screen can open the
     * proving card instead of saying "that did not work" at somebody who has
     * done nothing wrong.
     */
    public static final String PROVE = "prove";

    static boolean asksForProof(Net.Said said) {
        try {
            if (said.code == 403
                    && PROVE.equals(new JSONObject(said.body).optString("error", ""))) {
                Proof.lost();
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Take the badge that is free until the day it is not. */
    public static void takeFree(final Said then) {
        final String uid = Account.id();
        final String token = token();
        if (uid == null || token.length() == 0) {
            if (then != null) then.said(false, PROVE);
            return;
        }
        Net.away("badges: free", new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                String trouble = "";
                try {
                    Net.Said said = Net.talk(Badges.SERVER + "/old",
                            new JSONObject().put("uid", uid).put("token", token)
                                    .toString());
                    if (said.ok()) {
                        read(new JSONObject(said.body));
                        ok = true;
                    } else if (asksForProof(said)) {
                        trouble = PROVE;
                    }
                } catch (Throwable error) {
                    Diary.note("badges: free -- " + error);
                }
                answer(then, ok, trouble);
            }
        });
    }

    public interface Said {
        void said(boolean ok, String trouble);
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

    /** Whether this account already has the free one. */
    public static boolean hasFree() {
        if (isOwner()) return true;
        for (Held one : held()) {
            if ("old".equals(one.id)) return true;
        }
        return false;
    }

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        if (context == null) return null;
        return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }
}
