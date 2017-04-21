package com.mercdev.tangotest;

import android.graphics.Color;
import android.opengl.GLES20;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;

import org.rajawali3d.Object3D;
import org.rajawali3d.cameras.Camera;
import org.rajawali3d.materials.Material;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.util.OnObjectPickedListener;

import java.util.ArrayList;

/**
 * Created by gnusin on 20.03.2017.
 */

public class FloatObjectFinder extends Plane implements OnObjectPickedListener {
    private static final int DEFAULT_COLOR = Color.argb(255, 255, 0, 0);
    private static final int FIND_COLOR = Color.argb(255, 0, 128, 0);


    private Object3D foundObject;
    private float depthPosition = 1.0f;
    private boolean isHidden = false;
    private double distanceToFoundObject = 0.0d;
    private OnFloatObjectFinderListener listener;

    public FloatObjectFinder(float width, float height) {
        super(width, height, 1, 1);
        init();
    }

    public void setDepthPosition(@FloatRange(from = 0, to = Float.MAX_VALUE) float depthPosition) {
        this.depthPosition = depthPosition;
    }

    public void hide() {
        isHidden = true;
    }

    public void show() {
        isHidden = false;
    }

    public void setOnFloatObjectFinderListener(OnFloatObjectFinderListener listener) {
        this.listener = listener;
    }

    @Override
    public void onObjectPicked(@NonNull Object3D object) {
        this.foundObject = object;
        setColor(FIND_COLOR);
        listener.onObjectFound(object);
    }

    @Override
    public void onNoObjectPicked() {
        foundObject = null;
        setColor(DEFAULT_COLOR);
        listener.onObjectNoFound();
    }

    @Override
    public void renderColorPicking(Camera camera, Material pickingMaterial) {

    }

