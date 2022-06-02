package com.trees.common.rendering;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


public class DrawingView extends SurfaceView implements SurfaceHolder.Callback {


    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        holder.setFormat(PixelFormat.TRANSPARENT);
    }

    private void drawLines(SurfaceHolder holder, Canvas canvas) {

        Paint paint = new Paint();
        paint.setColor(Color.RED);
        Rect frame = holder.getSurfaceFrame();
        long x1 =  Math.round(frame.width()/3.0);
        long x2 = Math.round(2 * frame.width()/3.0);
        canvas.drawLine(x1, 0, x1, frame.height(), paint);
        canvas.drawLine(x1 - 1, 0, x1 - 1, frame.height(), paint);
        canvas.drawLine(x2, 0, x2, frame.height(), paint);
        canvas.drawLine(x2 - 1, 0, x2 - 1, frame.height(), paint);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Canvas canvas = holder.lockCanvas();
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        drawLines(holder, canvas);
        holder.unlockCanvasAndPost(canvas);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}
