package com.z68.birdtracker;

import java.nio.ByteBuffer;

/**
 * Lightweight Android replacement for the Termux/OpenCV MOG2 loop.
 * It consumes an NV21 Y plane and produces a horizontal target position.
 *
 * Deliberately NO FIRE command is generated. Only VEL:<speed> is sent.
 */
public final class TrackerEngine {

    public interface Output {
        void onTarget(boolean visible, float x, float y, float errorPx, float area, String state);
        void onVelocity(float velocity);
        void onFire(int durationMs);
    }

    private final Output output;
    private final int width;
    private final int height;
    private final int gridW;
    private final int gridH;

    private final float[] background;
    private final byte[] active;

    private static final int SAMPLE = 4;
    private static final int DIFF_THRESHOLD = 34;
    private static final int MIN_ACTIVE = 18;
    private static final int MAX_ACTIVE = 1800;
    private static final float BG_LEARN = 0.025f;

    private float filtX = Float.NaN;
    private float filtV = 0f;
    private long lastSeenMs = 0L;
    private long pursuitStartMs = 0L;
    private float homePositionSteps = 0f;
    private float currentPositionSteps = 0f;
    private long lastUpdateNs = 0L;

    private boolean enabled = true;
    private boolean warming = true;
    private int warmFrames = 0;

    // Matches the original Termux defaults reasonably closely.
    private static final float DEADBAND_PX = 8f;
    private static final float GAIN = 12.5f;
    private static final float MIN_SPEED = 30f;
    private static final float MAX_SPEED = 500f;
    private static final float MAX_JUMP_PX = 150f;
    private static final float LOST_GRACE_MS = 400f;
    private static final float RETURN_HOME_DELAY_MS = 1500f;
    private static final float CENTER_HOLD_MS = 100f;
    private static final float FIRE_COOLDOWN_MS = 4000f;
    private static final int FIRE_DURATION_MS = 200;
    private long centeredSinceMs = 0L;
    private long lastFireMs = 0L;
    private static final float LEFT_LIMIT = -225f;
    private static final float RIGHT_LIMIT = 225f;

    public TrackerEngine(int width, int height, Output output) {
        this.width = width;
        this.height = height;
        this.gridW = width / SAMPLE;
        this.gridH = height / SAMPLE;
        this.background = new float[gridW * gridH];
        this.active = new byte[gridW * gridH];
        this.output = output;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) output.onVelocity(0f);
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setHomeNow() {
        output.onVelocity(0f);
        homePositionSteps = currentPositionSteps;
    }

    public synchronized void resetSoftwarePosition() {
        currentPositionSteps = 0f;
        homePositionSteps = 0f;
        output.onVelocity(0f);
    }

