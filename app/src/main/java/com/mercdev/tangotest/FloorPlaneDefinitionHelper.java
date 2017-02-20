package com.mercdev.tangotest;

import android.opengl.Matrix;
import android.util.Log;

import com.google.atap.tangoservice.TangoException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.math.Matrix4;

/**
 * Created by gnusin on 16.02.2017.
 */

public class FloorPlaneDefinitionHelper {
    private static final String TAG = MainActivity.class.getSimpleName();

    /**
     * Use the TangoSupport library with point cloud data to calculate the plane
     * of the world feature pointed at the location the camera is looking.
     * It returns the transform of the fitted plane in a double array.
     */
    public static double getCameraHeightFromFloor(TangoPointCloudData pointCloud, double rgbTimestamp, int colorCameraToDisplayAndroidRotation) {
        // We need to calculate the transform between the color camera at the
        // time the user clicked and the depth camera at the time the depth
        // cloud was acquired.
        TangoPoseData depthTcolorPose = TangoSupport.calculateRelativePose(pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                                                           rgbTimestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR);
        try {
            float[] uv = getColorCameraUVFromDisplay(0.5f, 0.5f, colorCameraToDisplayAndroidRotation);
            // Perform plane fitting with the latest available point cloud data.
            double[] identityTranslation = {0.0, 0.0, 0.0};
            double[] identityRotation = {0.0, 0.0, 0.0, 1.0};

            TangoSupport.IntersectionPointPlaneModelPair intersectionPointPlaneModelPair =
                    TangoSupport.fitPlaneModelNearPoint(pointCloud,
                            identityTranslation, identityRotation, uv[0], uv[1],
                            depthTcolorPose.translation, depthTcolorPose.rotation);
            if (intersectionPointPlaneModelPair != null) {
                return intersectionPointPlaneModelPair.intersectionPoint[2];
            }
            /*} else {
                Log.w(TAG, "Can't get depth camera transform at time " + pointCloud.timestamp);
            }*/
        } catch (TangoException e) {
            Log.d(TAG, "Failed to floor plane definition. " + e.getMessage());
        }
        return 0.0;
    }

    private static float[] getColorCameraUVFromDisplay(float u, float v, int colorToDisplayRotation) {
        switch (colorToDisplayRotation) {
            case 1:
                return new float[]{1.0f - v, u};
            case 2:
                return new float[]{1.0f - u, 1.0f - v};
            case 3:
                return new float[]{v, 1.0f - u};
            default:
                return new float[]{u, v};
        }
    }

    /**
     * Calculate the pose of the plane based on the position and normal orientation of the plane
     * and align it with gravity.
     */
    private static float[] calculatePlaneTransform(double[] point, double normal[],
                                            float[] openGlTdepth) {
        // Vector aligned to gravity.
        float[] openGlUp = new float[]{0, 1, 0, 0};
        float[] depthTOpenGl = new float[16];
        Matrix.invertM(depthTOpenGl, 0, openGlTdepth, 0);
        float[] depthUp = new float[4];
        Matrix.multiplyMV(depthUp, 0, depthTOpenGl, 0, openGlUp, 0);
        // Create the plane matrix transform in depth frame from a point, the plane normal and the
        // up vector.
        float[] depthTplane = matrixFromPointNormalUp(point, normal, depthUp);
        float[] openGlTplane = new float[16];
        Matrix.multiplyMM(openGlTplane, 0, openGlTdepth, 0, depthTplane, 0);
        return openGlTplane;
    }

    /**
     * Calculates a transformation matrix based on a point, a normal and the up gravity vector.
     * The coordinate frame of the target transformation will a right handed system with Z+ in
     * the direction of the normal and Y+ up.
     */
    private static float[] matrixFromPointNormalUp(double[] point, double[] normal, float[] up) {
        float[] zAxis = new float[]{(float) normal[0], (float) normal[1], (float) normal[2]};
        normalize(zAxis);
        float[] xAxis = crossProduct(up, zAxis);
        normalize(xAxis);
        float[] yAxis = crossProduct(zAxis, xAxis);
        normalize(yAxis);
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        m[0] = xAxis[0];
        m[1] = xAxis[1];
        m[2] = xAxis[2];
        m[4] = yAxis[0];
        m[5] = yAxis[1];
        m[6] = yAxis[2];
        m[8] = zAxis[0];
        m[9] = zAxis[1];
        m[10] = zAxis[2];
        m[12] = (float) point[0];
        m[13] = (float) point[1];
        m[14] = (float) point[2];
        return m;
    }

    /**
     * Normalize a vector.
     */
    private static void normalize(float[] v) {
        double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= norm;
        v[1] /= norm;
        v[2] /= norm;
    }

    /**
     * Cross product between two vectors following the right hand rule.
     */
    private static float[] crossProduct(float[] v1, float[] v2) {
        float[] result = new float[3];
        result[0] = v1[1] * v2[2] - v2[1] * v1[2];
        result[1] = v1[2] * v2[0] - v2[2] * v1[0];
        result[2] = v1[0] * v2[1] - v2[0] * v1[1];
        return result;
    }

}
