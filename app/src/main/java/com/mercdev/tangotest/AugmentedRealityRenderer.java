/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mercdev.tangotest;

import android.content.Context;
import android.opengl.GLES20;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;

import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.renderer.Renderer;
import org.rajawali3d.util.ObjectColorPicker;

import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL10;

/**
 * Renderer that implements a basic augmented reality scene using Rajawali.
 * It creates a scene with a background quad taking the whole screen, where the color camera is
 * rendered, and a sphere with the texture of the earth floating ahead of the start position of
 * the Tango device.
 */
public class AugmentedRealityRenderer extends Renderer {
    private static final String TAG = AugmentedRealityRenderer.class.getSimpleName();

    private float[] textureCoords0 = new float[]{0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F};

    // Rajawali texture used to render the Tango color camera.
    private ATexture tangoCameraTexture;

    // Keeps track of whether the scene camera has been configured.
    private boolean sceneCameraConfigured;

    private TangoScreenQuad backgroundQuad;
    private ArrayList<Object3D> objects = new ArrayList<>();
    private Matrix4 transformFloorMatrix4;

    private boolean isFixturesVisible = true;

    private ObjectColorPicker picker;
    private FloatObjectFinder objectFinder;
    private FloatObjectFinder.OnFloatObjectFinderListener onFloatObjectFinderListener;

    public AugmentedRealityRenderer(Context context, FloatObjectFinder.OnFloatObjectFinderListener onFloatObjectFinderListener, Matrix4 transformFloorMatrix4) {
        super(context);
        this.transformFloorMatrix4 = transformFloorMatrix4;
        this.onFloatObjectFinderListener = onFloatObjectFinderListener;
    }

    @Override
    protected void initScene() {
        // Create a quad covering the whole background and assign a texture to it where the
        // Tango color camera contents will be rendered.
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);

        if (backgroundQuad == null) {
            backgroundQuad = new TangoScreenQuad();
            backgroundQuad.getGeometry().setTextureCoords(textureCoords0);
        }
        // We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
        // for GL_TEXTURE_EXTERNAL_OES rendering
        tangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try {
            tangoCameraMaterial.addTexture(tangoCameraTexture);
            backgroundQuad.setMaterial(tangoCameraMaterial);
        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception creating texture for RGB camera contents", e);
        }
        getCurrentScene().addChildAt(backgroundQuad, 0);

        objectFinder = new FloatObjectFinder(0.07f, 0.07f);
        objectFinder.setDepthPosition(4.0f);
        objectFinder.setOnFloatObjectFinderListener(onFloatObjectFinderListener);
        getCurrentScene().addChild(objectFinder);

        picker = new ObjectColorPicker(this);
        picker.setOnObjectPickedListener(objectFinder);

        // Add a directional light in an arbitrary direction.
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);

        Vector3 floorPlaneVector = transformFloorMatrix4.getTranslation();
        double cameraHeight = floorPlaneVector.y;

        Material material = new Material();
        material.enableLighting(true);
        material.setDiffuseMethod(new DiffuseMethod.Lambert());

        ArrayList<Fixture> fixtures = FixturesRepository.getInstance().getFixtures();

        if (fixtures != null && isFixturesVisible) {
            for (Fixture fixture : fixtures) {
                float width = (float) fixture.getWidth() / 100f;
                float height = (float) fixture.getHeight() / 100f;
                float depth = (float) fixture.getDepth() / 100f;
                FixtureRectangularPrism rect = new FixtureRectangularPrism(width, height, depth);
                rect.setPosition((double) fixture.getPosition().x / 100f + width * 0.5f, height * 0.5f + cameraHeight, (double) fixture.getPosition().y / 100f + depth * 0.5f);
                rect.setMaterial(material);
                rect.setColor(fixture.getColor());
                rect.setName(fixture.getName());
                rect.setDrawingMode(GLES20.GL_TRIANGLES);
                rect.setRotY(fixture.getRotationAngle());
                rect.setBackSided(true);
                rect.setDoubleSided(true);
                rect.setBlendingEnabled(true);
                rect.setBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                getCurrentScene().addChild(rect);
                objects.add(rect);
                picker.registerObject(rect);
            }
        }
    }

    /**
     * Update background texture's UV coordinates when device orientation is changed. i.e change
     * between landscape and portrait mode.
     * This must be run in the OpenGL thread.
     */
    public void updateColorCameraTextureUvGlThread(int rotation) {
        if (backgroundQuad == null) {
            backgroundQuad = new TangoScreenQuad();
        }

        float[] textureCoords =
                TangoSupport.getVideoOverlayUVBasedOnDisplayRotation(textureCoords0, rotation);
        backgroundQuad.getGeometry().setTextureCoords(textureCoords, true);
        backgroundQuad.getGeometry().reload();
    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The camera pose should match the pose of the camera color at the time the last rendered RGB
     * frame, which can be retrieved with this.getTimestamp();
     * <p/>
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData cameraPose) {
        float[] rotation = cameraPose.getRotationAsFloats();
        float[] translation = cameraPose.getTranslationAsFloats();
        Quaternion quaternion = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
        // Conjugating the Quaternion is need because Rajawali uses left handed convention for
        // quaternions.
        Quaternion conjugateQuaternion = quaternion.conjugate();
        Vector3 translationVector = new Vector3(translation[0], translation[1], translation[2]);

        getCurrentCamera().setRotation(conjugateQuaternion);
        getCurrentCamera().setPosition(translationVector);
    }

    public Vector3 getCameraPosition() {
        return getCurrentCamera().getPosition();
    }

    public double getCameraAngle() {
        return getCurrentCamera().getRotY();
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public int getTextureId() {
        return tangoCameraTexture == null ? -1 : tangoCameraTexture.getTextureId();
    }

    /**
     * We need to override this method to mark the camera for re-configuration (set proper
     * projection matrix) since it will be reset by Rajawali on surface changes.
     */
    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
        super.onRenderSurfaceSizeChanged(gl, width, height);
        sceneCameraConfigured = false;
    }

    public boolean isSceneCameraConfigured() {
        return sceneCameraConfigured;
    }

    /**
     * Sets the projection matrix for the scene camera to match the parameters of the color camera,
     * provided by the {@code TangoCameraIntrinsics}.
     */
    public void setProjectionMatrix(float[] matrixFloats) {
        getCurrentCamera().setProjectionMatrix(new Matrix4(matrixFloats));
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }

    public void switchCameraBackgroundVisibilyty() {
        if (backgroundQuad != null) {
            backgroundQuad.setVisible(!backgroundQuad.isVisible());
        }
    }

    public boolean isBackgroundVisible() {
        return backgroundQuad != null && backgroundQuad.isVisible();
    }


    public void switchFixturesVisibilyty() {
        isFixturesVisible = !isFixturesVisible;
        for (Object3D object : objects) {
            object.setVisible(isFixturesVisible);
        }
    }

    public FloatObjectFinder getObjectFinder() {
        return objectFinder;
    }

    public boolean isFixturesVisible() {
        return isFixturesVisible;
    }

    public void getObjectAt(float x, float y) {
        picker.getObjectAt(x, y);
    }

    public void removeObject(String name) {
        if (!TextUtils.isEmpty(name))
            for (Object3D object3D : objects) {
                if (name.equals(object3D.getName())) {
                    getCurrentScene().removeChild(object3D);
                    objects.remove(object3D);
                    break;
                }
            }
    }
}
