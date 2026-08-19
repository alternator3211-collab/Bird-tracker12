package com.z68.birdtracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class TrackerOverlay extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile boolean targetVisible;
    private volatile float targetX;
    private volatile float targetY;
    private volatile boolean trackingEnabled = true;

    public TrackerOverlay(Context context) {
        super(context);
        paint.setStrokeWidth(3f);
        setWillNotDraw(false);
    }

    public void setState(boolean visible, float x, float y, boolean enabled) {
        targetVisible = visible;
        targetX = x;
        targetY = y;
        trackingEnabled = enabled;
        postInvalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xffffffff);
        canvas.drawLine(cx - 25, cy, cx + 25, cy, paint);
        canvas.drawLine(cx, cy - 25, cx, cy + 25, paint);
        canvas.drawCircle(cx, cy, 28, paint);

        if (targetVisible && trackingEnabled) {
            paint.setColor(0xffffcc00);
            canvas.drawCircle(targetX, targetY, 32, paint);
            canvas.drawLine(targetX - 45, targetY, targetX + 45, targetY, paint);
            canvas.drawLine(targetX, targetY - 45, targetX, targetY + 45, paint);
        }
    }
}
