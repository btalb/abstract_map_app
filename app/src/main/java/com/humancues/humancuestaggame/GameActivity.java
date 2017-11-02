package com.humancues.humancuestaggame;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.util.Arrays;
import java.util.List;

public class GameActivity extends AppCompatActivity {
    // Camera information
    private static final int CAMERA_PERMISSIONS = 0;
    private final Size cameraSize = new Size(1280, 720);
    private CameraManager cameraManager = null;
    private String cameraID = null;
    private CameraCharacteristics cameraCharacteristics = null;
    private CameraDevice cameraDevice = null;
    private CaptureRequest cameraCaptureRequest = null;
    private CameraCaptureSession cameraCaptureSession = null;

    private final int imageFormat = ImageFormat.JPEG;
    private ImageReader imageReader = null;

    // Background thread for processing
    private HandlerThread cameraThread = null;
    private Handler cameraHandler = null;

    // Tag detection information for the app
    public boolean detecting = false;
    enum TAG_TYPE { NONE, EMPTY, INFO, WRONG, GOAL };
    private TAG_TYPE currentTagType = TAG_TYPE.NONE;
    private String currentTagText = null;

    // Experimental trial configurations
    public String[] listExperiments = {"Experiment 1", "Experiment 2", "TODO"};
    public String[] listGoals = {"Alice's office",
            "Bob's office"};
    private String currentGoal = "Alice's office";

    static {
        System.loadLibrary("native-lib");
    }

    public int HACK = 0;

