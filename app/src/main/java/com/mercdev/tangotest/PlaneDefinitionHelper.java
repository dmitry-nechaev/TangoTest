package com.mercdev.tangotest;

import android.opengl.Matrix;
import android.util.Log;

import com.google.atap.tangoservice.TangoException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

/**
 * Created by gnusin on 16.02.2017.
 */

public class PlaneDefinitionHelper {
    private static final String TAG = MainActivity.class.getSimpleName();

    /**
     * Use the TangoSupport library with point cloud data to calculate the plane
     * of the world feature pointed at the location the camera is looking.
     * It returns the transform of the fitted plane in a double array.
     */
    public static Matrix4 getFloorTransformMatrix4(float u, float v, TangoPointCloudData pointCloud, double rgbTimestamp) {
        // We need to calculate the transform between the color camera at the
        // time the user clicked and the depth camera at the time the depth
        // cloud was acquired.

        if (pointCloud == null) {
            return null;
        }

        TangoPoseData depthTcolorPose = TangoSupport.calculateRelativePose(pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                                                           rgbTimestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR);
        try {
            TangoPoseData devicePose = TangoSupport.getPoseAtTime(rgbTimestamp,
                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                    TangoSupport.ROTATION_IGNORED);

            if (devicePose.statusCode == TangoPoseData.POSE_VALID) {

                Quaternion deviceQuaternion = new Quaternion(devicePose.rotation[3],
                        devicePose.rotation[0],
                        devicePose.rotation[1],
                        devicePose.rotation[2]);

                double deviceRotationAngle = Math.toDegrees(deviceQuaternion.getRotationX());
                if (deviceRotationAngle <= 0) {
                    // Perform plane fitting with the latest available point cloud data.
                    double[] identityTranslation = {0.0, 0.0, 0.0};
                    double[] identityRotation = {0.0, 0.0, 0.0, 1.0};

                    TangoSupport.IntersectionPointPlaneModelPair intersectionPointPlaneModelPair =
                            TangoSupport.fitPlaneModelNearPoint(pointCloud,
                                    identityTranslation, identityRotation, u, v, TangoSupport.ROTATION_IGNORED,
                                    depthTcolorPose.translation, depthTcolorPose.rotation);

                    double[] upVector = {0, 1, 0, 0};

                    double[] planeModel = intersectionPointPlaneModelPair.planeModel;
                    Vector3 planeNormalVector = new Vector3(-planeModel[0], planeModel[1], -planeModel[2]);

                    double[] transformMatrix = new double[16];
                    deviceQuaternion.toRotationMatrix(transformMatrix);

                    double[] turnedUpVectorCoordinates = new double[4];
                    org.rajawali3d.math.Matrix.multiplyMV(turnedUpVectorCoordinates, 0, transformMatrix, 0, upVector, 0);
                    Vector3 turnedUpVector = new Vector3(turnedUpVectorCoordinates[0], turnedUpVectorCoordinates[1], turnedUpVectorCoordinates[2]);
                    double incidenceAngle = planeNormalVector.angle(turnedUpVector);

                    if (incidenceAngle <= 5) {
                        // Get the transform from depth camera to OpenGL world at the timestamp of the cloud.
                        TangoSupport.TangoMatrixTransformData transform =
                                TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
                                        TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                        TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                        TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                        TangoSupport.ROTATION_IGNORED);
                        if (transform.statusCode == TangoPoseData.POSE_VALID) {
                            Matrix4 openGlTPlane = getPlaneTransformMatrix4(
                                    intersectionPointPlaneModelPair.intersectionPoint,
                                    intersectionPointPlaneModelPair.planeModel, transform.matrix);

                            return openGlTPlane;
                        } else {
                            Log.w(TAG, "Can't get depth camera transform at time " + pointCloud.timestamp);
                        }
                    }
                }
            } else {
                Log.w(TAG, "Can't get color camera at time " + rgbTimestamp);
            }
        } catch (TangoException e) {
            Log.d(TAG, "Failed to floor plane definition. " + e.getMessage());
        }
        return null;
    }


    /**
     * Calculate the pose of the plane based on the position and normal orientation of the plane
     * and align it with gravity.
     * @param planePoint                    The point on plane (in local coordinate system)
     * @param planeNormal                   The normal vector of plane (in local coordinate system)
     * @param localTransformMatrix          The matrix for transformation from world's coordinate system to a local coordinate system
     */
    public static Matrix4 getPlaneTransformMatrix4(double[] planePoint, double[] planeNormal, float[] localTransformMatrix) {
        // Vector aligned to gravity which transforms in local coordinate system
        float[] openGlUp = new float[]{0, 1, 0, 0};
        float[] openGlTransformMatrix = new float[16];
        Matrix.invertM(openGlTransformMatrix, 0, localTransformMatrix, 0);
        float[] localUp = new float[4];
        Matrix.multiplyMV(localUp, 0, openGlTransformMatrix, 0, openGlUp, 0);
        // Create the plane matrix transform in local coordinate system from a point, the plane normal and the
        // up vector.
        float[] depthTplane = matrixFromPointNormalUp(planePoint, planeNormal, localUp);
        float[] openGlTplane = new float[16];
        // Converting plane coordinates to world's system coordinates.
        // It will allow to get translate and rotation
        // relative of world's coordinate system reference point
        Matrix.multiplyMM(openGlTplane, 0, localTransformMatrix, 0, depthTplane, 0);
        return new Matrix4(openGlTplane);
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
