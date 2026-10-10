package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.InputStream;

/**
 * The picture across the top of a supporter's profile.
 *
 * Whose profile is open is known from the name the profile just wrote, and
 * the picture is put in behind the header. TikTok has no such thing, so it is
 * a view of the mod's own, tagged so it goes in once and comes out when the
 * next profile is somebody without one.
 */
public final class Banner {

    /** Five megabytes, the same as the server accepts. */
    public static final int MOST = 5 * 1024 * 1024;

    /** How tall the picture is drawn, in density pixels. */
    private static final int TALL = 132;

    private static final int TAG = 0x4D617242;   // "MarB"

    /** Where the shadow strength is kept, and how strong it is by default. */
    static final String KEY_SHADE = "banner_shade";
    private static final int SHADE = 55;

    /** Marks a text this put a shadow on, so only those are put back. */
    private static final int SHADED = 0x4D617243;   // "MarC"

    /** Deep enough for a header, shallow enough to stay cheap. */
    private static final int DEEP = 6;

    private Banner() {}

    public interface Said {
        void said(boolean ok, String trouble);
    }

    private static volatile Bitmap localBitmap;

    public static boolean hasLocalBanner(Context context) {
        if (context == null) context = Margy.context();
        if (context == null) return false;
        File f = new File(context.getFilesDir(), "margyt/banners/local_banner.jpg");
        return f.exists() && f.length() > 0;
    }

    public static Bitmap getLocalBanner(Context context) {
        if (localBitmap != null) return localBitmap;
        if (context == null) context = Margy.context();
        if (context == null) return null;
        File f = new File(context.getFilesDir(), "margyt/banners/local_banner.jpg");
        if (!f.exists()) return null;
        byte[] blob = Net.read(f);
        if (blob == null) return null;
        localBitmap = decode(blob);
        return localBitmap;
    }

    public static boolean saveLocal(Context context, Uri uri) {
        try {
            byte[] blob = read(context, uri);
            if (blob == null || blob.length == 0) return false;
            File dir = new File(context.getFilesDir(), "margyt/banners");
            dir.mkdirs();
            File dest = new File(dir, "local_banner.jpg");
            Net.save(dest, blob);
            localBitmap = decode(blob);
            return true;
        } catch (Throwable error) {
            Diary.note("banner save local: " + error);
            return false;
        }
    }

    public static void dropLocal(Context context) {
        try {
            if (context == null) context = Margy.context();
            if (context != null) {
                File f = new File(context.getFilesDir(), "margyt/banners/local_banner.jpg");
                if (f.exists()) f.delete();
            }
            localBitmap = null;
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------- sending

    /** Read a picture the person picked, refusing anything too big. */
    public static void send(final Context context, final Uri uri, final Said then) {
        final String uid = Account.id();
        final String token = Mine.token();
        if (uid == null || token.length() == 0) {
            if (then != null) then.said(false, Mine.PROVE);
            return;
        }
        Net.away("banner: send", new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                String trouble = Text.BANNER_REFUSED;
                try {
                    String kind = context.getContentResolver().getType(uri);
                    if (kind == null) kind = "image/jpeg";
                    byte[] blob = read(context, uri);
                    if (blob == null) {
                        trouble = Text.BANNER_UNREADABLE;
                    } else if (blob.length > MOST) {
                        trouble = Text.BANNER_TOO_BIG;
                    } else {
                        Net.Said said = Net.deliver(Badges.SERVER + "/banner?uid="
                                + uid + "&token=" + token, kind, blob);
                        if (said.ok()) {
                            ok = true;
                            Badges.refresh();
                        } else if (Mine.asksForProof(said)) {
                            trouble = Mine.PROVE;
                        }
                    }
                } catch (Throwable error) {
                    Diary.note("banner: " + error);
                }
                answer(then, ok, ok ? "" : trouble);
            }
        });
    }

    public static void drop(final Said then) {
        final String uid = Account.id();
        final String token = Mine.token();
        if (uid == null || token.length() == 0) {
            if (then != null) then.said(false, Mine.PROVE);
            return;
        }
        Net.away("banner: drop", new Runnable() {
            @Override
            public void run() {
                Net.Said said = Net.deliver(Badges.SERVER + "/banner?uid=" + uid
                        + "&token=" + token, "image/jpeg", new byte[0]);
                boolean ok = said.ok();
                if (ok) Badges.refresh();
                answer(then, ok, ok ? "" : Mine.asksForProof(said)
                        ? Mine.PROVE : Text.BANNER_REFUSED);
            }
        });
    }

