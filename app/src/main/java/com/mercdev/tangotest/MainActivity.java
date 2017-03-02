package com.mercdev.tangotest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.Object3D;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.util.OnObjectPickedListener;
import org.rajawali3d.view.SurfaceView;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity implements OnObjectPickedListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int INVALID_TEXTURE_ID = 0;

    // For all current Tango devices, color camera is in the camera id 0.
    private static final int COLOR_CAMERA_ID = 0;

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int CAMERA_PERMISSION_CODE = 0;
    private static final PointF FLOOR_DEFINITION_POINT = new PointF(0.5f, 0.75f);

    private SurfaceView mSurfaceView;
    private FrameLayout mapContainer;
    private FrameLayout coverFrame;
    private TextView coverFrameText;
    private Minimap minimap;
    private View nothingTargeted, targetedFixture, deleteFixture, modifyFixture;
    private View marker;

    private AugmentedRealityRenderer renderer;
    private Tango tango;
    private TangoConfig config;
    private boolean isConnected = false;
    private double cameraPoseTimestamp = 0;
    private Object3D previousObject;
    private TangoPointCloudManager tangoPointCloudManager;

    // Texture rendering related fields.
    // NOTE: Naming indicates which thread is in charge of updating this variable.
    private int connectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private AtomicBoolean isFrameAvailableTangoThread = new AtomicBoolean(false);
    private double rgbTimestampGlThread;

    private boolean isLocalized = false;
    private boolean isCreateSurfaceAndScene = false;

    private int displayRotation;

    private double startModificationCameraX, startModificationCameraZ, startModificationFixtureX, startModificationFixtureZ, startModificationRotationAngle, startModificationCameraAngle;
    private Fixture startModificationFixture;

    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the Rajawali scene.
     */
    private static float[] projectionMatrixFromCameraIntrinsics(TangoCameraIntrinsics intrinsics) {
        // Uses frustumM to create a projection matrix taking into account calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        float near = 0.1f;
        float far = 100;

        double cx = intrinsics.cx;
        double cy = intrinsics.cy;
        double width = intrinsics.width;
        double height = intrinsics.height;
        double fx = intrinsics.fx;
        double fy = intrinsics.fy;

        double xscale = near / fx;
        double yscale = near / fy;

        double xoffset = (cx - (width / 2.0)) * xscale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        double yoffset = -(cy - (height / 2.0)) * yscale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                (float) (xscale * -width / 2.0 - xoffset),
                (float) (xscale * width / 2.0 - xoffset),
                (float) (yscale * -height / 2.0 - yoffset),
                (float) (yscale * height / 2.0 - yoffset), near, far);
        return m;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.a_main);

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setDisplayRotation();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            }, null);
        }

        initFixtures();

        tangoPointCloudManager = new TangoPointCloudManager();

        coverFrame = (FrameLayout) findViewById(R.id.cover_frame);
        coverFrameText = (TextView) coverFrame.findViewById(R.id.cover_frame_text);

        ImageButton homeButton = (ImageButton) findViewById(R.id.home);
        homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO open home screen
            }
        });

        ImageButton switchCameraVisibilityButton = (ImageButton) findViewById(R.id.switch_camera_visibility);
        switchCameraVisibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renderer.switchCameraBackgroundVisibilyty();
                v.setAlpha(renderer.isBackgroundVisible() ? 1f : .4f);

            }
        });

        ImageButton switch3dVisibilityButton = (ImageButton) findViewById(R.id.switch_3d_visibility);
        switch3dVisibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renderer.switchFixturesVisibilyty();
                v.setAlpha(renderer.isFixturesVisible() ? 1f : .4f);
            }
        });

        nothingTargeted = findViewById(R.id.nothing_targeted_container);
        targetedFixture = findViewById(R.id.targeted_fixture_container);
        deleteFixture = findViewById(R.id.delete_fixture_container);
        modifyFixture = findViewById(R.id.modify_fixture_container);
        marker = findViewById(R.id.marker);

        mapContainer = (FrameLayout) findViewById(R.id.map_container);
        minimap = (Minimap) findViewById(R.id.minimap);
        minimap.processFixtures();

        ImageButton switchMapVisibilityButton = (ImageButton) findViewById(R.id.switch_map_visibility);
        switchMapVisibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isMapVisible = mapContainer.getVisibility() == View.VISIBLE;
                mapContainer.setVisibility(isMapVisible ? View.GONE : View.VISIBLE);
                v.setAlpha(!isMapVisible ? 1f : .4f);
            }
        });

        if (ContextCompat.checkSelfPermission(this, Tango.PERMISSIONTYPE_ADF_LOAD_SAVE) == PackageManager.PERMISSION_GRANTED) {
            checkPermissionsAndBindTango();
        } else {
            startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                    Tango.TANGO_INTENT_ACTIVITYCODE);
        }
    }

    @Override
    public void onDestroy() {
        super.onStop();
        if (mSurfaceView != null) {
            mSurfaceView.onPause();

            // Synchronize against disconnecting while the service is being used in the OpenGL thread or
            // in the UI thread.
            // NOTE: DO NOT lock against this same object in the Tango callback thread. Tango.disconnect
            // will block here until all Tango callback calls are finished. If you lock against this
            // object in a Tango callback thread it will cause a deadlock.
            synchronized (this) {
                if (isConnected) {
                    try {
                        if (isLocalized && tango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT).getBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE)) {
                            String adfUuid = tango.saveAreaDescription();
                            TangoAreaDescriptionMetaData metadata = tango.loadAreaDescriptionMetaData(adfUuid);
                            tango.saveAreaDescriptionMetadata(adfUuid, metadata);
                        }
                        isLocalized = false;
                        isConnected = false;
                        tango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                        // We need to invalidate the connected texture ID so that we cause a
                        // re-connection in the OpenGL thread after resume.
                        connectedTextureIdGlThread = INVALID_TEXTURE_ID;
                        tango.disconnect();
                        tango = null;
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    }
                }
            }
        }
    }

    private void checkPermissionsAndBindTango() {
        if (hasCameraPermission()) {
            showCoverFrame(R.string.process_localization);
            bindTangoService();
        } else {
            requestCameraPermission();
        }
    }

    /**
     * Initialize Tango Service as a normal Android Service.
     */
    private void bindTangoService() {
        // Since we call tango.disconnect() in onStop, this will unbind Tango Service, so every
        // time when onStart gets called, we should create a new Tango object.
        tango = new Tango(getApplicationContext(), new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready, this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there
            // is no UI thread changes involved.
            @Override
            public void run() {
                // Synchronize against disconnecting while the service is being used in the
                // OpenGL thread or in the UI thread.
                synchronized (MainActivity.this) {
                    try {
                        TangoSupport.initialize();
                        config = setupTangoConfig(tango);
                        tango.connect(config);
                        startupTango();
                        isConnected = true;
                        setDisplayRotation();
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (MainActivity.this) {
                            final ImageButton adfRemoveButton = (ImageButton) findViewById(R.id.adf_remove_button);
                            final ArrayList<String> uuidAdf = tango.listAreaDescriptions();
                            if (uuidAdf.size() == 0) {
                                adfRemoveButton.setVisibility(View.INVISIBLE);
                            } else {
                                adfRemoveButton.setVisibility(View.VISIBLE);
                                adfRemoveButton.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        if (uuidAdf.size() > 0) {
                                            for (String uuid : uuidAdf) {
                                                tango.deleteAreaDescription(uuid);
                                            }
                                            adfRemoveButton.setVisibility(View.INVISIBLE);
                                        }
                                    }
                                });
                            }
                        }
                    }
                });
            }
        });
    }

    /**
     * Sets up the tango configuration object. Make sure tango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Use default configuration for Tango Service, plus color camera, low latency
        // IMU integration and drift correction.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        // NOTE: Low latency integration is necessary to achieve a precise alignment of
        // virtual objects with the RBG image and produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        // Drift correction allows motion tracking to recover after it loses tracking.
        // The drift corrected pose is is available through the frame pair with
        // base frame AREA_DESCRIPTION and target frame DEVICE.
        //config.putBoolean(TangoConfig.KEY_BOOLEAN_DRIFT_CORRECTION, true);

        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);

        ArrayList<String> uuidAdf = tango.listAreaDescriptions();
        if (uuidAdf.size() > 0) {
            config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION,
                    uuidAdf.get(uuidAdf.size() - 1));
        } else {
            config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);
        }

        return config;
    }

    /**
     * Set up the callback listeners for the Tango service and obtain other parameters required
     * after Tango connection.
     * Listen to updates from the RGB camera.
     */
    private void startupTango() {
        // No need to add any coordinate frame pairs since we aren't using pose data from callbacks.
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));


        tango.connectListener(framePairs, new Tango.TangoUpdateCallback() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                        && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {

                    if (pose.statusCode == TangoPoseData.POSE_VALID && !isLocalized) {
                        isLocalized = true;

                        if (!isCreateSurfaceAndScene) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showCoverFrame(R.string.process_floor_definition);
                                }
                            });

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    double cloudDataTimestamp = 0.0;
                                    while (true) {
                                        TangoPointCloudData cloudData = tangoPointCloudManager.getLatestPointCloud();
                                        if (cloudDataTimestamp < cloudData.timestamp) {
                                            final Matrix4 transformFloorMatrix4 = FloorPlaneDefinitionHelper.getTransformFloorMatrix4(FLOOR_DEFINITION_POINT.x, FLOOR_DEFINITION_POINT.y, cloudData,
                                                    rgbTimestampGlThread, displayRotation);

                                            // TODO need add check defined plane is floor
                                            if (transformFloorMatrix4 != null) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {

                                                        mSurfaceView = new SurfaceView(MainActivity.this);
                                                        setupRenderer(transformFloorMatrix4);

                                                        FrameLayout surfaceContainer = (FrameLayout) findViewById(R.id.surface_container);
                                                        surfaceContainer.addView(mSurfaceView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                                                        mSurfaceView.onResume();
                                                        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

                                                        hideCoverFrame();

                                                        isCreateSurfaceAndScene = true;
                                                    }
                                                });

                                                break;
                                            }

                                            cloudDataTimestamp = cloudData.timestamp;
                                        }
                                    }
                                }
                            }).start();
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (mSurfaceView != null) {
                                        mSurfaceView.onResume();
                                        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

                                        hideCoverFrame();
                                    } else {
                                        showCoverFrame(R.string.surface_is_not_available);
                                    }
                                }
                            });
                        }
                    }
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                tangoPointCloudManager.updatePointCloud(pointCloud);
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                // We are not using onTangoEvent for this app.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // Check if the frame available is for the camera we want and update its frame
                // on the view.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    // Now that we are receiving onFrameAvailable callbacks, we can switch
                    // to RENDERMODE_WHEN_DIRTY to drive the render loop from this callback.
                    // This will result on a frame rate of  approximately 30FPS, in synchrony with
                    // the RGB camera driver.
                    // If you need to render at a higher rate (i.e.: if you want to render complex
                    // animations smoothly) you  can use RENDERMODE_CONTINUOUSLY throughout the
                    // application lifecycle.
                    if (mSurfaceView.getRenderMode() != GLSurfaceView.RENDERMODE_WHEN_DIRTY) {
                        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                    }

                    // Mark a camera frame is available for rendering in the OpenGL thread.
                    isFrameAvailableTangoThread.set(true);
                    // Trigger an Rajawali render to update the scene with the new RGB data.
                    mSurfaceView.requestRender();
                }
            }
        });
    }

    /**
     * Connects the view and renderer to the color camara and callbacks.
     */
    private void setupRenderer(Matrix4 transformFloorMatrix4) {
        // Register a Rajawali Scene Frame Callback to update the scene camera pose whenever a new
        // RGB frame is rendered.
        // (@see https://github.com/Rajawali/Rajawali/wiki/Scene-Frame-Callbacks)

        renderer = new AugmentedRealityRenderer(this, this, transformFloorMatrix4);
        renderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                // NOTE: This is called from the OpenGL render thread, after all the renderer
                // onRender callbacks had a chance to run and before scene objects are rendered
                // into the scene.

                // Prevent concurrent access to {@code isFrameAvailableTangoThread} from the Tango
                // callback thread and service disconnection from an onPause event.
                try {
                    synchronized (MainActivity.this) {
                        // Don't execute any tango API actions if we're not connected to the service
                        if (!isConnected) {
                            return;
                        }

                        // Set-up scene camera projection to match RGB camera intrinsics.
                        if (!renderer.isSceneCameraConfigured()) {
                            TangoCameraIntrinsics intrinsics =
                                    TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
                                            TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                            displayRotation);
                            renderer.setProjectionMatrix(
                                    projectionMatrixFromCameraIntrinsics(intrinsics));
                        }

                        // Connect the camera texture to the OpenGL Texture if necessary
                        // NOTE: When the OpenGL context is recycled, Rajawali may re-generate the
                        // texture with a different ID.
                        if (connectedTextureIdGlThread != renderer.getTextureId()) {
                            tango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                    renderer.getTextureId());
                            connectedTextureIdGlThread = renderer.getTextureId();
                            Log.d(TAG, "connected to texture id: " + renderer.getTextureId());
                        }

                        // If there is a new RGB camera frame available, update the texture with it
                        if (isFrameAvailableTangoThread.compareAndSet(true, false)) {
                            rgbTimestampGlThread =
                                    tango.updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                        }

                        // If a new RGB frame has been rendered, update the camera pose to match.
                        if (rgbTimestampGlThread > cameraPoseTimestamp) {
                            // Calculate the camera color pose at the camera frame update time in
                            // OpenGL engine.
                            //
                            // When drift correction mode is enabled in config file, we must query
                            // the device with respect to Area Description pose in order to use the
                            // drift corrected pose.
                            //
                            // Note that if you don't want to use the drift corrected pose, the
                            // normal device with respect to start of service pose is available.
                            TangoPoseData lastFramePose = TangoSupport.getPoseAtTime(
                                    rgbTimestampGlThread,
                                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                    displayRotation);
                            if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                                // Update the camera pose from the renderer
                                renderer.updateRenderCameraPose(lastFramePose);
                                cameraPoseTimestamp = lastFramePose.timestamp;
                                if (modifyFixture.getVisibility() == View.VISIBLE && previousObject != null) {
                                    double dx = renderer.getCameraPosition().x - startModificationCameraX;
                                    double dz = renderer.getCameraPosition().z - startModificationCameraZ;
                                    double x = startModificationFixtureX + dx;
                                    double z = startModificationFixtureZ + dz;
                                    previousObject.setPosition(x, previousObject.getY(), z);
                                    FixturesRepository.getInstance().getFixture(startModificationFixture.getName())
                                            .setPosition(new Point((int) (startModificationFixture.getPosition().x + dx * 100f),
                                                    (int) (startModificationFixture.getPosition().y + dz * 100f)));

                                    float[] rotation = lastFramePose.getRotationAsFloats();
                                    Quaternion q = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
                                    double rotationAngle = Math.toDegrees(-q.getRotationY()) - startModificationCameraAngle;
                                    previousObject.setRotY(startModificationRotationAngle + rotationAngle);
                                    FixturesRepository.getInstance().getFixture(startModificationFixture.getName())
                                            .setRotationAngle(startModificationFixture.getRotationAngle() + rotationAngle);

                                    minimap.processFixtures();
                                }

                                minimap.setCameraPosition(lastFramePose);
                                minimap.postInvalidate();

                                renderer.getObjectAt(mSurfaceView.getWidth() / 2, mSurfaceView.getHeight() / 2);
                            } else {
                                // When the pose status is not valid, it indicates the tracking has
                                // been lost. In this case, we simply stop rendering.
                                //
                                // This is also the place to display UI to suggest the user walk
                                // to recover tracking.
                                Log.w(TAG, "Can't get device pose at time: " +
                                        rgbTimestampGlThread);
                            }
                        }
                    }

                    // Avoid crashing the application due to unhandled exceptions
                } catch (TangoErrorException e) {
                    Log.e(TAG, "Tango API call error within the OpenGL render thread", e);
                } catch (Throwable t) {
                    Log.e(TAG, "Exception on the OpenGL thread", t);
                }
            }

            @Override
            public void onPreDraw(long sceneTime, double deltaTime) {

            }

            @Override
            public void onPostFrame(long sceneTime, double deltaTime) {

            }

            @Override
            public boolean callPreFrame() {
                return true;
            }
        });

        renderer.updateColorCameraTextureUvGlThread(displayRotation);
        mSurfaceView.setSurfaceRenderer(renderer);
    }

    /**
     * Set the color camera background texture rotation and save the camera to display rotation.
     */
    private void setDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        displayRotation = display.getRotation();
    }

    /**
     * Check we have the necessary permissions for this app.
     */
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request the necessary permissions for this app.
     */
    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION)) {
            showRequestPermissionRationale();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{CAMERA_PERMISSION},
                    CAMERA_PERMISSION_CODE);
        }
    }

    /**
     * If the user has declined the permission before, we have to explain him the app needs this
     * permission.
     */
    private void showRequestPermissionRationale() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Java Augmented Reality Example requires camera permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
                    }
                })
                .create();
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (hasCameraPermission()) {
            checkPermissionsAndBindTango();
        } else {
            Toast.makeText(this, "Java Augmented Reality Example requires camera permission",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void initFixtures() {
        ArrayList<Fixture> fixtures = new ArrayList<>();

        int fixtureColor = getResources().getColor(R.color.object_color);
        fixtures.add(new Fixture("Fixture1", new Point(345, -620), 280, 25, 685, 0f, fixtureColor));
        fixtures.add(new Fixture("Fixture2", new Point(-860, -620), 280, 25, 685, 0f, fixtureColor));
        fixtures.add(new Fixture("Fixture3", new Point(-835, 40), 280, 1180, 25, 0f, fixtureColor));
        fixtures.add(new Fixture("Fixture4", new Point(-835, -620), 280, 1180, 25, 0f, fixtureColor));

        FixturesRepository.getInstance().setFixtures(fixtures);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            if (resultCode == RESULT_CANCELED) {
                finish();
            } else {
                checkPermissionsAndBindTango();
            }
        }
    }

    @Override
    public void onObjectPicked(@NonNull Object3D object) {
        if (modifyFixture.getVisibility() != View.VISIBLE && deleteFixture.getVisibility() != View.VISIBLE) {
            if (object != null) {
                Fixture fixture = FixturesRepository.getInstance().getFixture(object.getName());
                if (fixture != null && (previousObject == null || !previousObject.getName().equals(object.getName()))) {
                    previousObject = object;
                    showFixtureInformation(fixture);
                    changeMarkerColor(Color.GREEN);
                }
                setFixtureDistance();
            } else {
                onNoObjectPicked();
            }
        }
    }

    @Override
    public void onNoObjectPicked() {
        if (modifyFixture.getVisibility() != View.VISIBLE
                && deleteFixture.getVisibility() != View.VISIBLE
                && previousObject != null) {
            if (modifyFixture.getVisibility() == View.VISIBLE) {
                cancelFixtureModifications();
            }

            showNothingTargeted();
            changeMarkerColor(Color.RED);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    previousObject = null;
                }
            });
        }
    }

    private void changeMarkerColor(final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                marker.setBackgroundColor(color);
            }
        });
    }

    private void showCoverFrame(int coverText) {
        coverFrameText.setText(coverText);
        coverFrame.setVisibility(View.VISIBLE);
    }

    private void hideCoverFrame() {
        coverFrameText.setText("");
        coverFrame.setVisibility(View.GONE);
    }

    private void showNothingTargeted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                nothingTargeted.setVisibility(View.VISIBLE);
                targetedFixture.setVisibility(View.GONE);
                deleteFixture.setVisibility(View.GONE);
                modifyFixture.setVisibility(View.GONE);
            }
        });
    }

    private void showFixtureInformation(final Fixture fixture) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) targetedFixture.findViewById(R.id.fixture_name)).setText(fixture.getName());
                ((TextView) targetedFixture.findViewById(R.id.fixture_height))
                        .setText(String.format(getResources().getString(R.string.fixture_dialog_targeted_height), fixture.getHeight() / 100f));
                ((TextView) targetedFixture.findViewById(R.id.fixture_width))
                        .setText(String.format(getResources().getString(R.string.fixture_dialog_targeted_width), fixture.getWidth() / 100f));
                ((TextView) targetedFixture.findViewById(R.id.fixture_depth))
                        .setText(String.format(getResources().getString(R.string.fixture_dialog_targeted_depth), fixture.getDepth() / 100f));

                targetedFixture.findViewById(R.id.fixture_modify).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showModifyFixture(fixture);
                    }
                });
                targetedFixture.findViewById(R.id.fixture_delete).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showDeleteFixture(fixture);
                    }
                });

                nothingTargeted.setVisibility(View.GONE);
                targetedFixture.setVisibility(View.VISIBLE);
                deleteFixture.setVisibility(View.GONE);
                modifyFixture.setVisibility(View.GONE);
            }
        });
    }

    private void showDeleteFixture(final Fixture fixture) {
        if (fixture != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) deleteFixture.findViewById(R.id.delete_fixture_name))
                            .setText(String.format(getResources().getString(R.string.fixture_dialog_delete_fixture), fixture.getName()));

                    deleteFixture.findViewById(R.id.delete_fixture_no).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showFixtureInformation(fixture);
                        }
                    });
                    deleteFixture.findViewById(R.id.delete_fixture_yes).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (previousObject != null) {
                                FixturesRepository.getInstance().removeFixture(previousObject.getName());
                                renderer.removeObject(previousObject.getName());
                                minimap.postInvalidate();
                            }
                        }
                    });

                    nothingTargeted.setVisibility(View.GONE);
                    targetedFixture.setVisibility(View.GONE);
                    deleteFixture.setVisibility(View.VISIBLE);
                    modifyFixture.setVisibility(View.GONE);
                }
            });
        }
    }

    private void showModifyFixture(final Fixture fixture) {
        if (fixture != null) {
            startModificationFixture = (Fixture) fixture.clone();
            startModificationCameraX = renderer.getCameraPosition().x;
            startModificationCameraZ = renderer.getCameraPosition().z;
            startModificationFixtureX = previousObject.getX();
            startModificationFixtureZ = previousObject.getZ();
            startModificationRotationAngle = Math.toDegrees(previousObject.getRotY());
            startModificationCameraAngle = Math.toDegrees(renderer.getCameraAngle());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) modifyFixture.findViewById(R.id.modify_fixture_name))
                            .setText(String.format(getResources().getString(R.string.fixture_dialog_modify_fixture), fixture.getName()));

                    modifyFixture.findViewById(R.id.modify_fixture_cancel).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            cancelFixtureModifications();
                            showFixtureInformation(fixture);
                        }
                    });
                    modifyFixture.findViewById(R.id.modify_fixture_save).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            //TODO apply modifications
                            showFixtureInformation(fixture);
                        }
                    });

                    nothingTargeted.setVisibility(View.GONE);
                    targetedFixture.setVisibility(View.GONE);
                    deleteFixture.setVisibility(View.GONE);
                    modifyFixture.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void setFixtureDistance() {
        if (previousObject != null) {
            Vector3 cameraPosition = renderer.getCameraPosition();
            final double distance = Math.sqrt(Math.pow(previousObject.getX() - cameraPosition.x, 2) + Math.pow(previousObject.getY() - cameraPosition.y, 2));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) targetedFixture.findViewById(R.id.fixture_distance))
                            .setText(String.format(getResources().getString(R.string.fixture_dialog_targeted_distance), distance));
                }
            });
        }
    }

    private void cancelFixtureModifications() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                FixturesRepository.getInstance().updateFixture(startModificationFixture);
                previousObject.setPosition(startModificationFixtureX, previousObject.getY(), startModificationFixtureZ);
                previousObject.setRotY(startModificationRotationAngle);
                minimap.processFixtures();
                minimap.postInvalidate();
            }
        });
    }
}
