package com.mercdev.tangotest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.Object3D;
import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.util.OnObjectPickedListener;
import org.rajawali3d.view.SurfaceView;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity implements View.OnTouchListener, OnObjectPickedListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int INVALID_TEXTURE_ID = 0;

    // For all current Tango devices, color camera is in the camera id 0.
    private static final int COLOR_CAMERA_ID = 0;

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int CAMERA_PERMISSION_CODE = 0;

    private SurfaceView mSurfaceView;
    private FrameLayout mapContainer;
    private Minimap minimap;
    private View nothingTargeted;
    private View marker;

    private AugmentedRealityRenderer mRenderer;
    private TangoCameraIntrinsics mIntrinsics;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsConnected = false;
    private double mCameraPoseTimestamp = 0;
    private Object3D previousObject;

    private ArrayList<Fixture> fixtures;

    // Texture rendering related fields.
    // NOTE: Naming indicates which thread is in charge of updating this variable.
    private int mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private AtomicBoolean mIsFrameAvailableTangoThread = new AtomicBoolean(false);
    private double mRgbTimestampGlThread;

    private int mColorCameraToDisplayAndroidRotation = 0;

    private boolean isLocalized = false;
    private boolean isFloorPlaneDefined = false;

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
                        setAndroidOrientation();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            }, null);
        }

        initFixtures();

        mSurfaceView = new SurfaceView(this);
        setupRenderer();

        FrameLayout surfaceContainer = (FrameLayout) findViewById(R.id.surface_container);
        surfaceContainer.addView(mSurfaceView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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
                mRenderer.switchCameraBackgroundVisibilyty();
                v.setAlpha(mRenderer.isBackgroundVisible() ? 1f : .4f);

            }
        });

        ImageButton switch3dVisibilityButton = (ImageButton) findViewById(R.id.switch_3d_visibility);
        switch3dVisibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRenderer.switchFixturesVisibilyty();
                v.setAlpha(mRenderer.isFixturesVisible() ? 1f : .4f);
            }
        });

        nothingTargeted = findViewById(R.id.nothing_targeted);
        marker = findViewById(R.id.marker);

        mapContainer = (FrameLayout) findViewById(R.id.map_container);
        minimap = (Minimap) findViewById(R.id.minimap);
        minimap.setFixtures(fixtures, getResources());

        ImageButton switchMapVisibilityButton = (ImageButton) findViewById(R.id.switch_map_visibility);
        switchMapVisibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isMapVisible = mapContainer.getVisibility() == View.VISIBLE;
                mapContainer.setVisibility(isMapVisible ? View.GONE : View.VISIBLE);
                v.setAlpha(!isMapVisible ? 1f : .4f);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (ContextCompat.checkSelfPermission(this, Tango.PERMISSIONTYPE_ADF_LOAD_SAVE) == PackageManager.PERMISSION_GRANTED) {
            bindTangoAndResumeSurface();
        } else {
            startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                    Tango.TANGO_INTENT_ACTIVITYCODE);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mSurfaceView != null) {
            mSurfaceView.onPause();

            // Synchronize against disconnecting while the service is being used in the OpenGL thread or
            // in the UI thread.
            // NOTE: DO NOT lock against this same object in the Tango callback thread. Tango.disconnect
            // will block here until all Tango callback calls are finished. If you lock against this
            // object in a Tango callback thread it will cause a deadlock.
            synchronized (this) {
                if (mIsConnected) {
                    try {
                        if (isLocalized) {
                            String adfUuid = mTango.saveAreaDescription();
                            TangoAreaDescriptionMetaData metadata = mTango.loadAreaDescriptionMetaData(adfUuid);
                            mTango.saveAreaDescriptionMetadata(adfUuid, metadata);
                        }
                        isLocalized = false;
                        mIsConnected = false;
                        mTango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                        // We need to invalidate the connected texture ID so that we cause a
                        // re-connection in the OpenGL thread after resume.
                        mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
                        mTango.disconnect();
                        mTango = null;
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    }
                }
            }
        }
    }

    private void bindTangoAndResumeSurface() {
        if (hasCameraPermission()) {
            bindTangoService();

            mSurfaceView.onResume();

            setAndroidOrientation();

            // Set render mode to RENDERMODE_CONTINUOUSLY to force getting onDraw callbacks until
            // the Tango service is properly set-up and we start getting onFrameAvailable callbacks.
            mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            // Check and request camera permission at run time.
        } else {
            requestCameraPermission();
        }
    }

    /**
     * Initialize Tango Service as a normal Android Service.
     */
    private void bindTangoService() {
        // Since we call mTango.disconnect() in onStop, this will unbind Tango Service, so every
        // time when onStart gets called, we should create a new Tango object.
        mTango = new Tango(getApplicationContext(), new Runnable() {
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
                        mConfig = setupTangoConfig(mTango);
                        mTango.connect(mConfig);
                        startupTango();
                        mIsConnected = true;
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
                            final ArrayList<String> uuidAdf = mTango.listAreaDescriptions();
                            if (uuidAdf.size() == 0) {
                                adfRemoveButton.setVisibility(View.INVISIBLE);
                            } else {
                                adfRemoveButton.setVisibility(View.VISIBLE);
                                adfRemoveButton.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        if (uuidAdf.size() > 0) {
                                            for (String uuid : uuidAdf) {
                                                mTango.deleteAreaDescription(uuid);
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
     * Sets up the tango configuration object. Make sure mTango object is initialized before
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


        mTango.connectListener(framePairs, new Tango.OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                        && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {

                    if (!isLocalized) {
                        isLocalized = true;
                        if (isFloorPlaneDefined) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    FrameLayout frame = (FrameLayout) findViewById(R.id.process_localize_cover);
                                    frame.setVisibility(View.GONE);
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
                if (pointCloud != null && !isFloorPlaneDefined) {
                    float cameraHeight = (float) FloorPlaneDefinitionHelper.getCameraHeightFromFloor(pointCloud, mRgbTimestampGlThread, mColorCameraToDisplayAndroidRotation);
                    if (cameraHeight != 0.0) {
                        mRenderer.setCameraHeightFromFloor(cameraHeight);
                        isFloorPlaneDefined = true;
                        if (isLocalized) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    FrameLayout frame = (FrameLayout) findViewById(R.id.process_localize_cover);
                                    frame.setVisibility(View.GONE);
                                }
                            });
                        }
                    }
                }
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
                    mIsFrameAvailableTangoThread.set(true);
                    // Trigger an Rajawali render to update the scene with the new RGB data.
                    mSurfaceView.requestRender();
                }
            }
        });

        // Obtain the intrinsic parameters of the color camera.
        mIntrinsics = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
    }

    /**
     * Connects the view and renderer to the color camara and callbacks.
     */
    private void setupRenderer() {
        // Register a Rajawali Scene Frame Callback to update the scene camera pose whenever a new
        // RGB frame is rendered.
        // (@see https://github.com/Rajawali/Rajawali/wiki/Scene-Frame-Callbacks)

        mRenderer = new AugmentedRealityRenderer(this, this);
        mRenderer.setFixtures(fixtures);
        mRenderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                // NOTE: This is called from the OpenGL render thread, after all the renderer
                // onRender callbacks had a chance to run and before scene objects are rendered
                // into the scene.

                // Prevent concurrent access to {@code mIsFrameAvailableTangoThread} from the Tango
                // callback thread and service disconnection from an onPause event.
                try {
                    synchronized (MainActivity.this) {
                        // Don't execute any tango API actions if we're not connected to the service
                        if (!mIsConnected) {
                            return;
                        }

                        // Set-up scene camera projection to match RGB camera intrinsics.
                        if (!mRenderer.isSceneCameraConfigured()) {
                            mRenderer.setProjectionMatrix(
                                    projectionMatrixFromCameraIntrinsics(mIntrinsics,
                                            mColorCameraToDisplayAndroidRotation));
                        }
                        // Connect the camera texture to the OpenGL Texture if necessary
                        // NOTE: When the OpenGL context is recycled, Rajawali may re-generate the
                        // texture with a different ID.
                        if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                            mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                    mRenderer.getTextureId());
                            mConnectedTextureIdGlThread = mRenderer.getTextureId();
                            Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                        }

                        // If there is a new RGB camera frame available, update the texture with it
                        if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                            mRgbTimestampGlThread =
                                    mTango.updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                        }

                        // If a new RGB frame has been rendered, update the camera pose to match.
                        if (mRgbTimestampGlThread > mCameraPoseTimestamp) {
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
                                    mRgbTimestampGlThread,
                                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                    mColorCameraToDisplayAndroidRotation);
                            if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                                // Update the camera pose from the renderer
                                mRenderer.updateRenderCameraPose(lastFramePose);
                                mCameraPoseTimestamp = lastFramePose.timestamp;

                                minimap.setCameraPosition(lastFramePose);
                                minimap.postInvalidate();

                                mRenderer.getObjectAt(mSurfaceView.getWidth() / 2, mSurfaceView.getHeight() / 2);
                            } else {
                                // When the pose status is not valid, it indicates the tracking has
                                // been lost. In this case, we simply stop rendering.
                                //
                                // This is also the place to display UI to suggest the user walk
                                // to recover tracking.
                                Log.w(TAG, "Can't get device pose at time: " +
                                        mRgbTimestampGlThread);
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

        mSurfaceView.setSurfaceRenderer(mRenderer);
        mSurfaceView.setOnTouchListener(this);
    }

    private static int getColorCameraToDisplayAndroidRotation(int displayRotation,
                                                              int cameraRotation) {
        int cameraRotationNormalized = 0;
        switch (cameraRotation) {
            case 90:
                cameraRotationNormalized = 1;
                break;
            case 180:
                cameraRotationNormalized = 2;
                break;
            case 270:
                cameraRotationNormalized = 3;
                break;
            default:
                cameraRotationNormalized = 0;
                break;
        }
        int ret = displayRotation - cameraRotationNormalized;
        if (ret < 0) {
            ret += 4;
        }
        return ret;
    }

    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the Rajawali scene.
     *
     * @param intrinsics camera instrinsics for computing the project matrix.
     * @param rotation   the relative rotation between the camera intrinsics and display glContext.
     */
    private static float[] projectionMatrixFromCameraIntrinsics(TangoCameraIntrinsics intrinsics,
                                                                int rotation) {
        // Adjust camera intrinsics according to rotation
        float cx = (float) intrinsics.cx;
        float cy = (float) intrinsics.cy;
        float width = (float) intrinsics.width;
        float height = (float) intrinsics.height;
        float fx = (float) intrinsics.fx;
        float fy = (float) intrinsics.fy;

        switch (rotation) {
            case Surface.ROTATION_90:
                cx = (float) intrinsics.cy;
                cy = (float) intrinsics.width - (float) intrinsics.cx;
                width = (float) intrinsics.height;
                height = (float) intrinsics.width;
                fx = (float) intrinsics.fy;
                fy = (float) intrinsics.fx;
                break;
            case Surface.ROTATION_180:
                cx = (float) intrinsics.width - cx;
                cy = (float) intrinsics.height - cy;
                break;
            case Surface.ROTATION_270:
                cx = (float) intrinsics.height - (float) intrinsics.cy;
                cy = (float) intrinsics.cx;
                width = (float) intrinsics.height;
                height = (float) intrinsics.width;
                fx = (float) intrinsics.fy;
                fy = (float) intrinsics.fx;
                break;
            default:
                break;
        }

        // Uses frustumM to create a projection matrix taking into account calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        float near = 0.1f;
        float far = 100;

        float xScale = near / fx;
        float yScale = near / fy;
        float xOffset = (cx - (width / 2.0f)) * xScale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        float yOffset = -(cy - (height / 2.0f)) * yScale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                xScale * (float) -width / 2.0f - xOffset,
                xScale * (float) width / 2.0f - xOffset,
                yScale * (float) -height / 2.0f - yOffset,
                yScale * (float) height / 2.0f - yOffset,
                near, far);
        return m;
    }

    /**
     * Set the color camera background texture rotation and save the camera to display rotation.
     */
    private void setAndroidOrientation() {
        Display display = getWindowManager().getDefaultDisplay();
        Camera.CameraInfo colorCameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(COLOR_CAMERA_ID, colorCameraInfo);

        mColorCameraToDisplayAndroidRotation =
                getColorCameraToDisplayAndroidRotation(display.getRotation(),
                        colorCameraInfo.orientation);
        // Run this in OpenGL thread.
        if (mSurfaceView != null) {
            mSurfaceView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    mRenderer.updateColorCameraTextureUvGlThread(mColorCameraToDisplayAndroidRotation);
                }
            });
        }
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
            bindTangoAndResumeSurface();
        } else {
            Toast.makeText(this, "Java Augmented Reality Example requires camera permission",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void initFixtures() {
        fixtures = new ArrayList<>();

        int fixtureColor = Color.WHITE;
        fixtures.add(new Fixture(new Point(100, -50), 400, 40, 200, fixtureColor));
        fixtures.add(new Fixture(new Point(-100, -50), 200, 40, 200, fixtureColor));
        fixtures.add(new Fixture(new Point(-100, 250), 400, 400, 40, fixtureColor));
        fixtures.add(new Fixture(new Point(300, 1250), 400, 400, 40, fixtureColor));
        fixtures.add(new Fixture(new Point(-200, -670), 400, 600, 25, fixtureColor));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            if (resultCode == RESULT_CANCELED) {
                finish();
            } else {
                bindTangoAndResumeSurface();
            }
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mRenderer.getObjectAt(event.getX(), event.getY());
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onObjectPicked(@NonNull Object3D object) {
        if (object != null) {
            changeMarkerColor(Color.GREEN);
            previousObject = object;
        } else {
            onNoObjectPicked();
        }
    }

    @Override
    public void onNoObjectPicked() {
        changeMarkerColor(Color.RED);

        previousObject = null;
    }

    private void changeMarkerColor(final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                marker.setBackgroundColor(color);
            }
        });
    }
}