    private static byte[] read(Context context, Uri uri) {
        try {
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[65536];
            int got;
            while ((got = in.read(buffer)) > 0) {
                out.write(buffer, 0, got);
                if (out.size() > MOST) {
                    in.close();
                    return out.toByteArray();
                }
            }
            in.close();
            return out.toByteArray();
        } catch (Throwable error) {
            Diary.note("banner: " + error);
            return null;
        }
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

    // ------------------------------------------------------------- showing

    /** The header's own background, to put back when a profile has none. */
    private static final java.util.WeakHashMap<View, Object> before =
            new java.util.WeakHashMap<View, Object>();

    /**
     * Put somebody's banner on the profile they just opened.
     *
     * The picture becomes the header's background rather than a view of its
     * own. A view sits in the layout as a rectangle of its own and pushes
     * everything down; a background takes the shape the header already has
     * and the avatar and the name stay on top of it, which is what a banner
     * is meant to look like.
     */
    public static void show(final View nameView, final String uid) {
        if (nameView == null || uid == null) return;
        try {
            View header = headerOf(nameView);
            if (header == null) return;
            Context ctx = nameView.getContext();
            if (hasLocalBanner(ctx) && uid.equals(Account.id())) {
                Bitmap local = getLocalBanner(ctx);
                if (local != null) {
                    synchronized (before) {
                        if (!before.containsKey(header)) before.put(header, header.getBackground());
                    }
                    wear(header, local, uid);
                    return;
                }
            }
            String address = Looks.banner(uid);
            if (address == null) {
                restore(header);
                return;
            }
            synchronized (before) {
                if (!before.containsKey(header)) before.put(header, header.getBackground());
            }
            picture(ctx, uid, address, header);
        } catch (Throwable error) {
            Diary.note("banner: " + error);
        }
    }

    private static final String KEY_LOCAL_DIM = "banner_local_dim";

    /**
     * How dark your own banner is drawn, for everyone who sees it.
     */
    public static int dim() {
        try {
            Context context = Margy.context();
            if (context != null) {
                int local = context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                        .getInt(KEY_LOCAL_DIM, -1);
                if (local >= 0) return local;
            }
        } catch (Throwable ignored) {
        }
        return Looks.bannerDim(Account.id());
    }

    public static void setDim(final int how, final Said then) {
        try {
            Context context = Margy.context();
            if (context != null) {
                context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                        .edit().putInt(KEY_LOCAL_DIM, Math.max(0, Math.min(90, how))).apply();
            }
        } catch (Throwable ignored) {
        }
        final String uid = Account.id();
        final String token = Mine.token();
        if (uid != null && token.length() > 0) {
            Net.away("banner: dim", new Runnable() {
                @Override
                public void run() {
                    try {
                        Net.talk(Badges.SERVER + "/shade",
                                new org.json.JSONObject().put("uid", uid).put("token", token)
                                        .put("dim", Math.max(0, Math.min(90, how))).toString());
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
        answer(then, true, "");
    }

    /** How heavy the shadow under text on a banner is, nought to a hundred. */
    public static int shade() {
        try {
            Context context = Margy.context();
            if (context == null) return SHADE;
            return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .getInt(KEY_SHADE, SHADE);
        } catch (Throwable ignored) {
            return SHADE;
        }
    }

    public static void setShade(int how) {
        try {
            Context context = Margy.context();
            if (context == null) return;
            context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_SHADE, Math.max(0, Math.min(100, how))).apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * A shadow under the text standing on the picture.
     *
     * A name in white on a white photograph is not readable, and the picture
     * is whatever somebody uploaded. A shadow under the letters costs nothing
     * and works against any picture, which a scrim over the whole header does
     * not.
     */
    private static void shade(View view, int depth) {
        if (view == null || depth > DEEP) return;
        int how = shade();
        if (view instanceof android.widget.TextView) {
            android.widget.TextView text = (android.widget.TextView) view;
            try {
                if (how <= 0) {
                    if (text.getTag(SHADED) != null) {
                        text.setShadowLayer(0, 0, 0, 0);
                        text.setTag(SHADED, null);
                    }
                } else {
                    float radius = 1f + how * 0.05f;
                    int ink = (int) (how * 2.4f) << 24;
                    text.setShadowLayer(radius, 0, radius * 0.35f, ink);
                    text.setTag(SHADED, Boolean.TRUE);
                }
            } catch (Throwable ignored) {
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int many = group.getChildCount();
            for (int i = 0; i < many; i++) shade(group.getChildAt(i), depth + 1);
        }
    }

    private static void unshade(View view, int depth) {
        if (view == null || depth > DEEP) return;
        if (view instanceof android.widget.TextView) {
            android.widget.TextView text = (android.widget.TextView) view;
            if (text.getTag(SHADED) != null) {
                try {
                    text.setShadowLayer(0, 0, 0, 0);
                    text.setTag(SHADED, null);
                } catch (Throwable ignored) {
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int many = group.getChildCount();
            for (int i = 0; i < many; i++) unshade(group.getChildAt(i), depth + 1);
        }
    }

    private static void restore(View header) {
        Object had;
        synchronized (before) {
            if (!before.containsKey(header)) return;
            had = before.remove(header);
        }
        try {
            header.setBackground((android.graphics.drawable.Drawable) had);
            header.setTag(TAG, null);
            unshade(header, 0);
        } catch (Throwable ignored) {
        }
    }

    /**
     * The view the picture goes behind, or nothing.
     *
     * A name turns up in more places than a profile: the search box shows one
     * while you type it, and putting a banner behind that was exactly as odd
     * as it sounds. So the answer has to look like a header and not merely be
     * wide: tall enough for an avatar, near the top of the screen, and made of
     * several pieces. A search box is none of those.
     */
    private static View headerOf(View from) {
        android.util.DisplayMetrics screen = from.getResources().getDisplayMetrics();
        int wide = screen.widthPixels;
        int tall = screen.heightPixels;
        int least = (int) (150 * screen.density);

        View at = from;
        View best = null;
        int[] where = new int[2];
        for (int up = 0; up < 7 && at != null; up++) {
            View parent = at.getParent() instanceof View ? (View) at.getParent() : null;
            if (parent == null) break;
            if (parent instanceof ViewGroup && parent.getWidth() >= wide * 0.9f
                    && parent.getHeight() >= least && parent.getHeight() < tall * 0.75f
                    && ((ViewGroup) parent).getChildCount() >= 3) {
                parent.getLocationOnScreen(where);
                if (where[1] < tall * 0.5f) best = parent;
            }
            at = parent;
        }
        return best;
    }

    /** The header this last painted, so it can be kept painted. */
    private static java.lang.ref.WeakReference<View> standing;
    private static String standingFor;

    /**
     * Put it back if it came off.
     *
     * A profile rebuilds its header as it loads and the mod is only told
     * whose profile it is for a few seconds around the name arriving. Without
     * this the banner turned up when that timing worked out and not otherwise.
     */
    public static void again() {
        View header = standing == null ? null : standing.get();
        String uid = standingFor;
        if (header == null || uid == null) return;
        if (!header.isAttachedToWindow()) {
            standing = null;
            standingFor = null;
            return;
        }
        Context ctx = header.getContext();
        if (hasLocalBanner(ctx) && uid.equals(Account.id())) {
            Bitmap local = getLocalBanner(ctx);
            if (local != null) {
                wear(header, local, uid);
                return;
            }
        }
        if (!Looks.hasBanner(uid)) {
            restore(header);
            standing = null;
            standingFor = null;
            return;
        }
        String address = Looks.banner(uid);
        if (address != null) picture(ctx, uid, address, header);
    }

    /** Centre-cropped into whatever shape the header turns out to be. */
    private static final class Painted extends android.graphics.drawable.Drawable {
        private final Bitmap picture;
        private final int dim;
        private final android.graphics.Paint brush = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
                        | android.graphics.Paint.FILTER_BITMAP_FLAG);

        Painted(Bitmap picture, int dim) {
            this.picture = picture;
            this.dim = dim;
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            if (picture == null || bounds.width() <= 0 || bounds.height() <= 0) return;
            float scale = Math.max((float) bounds.width() / picture.getWidth(),
                    (float) bounds.height() / picture.getHeight());
            float across = picture.getWidth() * scale;
            float down = picture.getHeight() * scale;
            float left = bounds.left + (bounds.width() - across) / 2f;
            float top = bounds.top + (bounds.height() - down) / 2f;
            canvas.drawBitmap(picture, null,
                    new android.graphics.RectF(left, top, left + across, top + down), brush);
            if (dim > 0) {
                // black over a dark theme, white over a light one. The name and
                // the counts on top are white in one and near black in the
                // other, and a veil that only ever darkens puts black on black
                int veil = Themes.isDark() ? 0x000000 : 0xFFFFFF;
                canvas.drawColor(((int) (dim * 2.55f) << 24) | veil);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            brush.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter filter) {
            brush.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    private static final java.util.Map<String, Bitmap> kept =
            new java.util.HashMap<String, Bitmap>();

    /**
     * The picture itself, from memory, from the cache, or from the server.
     *
     * Its own fetch rather than the badges' one: that builds an address under
     * /icon/, and handing it a whole address gave /icon/http://... and nothing
     * on screen. Nothing waits here, and the view is filled in whenever the
     * picture arrives.
     */
    private static void picture(final Context context, final String uid,
                                final String address, final View into) {
        final String key = safe(uid + "-" + Looks.bannerVersion(uid));
        Bitmap known;
        synchronized (kept) {
            if (kept.containsKey(key)) {
                known = kept.get(key);
                if (known != null) wear(into, known, uid);
                return;
            }
            kept.put(key, null);
        }

        final java.lang.ref.WeakReference<View> waiting =
                new java.lang.ref.WeakReference<View>(into);
        final File cache = new File(context.getFilesDir(), "margyt/banners/" + key);
        Net.away("banner: fetch", new Runnable() {
            @Override
            public void run() {
                byte[] blob = Net.read(cache);
                if (blob == null) {
                    blob = Net.bytes(address);
                    if (blob != null) Net.save(cache, blob);
                }
                final Bitmap picture = decode(blob);
                if (picture == null) {
                    // forget the asking, or one bad moment on the network
                    // means no banner until the app is started again
                    synchronized (kept) {
                        kept.remove(key);
                    }
                    return;
                }
                synchronized (kept) {
                    kept.put(key, picture);
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        View view = waiting.get();
                        if (view != null) wear(view, picture, uid);
                    }
                });
            }
        });
    }

    /**
     * A file name that cannot be a path.
     *
     * The uid and the version come off the server over plain http, so both are
     * somebody else's words until proven otherwise. A name that kept its
     * slashes and dots would let an answer of "../../databases/x" write
     * wherever it liked.
     */
    private static String safe(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length() && i < 80; i++) {
            char c = name.charAt(i);
            out.append(Character.isLetterOrDigit(c) || c == '-' ? c : '_');
        }
        String cleaned = out.toString();
        return cleaned.isEmpty() ? "banner" : cleaned;
    }

    /** Worn once per person, so a recycled header does not keep somebody else's. */
    private static void wear(View header, Bitmap picture, String uid) {
        try {
            int dimVal = (uid != null && uid.equals(Account.id())) ? dim() : Looks.bannerDim(uid);
            header.setBackground(new Painted(picture, dimVal));
            header.setTag(TAG, uid);
            standing = new java.lang.ref.WeakReference<View>(header);
            standingFor = uid;
            // again on every pass: the header is rebuilt as a profile loads,
            // and text that arrived after the picture has no shadow yet
            shade(header, 0);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Decoded no larger than a phone screen. A banner may be five megabytes,
     * and the full size of one is memory nobody needs.
     */
    static Bitmap decode(byte[] blob) {
        if (blob == null) return null;
        try {
            BitmapFactory.Options measure = new BitmapFactory.Options();
            measure.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(blob, 0, blob.length, measure);
            int step = 1;
            while (measure.outWidth / step > 1600) step *= 2;
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = step;
            return BitmapFactory.decodeByteArray(blob, 0, blob.length, real);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
