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
            paint.setColor(Color.BLACK);
            int x1 = (int) ((-minX + fixture.getPosition().x) * delta);
            int y1 = (int) ((-minY + fixture.getPosition().y) * delta);
            int x2 = (int) ((-minX + fixture.getPosition().x + fixture.getWidth()) * delta);
            int y2 = (int) ((-minY + fixture.getPosition().y + fixture.getDepth()) * delta);
            canvas.drawRect(new Rect(x1, y1, x2, y2), paint);
        }

        paint.setColor(Color.BLUE);
        canvas.drawCircle((-minX + cameraX) * delta, (-minY + cameraY)* delta, 30 * delta, paint);
    }

    public void setFixtures(ArrayList<Fixture> fixtures, Resources resources) {
        this.fixtures = fixtures;
        if (fixtures.size() > 0) {
            minX = fixtures.get(0).getPosition().x;
            maxX = fixtures.get(0).getPosition().x + fixtures.get(0).getWidth();
            minY = fixtures.get(0).getPosition().y;
            maxY = fixtures.get(0).getPosition().y + fixtures.get(0).getDepth();
            for (Fixture fixture : fixtures) {
                if (fixture.getPosition().x < minX) {
                    minX = fixture.getPosition().x;
                }
                if (fixture.getPosition().x + fixture.getWidth() > maxX) {
                    maxX = fixture.getPosition().x + fixture.getWidth();
                }
                if (fixture.getPosition().y < minY) {
                    minY = fixture.getPosition().y;
                }
                if (fixture.getPosition().y + fixture.getHeight() > maxY) {
                    maxY = fixture.getPosition().y + fixture.getDepth();
                }
            }
        }
        minX -= LAYOUT_PADDING;
        maxX += LAYOUT_PADDING;
        minY -= LAYOUT_PADDING;
        maxY += LAYOUT_PADDING;

        float deltaX = Math.abs((float) resources.getDimensionPixelSize(R.dimen.minimap_width) / (maxX - minX));
        float deltaY = Math.abs((float) resources.getDimensionPixelSize(R.dimen.minimap_height) / (maxY - minY));
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