    @Override
    public void render(Camera camera, Matrix4 vpMatrix, Matrix4 projMatrix, Matrix4 vMatrix, Material sceneMaterial) {
        if (!isHidden) {
            if (calculatePositionAndOrientation(camera)) {
                super.render(camera, vpMatrix, projMatrix, vMatrix, sceneMaterial);
            }
            if (foundObject != null && listener != null) {
                listener.onMeasureDistance(distanceToFoundObject);
            }
        }
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

    private boolean calculatePositionAndOrientation(Camera camera) {
        distanceToFoundObject = 0.0d;
        boolean isCalculated = false;

        if (foundObject == null) {
            // Calculate transform matrix for the plane in front of the camera
            double[] normalPlane = {0.0f, 0.0f, 1.0f};
            double[] planePoint = {0.0f, 0.0f, -depthPosition};
            float[] cameraModelMatrix = new float[16];
            camera.getModelMatrix().toFloatArray(cameraModelMatrix);

            Matrix4 transformMatrix4 = PlaneDefinitionHelper.getPlaneTransformMatrix4(planePoint, normalPlane, cameraModelMatrix);
            setScale(depthPosition);
            Vector3 newPosition = transformMatrix4.getTranslation();
            setPosition(newPosition);
            Quaternion newOrientation = new Quaternion().fromMatrix(transformMatrix4);
            setOrientation(newOrientation);

            isCalculated = true;
        } else {
            Quaternion cameraOrientation = camera.getOrientation();
            cameraOrientation.conjugate();
            double[] cameraOrientationMatrix = new double[16];
            cameraOrientation.toRotationMatrix(cameraOrientationMatrix);

            ArrayList<FixtureRectangularPrism.Facet> objectFacets = ((FixtureRectangularPrism) foundObject).getFacets();
            for (FixtureRectangularPrism.Facet facet : objectFacets) {
                // identify face object facet
                double[] normal = {facet.getNormal().x, facet.getNormal().y, facet.getNormal().z, 1};
                double[] tNormal = new double[4];
                org.rajawali3d.math.Matrix.multiplyMV(tNormal, 0, cameraOrientationMatrix, 0, normal, 0);
                if (tNormal[2] > 0) {
                    // transform coordinates of object vertices to camera coordinate system
                    double[] modelViewMatrix = new double[16];
                    foundObject.getModelViewMatrix().toArray(modelViewMatrix);

                    Vector3[] tVertices = new Vector3[4];
                    for (int i = 0; i < 4; i++) {
                        double[] vertex = {facet.getVertices()[i].x, facet.getVertices()[i].y, facet.getVertices()[i].z, 1};
                        double[] tVertex = new double[4];
                        org.rajawali3d.math.Matrix.multiplyMV(tVertex, 0, modelViewMatrix, 0, vertex, 0);
                        tVertices[i] = new Vector3(tVertex[0], tVertex[1], tVertex[2]);
                    }

                    // identify intersection of Oz with facet
                    if (((((tVertices[1].x - tVertices[0].x) * (0 - tVertices[0].y)) - ((tVertices[1].y - tVertices[0].y) * (0 - tVertices[0].x))) > 0) &&
                            ((((tVertices[2].x - tVertices[1].x) * (0 - tVertices[1].y)) - ((tVertices[2].y - tVertices[1].y) * (0 - tVertices[1].x))) > 0) &&
                            ((((tVertices[3].x - tVertices[2].x) * (0 - tVertices[2].y)) - ((tVertices[3].y - tVertices[2].y) * (0 - tVertices[2].x))) > 0) &&
                            ((((tVertices[0].x - tVertices[3].x) * (0 - tVertices[3].y)) - ((tVertices[0].y - tVertices[3].y) * (0 - tVertices[3].x))) > 0)) {

                            /* define equation of plane by 3 points
                               A = y0 * (z1 - z2) + y1 * (z2 - z0) + y2 * (z0 - z1)
                               B = z0 * (x1 - x2) + z1 * (x2 - x0) + z2 * (x0 - x1)
                               C = x0 * (y1 - y2) + x1 * (y2 - y0) + x2 * (y0 - y1)
                               -D = x0 * (y1 * z2 - y2 * z1) + x1 * (y2 * z0 - y0 * z2) + x2 * (y0 * z1 - y1 * z0)
                            */

                        double a = tVertices[0].y * (tVertices[1].z - tVertices[2].z) + tVertices[1].y * (tVertices[2].z - tVertices[0].z) + tVertices[2].y * (tVertices[0].z - tVertices[1].z);
                        double b = tVertices[0].z * (tVertices[1].x - tVertices[2].x) + tVertices[1].z * (tVertices[2].x - tVertices[0].x) + tVertices[2].z * (tVertices[0].x - tVertices[1].x);
                        double c = tVertices[0].x * (tVertices[1].y - tVertices[2].y) + tVertices[1].x * (tVertices[2].y - tVertices[0].y) + tVertices[2].x * (tVertices[0].y - tVertices[1].y);
                        double d = -(tVertices[0].x * (tVertices[1].y * tVertices[2].z - tVertices[2].y * tVertices[1].z) + tVertices[1].x * (tVertices[2].y * tVertices[0].z - tVertices[0].y * tVertices[2].z) +
                                   tVertices[2].x * (tVertices[0].y * tVertices[1].z - tVertices[1].y * tVertices[0].z));

                        // calculate distance to found plane considering position of objects in perspective
                        double[] projectionMatrix = new double[16];
                        camera.getProjectionMatrix().toArray(projectionMatrix);

                        double[] distanceViewVector = {0, 0, -d / c, 1};
                        double[] distanceProjectionVector = new double[4];
                        org.rajawali3d.math.Matrix.multiplyMV(distanceProjectionVector, 0, projectionMatrix, 0, distanceViewVector, 0);

                        double cameraNear = camera.getProjectionMatrix().getTranslation().z / (camera.getProjectionMatrix().getTranslation().z - 2.0);
                        double distanceToPlane = distanceProjectionVector[2] + cameraNear;

                        // continue the calculations if found plane is located in front of camera
                        if (distanceToPlane > 0) {
                            // calculate intersection point of Oz with plane
                            // need to reduce distance to plane, that ObjectFinder floats on plane and doesn't flicker
                            double[] tIntersectionPoint = {0, 0, -d / c * 0.95, 1};

                            double[] normalPlane = {a, b, c};

                            float[] cameraModelMatrix = new float[16];
                            camera.getModelMatrix().toFloatArray(cameraModelMatrix);

                            Matrix4 transformMatrix4 = PlaneDefinitionHelper.getPlaneTransformMatrix4(tIntersectionPoint, normalPlane, cameraModelMatrix);
                            setScale(tIntersectionPoint[2]);
                            Vector3 translation = transformMatrix4.getTranslation();
                            setPosition(translation);
                            Quaternion orientationQuaternion = new Quaternion().fromMatrix(transformMatrix4);
                            setOrientation(orientationQuaternion);
                            setRotZ(0);

                            distanceToFoundObject = distanceToPlane;

                            isCalculated = true;
                            break;
                        }
                    }
                }
            }
        }

        return isCalculated;
    }

    public interface OnFloatObjectFinderListener {

        void onObjectFound(Object3D foundObject);

        void onObjectNoFound();

        void onMeasureDistance(double distance);

    }
}