    public void onFrame(ByteBuffer frame) {
        if (!enabled) return;

        long nowNs = System.nanoTime();
        float dt = lastUpdateNs == 0 ? 0.033f : Math.min(0.1f, (nowNs - lastUpdateNs) / 1_000_000_000f);
        lastUpdateNs = nowNs;

        ByteBuffer b = frame.duplicate();
        b.rewind();

        // NV21's first width*height bytes are the Y plane.
        if (b.remaining() < width * height) return;

        float prevPos = Float.NaN;
        if (!Float.isNaN(filtX)) prevPos = filtX + width / 2f;

        int idx = 0;
        int activeCount = 0;
        int minX = gridW, minY = gridH, maxX = -1, maxY = -1;
        long sumX = 0, sumY = 0;

        for (int gy = 0; gy < gridH; gy++) {
            int y = gy * SAMPLE;
            for (int gx = 0; gx < gridW; gx++, idx++) {
                int x = gx * SAMPLE;
                int yi = y * width + x;
                int lum = b.get(yi) & 0xff;

                if (warming) {
                    background[idx] = lum;
                    active[idx] = 0;
                    continue;
                }

                float bg = background[idx];
                int diff = Math.abs(lum - Math.round(bg));

                boolean on = diff >= DIFF_THRESHOLD;
                active[idx] = (byte)(on ? 1 : 0);

                // Learn the background slowly; don't absorb strong motion immediately.
                if (!on) {
                    background[idx] = bg + (lum - bg) * BG_LEARN;
                }

                if (on) {
                    activeCount++;
                    minX = Math.min(minX, gx);
                    maxX = Math.max(maxX, gx);
                    minY = Math.min(minY, gy);
                    maxY = Math.max(maxY, gy);
                    sumX += x;
                    sumY += y;
                }
            }
        }

        if (warming) {
            warmFrames++;
            if (warmFrames >= 45) warming = false;
            output.onTarget(false, 0f, 0f, 0f, 0f, "WARMING UP");
            output.onVelocity(0f);
            return;
        }

        long nowMs = System.currentTimeMillis();

        // Reject almost the entire frame being active.
        boolean valid = activeCount >= MIN_ACTIVE && activeCount <= MAX_ACTIVE;

        float cx = 0f, cy = 0f;
        if (valid) {
            cx = sumX / (float)activeCount;
            cy = sumY / (float)activeCount;

            // Keep lock stable when the motion jumps wildly.
            if (!Float.isNaN(prevPos) && Math.abs(cx - prevPos) > MAX_JUMP_PX) {
                valid = false;
            }
        }

        currentPositionSteps += lastVelocity * dt;
        currentPositionSteps = Math.max(LEFT_LIMIT, Math.min(RIGHT_LIMIT, currentPositionSteps));

        if (valid) {
            lastSeenMs = nowMs;
            if (pursuitStartMs == 0L) pursuitStartMs = nowMs;

            float measurement = cx - width / 2f;

            if (Float.isNaN(filtX)) {
                filtX = measurement;
                filtV = 0f;
            } else {
                float predicted = filtX + filtV * dt;
                float residual = measurement - predicted;
                filtX = predicted + 0.55f * residual;
                filtV = filtV + 0.25f * residual / Math.max(dt, 0.001f);
            }

            float absErr = Math.abs(filtX);
            if (absErr <= DEADBAND_PX) {
                setVelocityInternal(0f);
                if (centeredSinceMs == 0L) centeredSinceMs = nowMs;
                if ((nowMs - centeredSinceMs) >= CENTER_HOLD_MS &&
                        (nowMs - lastFireMs) >= FIRE_COOLDOWN_MS) {
                    output.onFire(FIRE_DURATION_MS);
                    lastFireMs = nowMs;
                }
                output.onTarget(true, cx, cy, filtX, activeCount, "CENTERED");
            } else {
                centeredSinceMs = 0L;
                float speed = Math.min(MAX_SPEED, Math.max(MIN_SPEED, absErr * GAIN));
                float v = filtX > 0 ? speed : -speed;
                setVelocityInternal(v);
                output.onTarget(true, cx, cy, filtX, activeCount, "TRACKING");
            }
            return;
        }

        float sinceSeen = nowMs - lastSeenMs;
        if (lastSeenMs != 0L && sinceSeen < LOST_GRACE_MS) {
            output.onTarget(false, 0f, 0f, 0f, 0f, "COASTING");
            return;
        }

        if (lastSeenMs != 0L && sinceSeen >= RETURN_HOME_DELAY_MS) {
            float errHome = homePositionSteps - currentPositionSteps;
            if (Math.abs(errHome) <= 3f) {
                setVelocityInternal(0f);
                output.onTarget(false, 0f, 0f, 0f, 0f, "HOME");
            } else {
                float v = Math.max(MIN_SPEED, Math.min(MAX_SPEED, Math.abs(errHome) * 3f));
                setVelocityInternal(errHome > 0 ? v : -v);
                output.onTarget(false, 0f, 0f, 0f, 0f, "RETURNING HOME");
            }
        } else {
            setVelocityInternal(0f);
            output.onTarget(false, 0f, 0f, 0f, 0f, "IDLE");
        }
    }

    private float lastVelocity = 0f;

    private void setVelocityInternal(float v) {
        v = Math.max(LEFT_LIMIT < 0 ? -MAX_SPEED : -MAX_SPEED,
                     Math.min(MAX_SPEED, v));
        lastVelocity = v;
        output.onVelocity(v);
    }
}
