package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Offline mini-game: "Flappy TikTok"
 *
 * When there's no internet connection, users can play a simple arcade game
 * inspired by Flappy Bird. The game features:
 * - TikTok themed graphics (black and white gradient)
 * - Simple tap-to-jump mechanic
 * - Obstacle avoidance gameplay
 * - Score tracking with high score persistence
 */
public final class OfflineGame extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final String PREFS_GAME = "margyt_game";
    private static final String KEY_HIGH_SCORE = "high_score";
    private static final float GRAVITY = 0.6f;
    private static final float JUMP_FORCE = -15f;
    private static final float PIPE_SPEED = -8f;
    private static final int PIPE_GAP = 200;
    private static final int PIPE_WIDTH = 100;
    private static final int SPAWN_DISTANCE = 400;

    private Thread gameThread;
    private SurfaceHolder holder;
    private boolean running = false;
    private boolean gameOver = false;
    private boolean paused = false;

    // Player
    private float playerX = 100;
    private float playerY = 300;
    private float playerVelocityY = 0;
    private static final int PLAYER_SIZE = 50;
    private Paint playerPaint;
    private Paint playerEyePaint;

    // Pipes
    private java.util.List<Pipe> pipes;
    private int score = 0;
    private int highScore = 0;

    // Graphics
    private Paint bgPaint;
    private Paint pipePaint;
    private Paint textPaint;
    private Paint scorePaint;
    private Paint gameOverPaint;

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    public static class Pipe {
        float x;
        int topHeight;
        boolean scored = false;

        Pipe(float x, int topHeight) {
            this.x = x;
            this.topHeight = topHeight;
        }
    }

    public OfflineGame(Context context) {
        super(context);
        this.holder = getHolder();
        this.holder.addCallback(this);
        this.setFocusable(true);

        initializePaints();
        loadHighScore();
        resetGame();
    }

    private void initializePaints() {
        // Background (TikTok dark theme)
        bgPaint = new Paint();
        bgPaint.setColor(Color.BLACK);
        bgPaint.setStyle(Paint.Style.FILL);

        // Player (TikTok pink)
        playerPaint = new Paint();
        playerPaint.setColor(0xFFFE2C55); // TikTok pink
        playerPaint.setStyle(Paint.Style.FILL);

        // Player eyes
        playerEyePaint = new Paint();
        playerEyePaint.setColor(Color.WHITE);
        playerEyePaint.setStyle(Paint.Style.FILL);

        // Pipes (white/gray)
        pipePaint = new Paint();
        pipePaint.setColor(Color.WHITE);
        pipePaint.setStyle(Paint.Style.FILL);

        // Text
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        // Score display
        scorePaint = new Paint();
        scorePaint.setColor(0xFFFE2C55);
        scorePaint.setTextSize(60);
        scorePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        // Game Over overlay
        gameOverPaint = new Paint();
        gameOverPaint.setColor(Color.BLACK);
        gameOverPaint.setAlpha(200);
        gameOverPaint.setStyle(Paint.Style.FILL);
    }

    private void resetGame() {
        playerY = 300;
        playerVelocityY = 0;
        score = 0;
        gameOver = false;
        pipes = new java.util.ArrayList<>();
        spawnPipe();
    }

    private void spawnPipe() {
        int screenHeight = getHeight();
        int minHeight = 100;
        int maxHeight = screenHeight - PIPE_GAP - 100;
        int topHeight = minHeight + (int) (Math.random() * (maxHeight - minHeight));

        pipes.add(new Pipe(getWidth(), topHeight));
    }

    private void loadHighScore() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_GAME, Context.MODE_PRIVATE);
        highScore = prefs.getInt(KEY_HIGH_SCORE, 0);
    }

    private void saveHighScore() {
        if (score > highScore) {
            highScore = score;
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_GAME, Context.MODE_PRIVATE);
            prefs.edit().putInt(KEY_HIGH_SCORE, highScore).apply();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        long lastTime = System.currentTimeMillis();

        while (running) {
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastTime) / 1000.0f;
            lastTime = currentTime;

            if (!paused && !gameOver) {
                update(deltaTime);
            }

            draw();

            try {
                Thread.sleep(16); // ~60 FPS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void update(float deltaTime) {
        int screenHeight = getHeight();
        int screenWidth = getWidth();

        // Apply gravity
        playerVelocityY += GRAVITY;
        playerY += playerVelocityY;

        // Check bounds
        if (playerY + PLAYER_SIZE >= screenHeight || playerY <= 0) {
            gameOver = true;
            saveHighScore();
            return;
        }

        // Update pipes
        for (int i = pipes.size() - 1; i >= 0; i--) {
            Pipe pipe = pipes.get(i);
            pipe.x += PIPE_SPEED;

            // Remove off-screen pipes
            if (pipe.x + PIPE_WIDTH < 0) {
                pipes.remove(i);
            } else {
                // Check collision
                if (checkCollision(pipe)) {
                    gameOver = true;
                    saveHighScore();
                    return;
                }

                // Score point
                if (!pipe.scored && pipe.x + PIPE_WIDTH < playerX) {
                    pipe.scored = true;
                    score++;
                }
            }
        }

        // Spawn new pipe
        if (pipes.isEmpty() || pipes.get(pipes.size() - 1).x < screenWidth - SPAWN_DISTANCE) {
            spawnPipe();
        }
    }

    private boolean checkCollision(Pipe pipe) {
        int screenHeight = getHeight();
        int bottomPipeY = pipe.topHeight + PIPE_GAP;

        // Check if player intersects with pipe
        Rect playerRect = new Rect((int) playerX, (int) playerY, (int) (playerX + PLAYER_SIZE), (int) (playerY + PLAYER_SIZE));
        Rect topPipeRect = new Rect((int) pipe.x, 0, (int) (pipe.x + PIPE_WIDTH), pipe.topHeight);
        Rect bottomPipeRect = new Rect((int) pipe.x, bottomPipeY, (int) (pipe.x + PIPE_WIDTH), screenHeight);

        return playerRect.intersect(topPipeRect) || playerRect.intersect(bottomPipeRect);
    }

    private void draw() {
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas == null) return;

            // Draw background
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), bgPaint);

            // Draw pipes
            for (Pipe pipe : pipes) {
                int screenHeight = getHeight();
                // Top pipe
                canvas.drawRect(pipe.x, 0, pipe.x + PIPE_WIDTH, pipe.topHeight, pipePaint);
                // Bottom pipe
                int bottomPipeY = pipe.topHeight + PIPE_GAP;
                canvas.drawRect(pipe.x, bottomPipeY, pipe.x + PIPE_WIDTH, screenHeight, pipePaint);
            }

            // Draw player (circle with eyes)
            canvas.drawCircle(playerX + PLAYER_SIZE / 2, playerY + PLAYER_SIZE / 2, PLAYER_SIZE / 2, playerPaint);

            // Draw eyes
            int eyeRadius = 5;
            canvas.drawCircle(playerX + PLAYER_SIZE / 3, playerY + PLAYER_SIZE / 3, eyeRadius, playerEyePaint);
            canvas.drawCircle(playerX + 2 * PLAYER_SIZE / 3, playerY + PLAYER_SIZE / 3, eyeRadius, playerEyePaint);

            // Draw score
            canvas.drawText(String.valueOf(score), 30, 80, scorePaint);

            // Draw high score
            canvas.drawText("HI: " + highScore, canvas.getWidth() - 200, 80, scorePaint);

            // Draw game over
            if (gameOver) {
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), gameOverPaint);
                Paint gameOverTextPaint = new Paint();
                gameOverTextPaint.setColor(0xFFFE2C55);
                gameOverTextPaint.setTextSize(100);
                gameOverTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.drawText("GAME OVER", 50, canvas.getHeight() / 2 - 50, gameOverTextPaint);

                Paint instructionPaint = new Paint();
                instructionPaint.setColor(Color.WHITE);
                instructionPaint.setTextSize(40);
                canvas.drawText("Tap to restart", 100, canvas.getHeight() / 2 + 100, instructionPaint);
            }

        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (gameOver) {
                resetGame();
            } else {
                playerVelocityY = JUMP_FORCE;
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public void stop() {
        running = false;
    }
}
