package com.mercdev.tangotest;

import android.graphics.Color;
import android.opengl.GLES20;
import android.support.annotation.FloatRange;

import org.rajawali3d.cameras.Camera;
import org.rajawali3d.materials.Material;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Plane;

/**
 * Created by gnusin on 20.03.2017.
 */

public class FloatObjectFinder extends Plane {
    private static final int DEFAULT_COLOR = Color.argb(255, 255, 0, 0);
    private static final int FIND_COLOR = Color.argb(255, 0, 128, 0);

    private float depthPosition = 1.0f;

    public FloatObjectFinder(float width, float height) {
        super(width, height, 1, 1);
        init();
    }

    public void setDepthPosition(@FloatRange(from = 0, to = Float.MAX_VALUE) float depthPosition) {
        this.depthPosition = depthPosition;
    }

    public void setOrientation(Vector3 translation, Quaternion rotation) {
        double[] startPosition = {0.0f, 0.0f, -depthPosition, 1.0f};
        double[] transformMatrix = new double[16];
        rotation.toRotationMatrix(transformMatrix);
        transformMatrix[Matrix4.M03] = translation.x;
        transformMatrix[Matrix4.M13] = translation.y;
        transformMatrix[Matrix4.M23] = translation.z;
        double[] newPosition = new double[4];
        org.rajawali3d.math.Matrix.multiplyMV(newPosition, 0, transformMatrix, 0, startPosition, 0);

        setRotation(rotation);
        setPosition(newPosition[0], newPosition[1], newPosition[2]);
    }

    public void onFind() {
        setColor(FIND_COLOR);
    }

    public void onLose() {
        setColor(DEFAULT_COLOR);
    }

    @Override
    public void renderColorPicking(Camera camera, Material pickingMaterial) {
    }

    private void init() {
        Material objectFinderMaterial = new Material();
        objectFinderMaterial.enableLighting(false);

        setMaterial(objectFinderMaterial);
        setColor(DEFAULT_COLOR);
        setDrawingMode(GLES20.GL_TRIANGLES);
        setBlendingEnabled(true);
        setBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }
}