    /**
     * Lifecycle implementations
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // Configure the interface (interactivity and content)
        refreshExperimentSpinner();
        configureInteractivity();

        // Start everything to help with image acquisition and processing
        startCameraThread();
        initialiseCameraInfo();
        configureCameraView();
        initAprilTags();
    }

    @Override
    protected void onDestroy() {
        cleanupAprilTags();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case CAMERA_PERMISSIONS: {
                if (grantResults.length > 0 && grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED) {
                    initialiseCameraInfo();
                } else {
                    Toast.makeText(this, "The app failed to acquire camera " +
                                    "permissions",
                            Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    /**
     * Configure all of the button interactivity
     */
    private void configureInteractivity() {
        Button b = findViewById(R.id.start_button);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Pull all state from the intro screen UI
                // TODO

                // Update the UI
                ViewSwitcher vs = findViewById(R.id.switcher);
                vs.showNext();

                ((TextView) findViewById(R.id.task_info)).setText("find " + currentGoal);
                findViewById(R.id.background_mask).setVisibility(View.GONE);
                updateTagDisplay();
            }
        });

        ImageButton ib = findViewById(R.id.tag_dismiss);
        ib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean done = currentTagType == TAG_TYPE.GOAL;
                currentTagType = TAG_TYPE.NONE;
                currentTagText = null;

                if (done) {
                    findViewById(R.id.background_mask).setVisibility(View.VISIBLE);
                    ((ViewSwitcher) findViewById(R.id.switcher)).showPrevious();
                } else {
                    updateTagDisplay();
                }
            }
        });

        // TODO remove these hacks
        SurfaceView sv = findViewById(R.id.camera_view);
        sv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (HACK % 3 == 0) {
                    currentTagType = TAG_TYPE.INFO;
                    currentTagText = "Alice's office is down the corridor, past Bob's office";
                } else if (HACK % 3 == 1) {
                    currentTagType = TAG_TYPE.WRONG;
                    currentTagText = "BOB's office";
                } else {
                    currentTagType = TAG_TYPE.GOAL;
                    currentTagText = "Alice's office";
                }
                HACK++;
                updateTagDisplay();
                return true;
            }
        });
    }

    private void updateTagDisplay() {
        // Apply correct colours
        int colourId;
        switch (currentTagType) {
            case EMPTY:
                colourId = android.R.attr.textColor;
                break;
            case INFO:
                colourId = R.color.feedbackOrange;
                break;
            case WRONG:
                colourId = R.color.feedbackRed;
                break;
            case GOAL:
                colourId = R.color.feedbackGreen;
                break;
            default:
                colourId = R.color.colorPrimary;
        }
        findViewById(R.id.task_panel).setBackgroundResource(colourId);
        findViewById(R.id.tag_panel).setBackgroundResource(colourId);

        // Hide and exit if there is no tag
        if (currentTagType == TAG_TYPE.NONE) {
            findViewById(R.id.tag_panel).setVisibility(View.GONE);
            detecting = true;
            return;
        }

        // Update the icon and text for the tag info
        findViewById(R.id.tag_panel).setVisibility(View.VISIBLE);
        ((ImageView) findViewById(R.id.tag_dismiss)).setImageResource((currentTagType == TAG_TYPE
                .GOAL) ? R.drawable.done_24dp : R.drawable.close_24dp);
        ((Button) findViewById(R.id.tag_info_button)).setText(currentTagText);

        // We have some tag info being displayed, turn detecting off
        detecting = false;
    }

    /**
     * Camera view and processing control
     */
    private void startCameraThread() {
        cameraThread = new HandlerThread("CameraBackground");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void configureCameraView() {
        SurfaceView cv = findViewById(R.id.camera_view);
        cv.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                // Set size and format to match what we will be getting out
                // of the camera
                surfaceHolder.setFixedSize(cameraSize.getWidth(), cameraSize
                        .getHeight());

                // Create the image reader
                imageReader = ImageReader.newInstance(cameraSize.getWidth(),
                        cameraSize.getHeight(), imageFormat, 2);
                imageReader.setOnImageAvailableListener(new
                        ImageReaderCallback(), cameraHandler);

                // This is run every time the app is resumed, so attach to
                // the camera here
                attachToCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                // Detach from the camera
                detachFromCamera();
            }
        });
    }

    /**
     * Camera control functions
     */
    private void initialiseCameraInfo() {
        // Ensure that camera access permissions exist
        int permission = ContextCompat.checkSelfPermission(this, Manifest
                .permission.CAMERA);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest
                    .permission.CAMERA}, CAMERA_PERMISSIONS);
            return;
        }

        // Attempt to get the ID of the back facing camera
        try {
            cameraManager = (CameraManager) this.getSystemService(Context
                    .CAMERA_SERVICE);
            String[] cameras = cameraManager.getCameraIdList();
            for (final String s : cameras) {
                CameraCharacteristics cc = cameraManager.getCameraCharacteristics(s);
                if (cc.get(CameraCharacteristics.LENS_FACING) ==
                        CameraMetadata.LENS_FACING_BACK) {
                    cameraID = s;
                    cameraCharacteristics = cc;
                }
            }
            if (cameraID == null) throw new Exception();
        } catch (Exception e) {
            Toast.makeText(this, "Back facing camera access failed!", Toast
                    .LENGTH_LONG).show();
        }
    }

    private void attachToCamera() {
        try {
            // Bail if we don't have the valid info (i.e. initialise camera info
            // has not been called), or permissions fail
            if (cameraManager == null || cameraID == null || cameraCharacteristics
                    == null || ContextCompat.checkSelfPermission(this, Manifest
                    .permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
            }
            cameraManager.openCamera(cameraID, new CameraDeviceCallback(),
                    cameraHandler);
        } catch (CameraAccessException e) {
            Toast.makeText(this, "The app failed to access the camera",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void detachFromCamera() {
        if (cameraCaptureSession != null) {
            try {
                cameraCaptureSession.stopRepeating();
                cameraCaptureSession.abortCaptures();
            } catch (Exception e) {}
            cameraCaptureSession.close();
        }
        if (cameraDevice != null) cameraDevice.close();
        cameraCaptureSession = null;
        cameraDevice = null;
    }

    class CameraDeviceCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            try {
                GameActivity.this.cameraDevice = cameraDevice;

                // Get a list of the Surfaces we want to push the camera data to
                List<Surface> ls = Arrays.asList(((SurfaceView) findViewById
                        (R.id.camera_view)).getHolder().getSurface(),
                        imageReader.getSurface());

                // Build a capture request, with the target surfaces included
                CaptureRequest.Builder b = cameraDevice.createCaptureRequest
                        (CameraDevice.TEMPLATE_PREVIEW);
                for (final Surface s : ls) b.addTarget(s);
                cameraCaptureRequest = b.build();

                // Generate the capture session
                cameraDevice.createCaptureSession(ls, new
                        CameraSessionCallback(), cameraHandler);
            } catch (CameraAccessException e) {
                Log.e("HuC", "Device failed to access camera...");
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            detachFromCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {

        }
    }

    class CameraSessionCallback extends CameraCaptureSession.StateCallback {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            try {
                GameActivity.this.cameraCaptureSession = cameraCaptureSession;

                // Start capturing
                cameraCaptureSession.setRepeatingRequest(cameraCaptureRequest,
                        null, cameraHandler);
            } catch (CameraAccessException e) {
                Log.e("HuC", "Session failed to access camera...");
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

        }
    }

    class ImageReaderCallback implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            long tS = System.nanoTime();
            // Always get an image, ensuring it is closed even if we
            // terminate early
            Image i = imageReader.acquireLatestImage();
            if (i == null) return;
            if (!detecting) {
                i.close();
                return;
            }
            long tR = System.nanoTime();

            // Step into native code, passing the JPEG byte buffer down to be decoded and checked
            // for April Tags
            Image.Plane p = i.getPlanes()[0];
            byte[] bytes = new byte[p.getBuffer().remaining()];
            p.getBuffer().get(bytes);
            long tB = System.nanoTime();
            Detection d = searchForAprilTags(bytes, bytes.length);
            if (d != null) {
                Log.e("HuC", "Tag " + d.id + " detected @ " + Arrays
                        .toString(d.coords()));
            }
            long tD = System.nanoTime();

            // Clean up things when we are done
            i.close();
            long tC = System.nanoTime();
            Log.w("HuC", "Method took: " + ms(tC-tS) + "(tR:" + ms(tR-tS)
                    + ",tB:" + ms(tB-tR) + ",tD:" + ms(tD-tB) + ",tC:" + ms
                    (tC-tD) + ")");
        }

        private double ms(long nano) { return nano/1000000; }
    }

    /**
     * Control the spinner content
     */
    private void refreshExperimentSpinner() {
        // Configure the spinner
        Spinner s = findViewById(R.id.experiment_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, listExperiments);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);

        // Always update the goal spinner
        refreshGoalSpinner();
    }

    private void refreshGoalSpinner() {
        // Configure the spinner
        Spinner s = findViewById(R.id.goal_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, listGoals);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
    }

    /**
     * Detection representation
     */
    public class Detection {
        public int id;
        public double x1, y1, x2, y2, x3, y3, x4, y4;

        public double[] coords() {
            return new double[] {x1, y1, x2, y2, x3, y3, x4, y4};
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native void initAprilTags();

    public native void cleanupAprilTags();

    public native Detection searchForAprilTags(byte[] bytes, int nbytes);
}
