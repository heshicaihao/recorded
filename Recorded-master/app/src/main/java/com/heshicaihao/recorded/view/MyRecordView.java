package com.heshicaihao.recorded.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.heshicaihao.recorded.R;

public class MyRecordView extends View {

    private Paint paint;

    private int downColor;
    private int upColor;

    private float slideDis;

    private float radiusDis;
    private float currentRadius;
    private float downRadius;
    private float upRadius;

    private float strokeWidthDis;
    private float currentStrokeWidth;
    private float minStrokeWidth;
    private float maxStrokeWidth;

    public MyRecordView(Context context) {
        super(context);
        init();
    }

    public MyRecordView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MyRecordView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {

        downColor = R.color.video_gray;
        upColor = R.color.white;

        paint = new Paint();
        paint.setAntiAlias(true);//抗锯齿
        paint.setStyle(Paint.Style.STROKE);//画笔属性是空心圆
        currentStrokeWidth = getResources().getDimension(R.dimen.dp10);
        paint.setStrokeWidth(currentStrokeWidth);//设置画笔粗细

        slideDis = getResources().getDimension(R.dimen.dp10);
        radiusDis = getResources().getDimension(R.dimen.dp3);
        strokeWidthDis = getResources().getDimension(R.dimen.dp1) / 4;

        minStrokeWidth = currentStrokeWidth;
        maxStrokeWidth = currentStrokeWidth * 2;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (downRadius == 0) {
            downRadius = getWidth() * 0.5f - currentStrokeWidth;
            upRadius = getWidth() * 0.3f - currentStrokeWidth;
        }
    }


    boolean changeStrokeWidth;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        changeStrokeWidth = false;
        currentStrokeWidth = minStrokeWidth;
        paint.setStrokeWidth(currentStrokeWidth);
        paint.setColor(ContextCompat.getColor(getContext(), upColor));
        if (currentRadius > upRadius) {
            currentRadius -= radiusDis;
            invalidate();
        } else if (currentRadius < upRadius) {
            currentRadius = upRadius;
            invalidate();
        }
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, currentRadius, paint);
    }
}
