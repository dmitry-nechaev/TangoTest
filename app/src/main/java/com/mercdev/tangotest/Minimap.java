package com.mercdev.tangotest;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.google.atap.tangoservice.TangoPoseData;

import java.util.ArrayList;

/**
 * Created by nechaev on 13.02.2017.
 */

public class Minimap extends View {

    private final int LAYOUT_PADDING = 16;

    private Paint paint;
    private ArrayList<Fixture> fixtures;
    private int minX, minY, maxX, maxY;
    private float delta;
    private int cameraX, cameraY;

    public Minimap(Context context) {
        super(context);
        init();
    }

    public Minimap(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public void onDraw(Canvas canvas) {
        for (Fixture fixture : fixtures) {
            paint.setColor(fixture.getColor());
            canvas.drawRect(
                    new Rect((int) Math.abs(fixture.getRect().left * delta - minX),
                            (int) Math.abs(fixture.getRect().top * delta - minY),
                            (int) Math.abs(fixture.getRect().right * delta - minX),
                            (int) Math.abs(fixture.getRect().bottom * delta - minY)), paint);
        }

        paint.setColor(Color.BLUE);
        canvas.drawCircle(cameraX * delta - minX, cameraY * delta - minY, 10, paint);
    }

    public void setFixtures(ArrayList<Fixture> fixtures, Resources resources) {
        this.fixtures = fixtures;
        if (fixtures.size() > 0) {
            minX = fixtures.get(0).getRect().left;
            maxX = fixtures.get(0).getRect().right;
            minY = fixtures.get(0).getRect().top;
            maxY = fixtures.get(0).getRect().bottom;
            for (Fixture fixture : fixtures) {
                if (fixture.getRect().left < minX) {
                    minX = fixture.getRect().left;
                }
                if (fixture.getRect().right > maxX) {
                    maxX = fixture.getRect().right;
                }
                if (fixture.getRect().top < minY) {
                    minY = fixture.getRect().top;
                }
                if (fixture.getRect().bottom > maxY) {
                    maxY = fixture.getRect().bottom;
                }
            }
        }
        minX-= LAYOUT_PADDING;
        maxX+= LAYOUT_PADDING;
        minY-= LAYOUT_PADDING;
        maxY+= LAYOUT_PADDING;

        float deltaX = (float) (maxX - minX) / resources.getDimensionPixelSize(R.dimen.minimap_width);
        float deltaY = (float) (maxY - minY) / resources.getDimensionPixelSize(R.dimen.minimap_height);
        delta = Math.min(deltaX, deltaY);
    }

    public void setCameraPosition(TangoPoseData cameraPose) {
        float[] rotation = cameraPose.getRotationAsFloats();
        float[] translation = cameraPose.getTranslationAsFloats();
        cameraX = (int) (translation[0] * 100);
        cameraY = (int) (translation[2] * 100);
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
    }

}
