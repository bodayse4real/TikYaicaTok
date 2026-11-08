package cat.narezany.margyt;

/**
 * Easter Eggs and Fun Messages - TikYaicaTok Edition
 *
 * Hidden messages and jokes throughout the mod.
 * Access developer mode with 5 taps on the settings title.
 */
public final class EasterEggs {

    private EasterEggs() {}

    /**
     * Random fun messages that appear in the diary or during special events
     */
    public static final String[] FUNNY_MESSAGES = {
            "Flappy TikTok is watching you play videos",
            "This mod was built with ❤️ and a lot of coffee",
            "Did you know? You can play Flappy TikTok offline!",
            "TikYaicaTok: Because scrolling forever is boring",
            "Made by the best developer... no wait, by Claude 🤖",
            "Your high score in Flappy TikTok: probably 0 😅",
            "This mod is open source. Go star it on GitHub!",
            "Offline game incoming... 3... 2... 1... TAP!",
            "No internet? No problem! Play Flappy TikTok instead",
            "The pink bird is judging your game skills",
            "Remember: TikTok is for content, Flappy is for fun",
            "Settings changed? Don't forget to restart TikTok!",
            "Easter eggs make everything better, right?",
            "Спасибо за использование TikYaicaTok! ✌️",
            "If you beat Flappy TikTok, you're a legend",
    };

    /**
     * Developer Easter eggs - show in dev mode
     */
    public static final String[] DEV_MESSAGES = {
            "Developer mode activated - with great power comes great responsibility",
            "You found the secret settings! Nice!",
            "Here be dragons (and feature flags)",
            "TikTok's bytecode: now with 50% more rewrites!",
            "Flag overrides are dangerous, use with caution",
            "Diary logging level: OVER 9000",
            "The mod sees everything (within reason)",
    };

    /**
     * Get a random funny message
     */
    public static String getRandomMessage() {
        return FUNNY_MESSAGES[(int) (Math.random() * FUNNY_MESSAGES.length)];
    }

    /**
     * Get a random developer message
     */
    public static String getRandomDevMessage() {
        return DEV_MESSAGES[(int) (Math.random() * DEV_MESSAGES.length)];
    }

    /**
     * Easter egg: secret achievement unlocked messages
     */
    public static String getAchievementMessage(int score) {
        if (score >= 1000) {
            return "LEGENDARY PLAYER! You are a Flappy TikTok master! 🏆";
        } else if (score >= 500) {
            return "EXPERT PLAYER! You're getting really good at this! 🎮";
        } else if (score >= 100) {
            return "ADVANCED PLAYER! Keep practicing! 💪";
        } else if (score >= 50) {
            return "INTERMEDIATE PLAYER! Not bad! 👍";
        } else if (score >= 20) {
            return "BEGINNER PLAYER! You're learning! 🚀";
        } else if (score >= 10) {
            return "FIRST STEPS! Keep going! 👶";
        }
        return "Don't give up! You can do better! 💪";
    }

    /**
     * Fun version strings
     */
    public static String getVersionEasterEgg() {
        long time = System.currentTimeMillis();
        int seed = (int) (time / 86400000); // Changes daily

        switch (seed % 5) {
            case 0: return "Version: Now with 50% more features!";
            case 1: return "Version: The Flappy TikTok edition";
            case 2: return "Version: Made by Claude (pretty cool right?)";
            case 3: return "Version: Open source and proud of it";
            default: return "Version: Offshore, online, and ornery";
        }
    }

    /**
     * Get a random tip for users
     */
    public static String getRandomTip() {
        String[] tips = {
                "Did you know? You can customize the mod colors in Appearance settings!",
                "Pro tip: Enable developer options to see detailed logs",
                "You can export your settings as backup JSON",
                "The mod supports plugins - check the documentation!",
                "Offline games are great for planes and trains",
                "TikTok features can be enabled/disabled in TikTok Features section",
                "The diary logs everything the mod does - useful for debugging",
                "Your high score is saved even after closing the app!",
                "Try different avatar shapes in Appearance!",
                "Badges can be rearranged by dragging them around",
        };
        return tips[(int) (Math.random() * tips.length)];
    }
}
