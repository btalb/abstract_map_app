package com.humancues.humancuestaggame;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
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

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class GameActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSIONS = 0;

    private final Size cameraSize = new Size(1280, 720);
    private String cameraID = null;
    private CameraCharacteristics cameraCharacteristics = null;
    private CameraDevice currentCameraDevice = null;
    private CaptureRequest currentCaptureRequest = null;

    private final int imageFormat = ImageFormat.JPEG;
    private ImageReader imageReader = null;

    public String[] listExperiments = {"Experiment 1", "Experiment 2", "TODO"};
    public String[] listGoals = {"Alice's office",
            "Bob's office"};

    private String currentGoal = "Alice's office";

    enum TAG_TYPE { NONE, GOAL, INFO, WRONG };
    private TAG_TYPE currentTagType = TAG_TYPE.NONE;
    private String currentTagText = null;

    private int HACK = 0;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // Reconstruct the adapters
        refreshExperimentSpinner();

        // Configure the surface view
        configureCameraView();

        // Configure the interface's interactivity
        configureInteractivity();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case CAMERA_PERMISSIONS: {
                if (grantResults.length > 0 && grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED) {
                    initialiseCamera();
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
            case GOAL:
                colourId = R.color.feedbackGreen;
                break;
            case INFO:
                colourId = R.color.feedbackOrange;
                break;
            case WRONG:
                colourId = R.color.feedbackRed;
                break;
            default:
                colourId = R.color.colorPrimary;
        }
        findViewById(R.id.task_panel).setBackgroundResource(colourId);
        findViewById(R.id.tag_panel).setBackgroundResource(colourId);

        // Act and exit early if tag type is NONE
        if (currentTagType == TAG_TYPE.NONE) {
            findViewById(R.id.tag_panel).setVisibility(View.GONE);
            return;
        }
        findViewById(R.id.tag_panel).setVisibility(View.VISIBLE);

        // Update the icon and text for the tag info
        ((ImageView) findViewById(R.id.tag_dismiss)).setImageResource((currentTagType == TAG_TYPE
                .GOAL) ? R.drawable.done_24dp : R.drawable.close_24dp);
        ((Button) findViewById(R.id.tag_info_button)).setText(currentTagText);
    }

    /**
     * Camera surface view control
     */
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
                        ImageReaderCallback(), null);

                // Initialise the camera
                initialiseCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

            }
        });
    }

    /**
     * Camera control functions
     */
    private void initialiseCamera() {
        // Do not do this if we already have a camera!
        //if (currentCameraDevice != null) return;

        // Ensure that camera access permissions exist
        int permission = ContextCompat.checkSelfPermission(this, Manifest
                .permission.CAMERA);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest
                    .permission.CAMERA}, CAMERA_PERMISSIONS);
            return;
        }

        // Attempt to get the ID of the back facing camera
        CameraManager cm = (CameraManager) this.getSystemService(Context
                .CAMERA_SERVICE);
        try {
            String[] cameras = cm.getCameraIdList();
            for (final String s : cameras) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(s);
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
            return;
        }

        StreamConfigurationMap scm = cameraCharacteristics.get
                (CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        for (final int i : scm.getOutputFormats()) {
            Log.e("HuC", "Output Format: " + i);
            Log.e("HuC", "Output Sizes: " + Arrays.toString(scm
                    .getOutputSizes(i)));
        }

        // We have the ID of the back facing camera, now
        try {
            cm.openCamera(cameraID, new CameraDeviceCallback(), null);
        } catch (CameraAccessException e) {
            Toast.makeText(this, "The app failed to access the camera",
                    Toast.LENGTH_LONG).show();
            return;
        }
    }

    class CameraDeviceCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            try {
                currentCameraDevice = cameraDevice;

                // Get a list of the Surfaces we want to push the camera data to
                List<Surface> ls = Arrays.asList(((SurfaceView) findViewById
                        (R.id.camera_view)).getHolder().getSurface(),
                        imageReader.getSurface());

                // Build a capture request, with the target surfaces included
                CaptureRequest.Builder b = cameraDevice.createCaptureRequest
                        (CameraDevice.TEMPLATE_PREVIEW);
                for (final Surface s : ls) b.addTarget(s);
                currentCaptureRequest = b.build();

                // Generate the capture session
                cameraDevice.createCaptureSession(ls, new
                        CameraSessionCallback(), null);
            } catch (CameraAccessException e) {
                Log.e("HuC", "Device failed to access camera...");
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {

        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {

        }
    }

    class CameraSessionCallback extends CameraCaptureSession.StateCallback {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            try {
                // Start capturing
                cameraCaptureSession.setRepeatingRequest(currentCaptureRequest,
                        null, null);
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
            // Attempt to get an image, exiting if that failed
            Image i = imageReader.acquireLatestImage();
            if (i == null) return;

            // Copy all of the bytes into an array (gross...)
            Image.Plane p = i.getPlanes()[0];
            byte[] bytes = new byte[p.getBuffer().remaining()];
            p.getBuffer().get(bytes);

            // Convert the JPEG into ARGB
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPremultiplied = false;
            Bitmap b = BitmapFactory.decodeByteArray(bytes,
                    0, p.getBuffer().position());

            // Move the bytes from the ARGB bitmap into a byte[]
            ByteBuffer bb = ByteBuffer.allocate(b.getByteCount());
            b.copyPixelsToBuffer(bb);
            bytes = bb.array();

            // Step into native code, checking the ARGB byte[] for April Tags
            int[] tags = searchForAprilTags(bytes);
            Log.e("HuC", "Returned " + Arrays.toString(tags));

            // Clean up things when we are done
            i.close();
        }
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
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    public native int[] searchForAprilTags(byte[] argbBytes);
}
