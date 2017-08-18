package com.mercdev.tangotest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.google.atap.tangoservice.TangoPoseData;

import org.rajawali3d.math.Quaternion;

import java.util.ArrayList;

/**
 * Created by nechaev on 13.02.2017.
 */

public class Minimap extends View {

    private final int LAYOUT_PADDING = 16;
    private Paint paint;
    private int minX, minY, maxX, maxY;
    private float delta = 0.25f;
    private int cameraX, cameraY;
    private double cameraRotation;
    private double cameraYaw, cameraPitch, cameraRoll;

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
        float cx = (-minX + cameraX) * delta;
        float cy = (-minY + cameraY) * delta;
        canvas.translate(getWidth() * 0.5f - cx, getHeight() * 0.5f - cy);

        float radius = 30 * delta;
        paint.setColor(Color.BLUE);
        paint.setAlpha(128);
        float sweepAngle = 60f;
        int radiusMultiplier = 4;
        canvas.drawArc(cx - radiusMultiplier * radius,
                cy - radiusMultiplier * radius,
                cx + radiusMultiplier * radius,
                cy + radiusMultiplier * radius,
                -90 - sweepAngle * 0.5f - (float) Math.toDegrees(0), sweepAngle, true, paint);
        paint.setAlpha(255);
        canvas.drawCircle(cx, cy, radius, paint);

        canvas.rotate((float) Math.toDegrees(cameraRotation),
                (-minX + cameraX) * delta,
                (-minY + cameraY) * delta);

        ArrayList<Fixture> fixtures = FixturesRepository.getInstance().getFixtures();
        for (Fixture fixture : fixtures) {
            canvas.save();
            paint.setColor(Color.BLACK);
            int x1 = (int) ((-minX + fixture.getPosition().x) * delta);
            int y1 = (int) ((-minY + fixture.getPosition().y) * delta);
            int x2 = (int) ((-minX + fixture.getPosition().x + fixture.getWidth()) * delta);
            int y2 = (int) ((-minY + fixture.getPosition().y + fixture.getDepth()) * delta);
            canvas.rotate((float) fixture.getRotationAngle(),
                    (-minX + fixture.getPosition().x + fixture.getWidth() * 0.5f) * delta,
                    (-minY + fixture.getPosition().y + fixture.getDepth() * 0.5f) * delta);
            canvas.drawRect(new Rect(x1, y1, x2, y2), paint);
            canvas.restore();
        }
    }

    public void processFixtures() {
        ArrayList<Fixture> fixtures = FixturesRepository.getInstance().getFixtures();
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
    }

    public void setCameraPosition(TangoPoseData cameraPose) {
        float[] rotation = cameraPose.getRotationAsFloats();
        Quaternion q = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
        cameraRotation = q.getRotationY();
        cameraYaw = Math.atan2(2.0 * (q.y * q.z + q.w * q.x), q.w * q.w - q.x * q.x - q.y * q.y + q.z * q.z);
        cameraPitch = Math.asin(-2.0 * (q.x * q.z - q.w * q.y));
        cameraRoll = Math.atan2(2.0 * (q.x * q.y + q.w * q.z), q.w * q.w + q.x * q.x - q.y * q.y - q.z * q.z);
        float[] translation = cameraPose.getTranslationAsFloats();
        cameraX = (int) (translation[0] * 100);
        cameraY = (int) (translation[2] * 100);
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
        processFixtures();
    }

    private void calculateDelta() {
        float deltaX = Math.abs((float) getResources().getDimensionPixelSize(R.dimen.minimap_width) / (maxX - minX));
        float deltaY = Math.abs((float) getResources().getDimensionPixelSize(R.dimen.minimap_height) / (maxY - minY));
        delta = Math.min(deltaX, deltaY);
    }

}
