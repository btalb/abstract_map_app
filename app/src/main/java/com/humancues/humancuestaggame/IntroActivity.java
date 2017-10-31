package com.humancues.humancuestaggame;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
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

import java.util.Collections;
import java.util.List;

public class IntroActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSIONS = 0;

    private CameraDevice currentCameraDevice = null;
    private CaptureRequest currentCaptureRequest = null;

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
        setContentView(R.layout.activity_intro);

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
        String id = null;
        try {
            String[] cameras = cm.getCameraIdList();
            for (final String s : cameras) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(s);
                if (cc.get(CameraCharacteristics.LENS_FACING) ==
                        CameraMetadata.LENS_FACING_BACK) {
                    id = s;
                }
            }
            if (id == null) throw new Exception();
        } catch (Exception e) {
            Toast.makeText(this, "Back facing camera access failed!", Toast
                    .LENGTH_LONG).show();
            return;
        }

        // We have the ID of the back facing camera, now
        try {
            cm.openCamera(id, new CameraDeviceCallback(), null);
        } catch (CameraAccessException e) {
            Toast.makeText(this, "The app failed to access the camera",
                    Toast.LENGTH_LONG).show();
            return;
        }
    }

    class CameraDeviceCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // Get the Surface to hold the camera data
            Surface s = ((SurfaceView) findViewById(R.id.camera_view))
                    .getHolder().getSurface();

            List<Surface> ls = Collections.singletonList(s);

            try {
                // Update the cached values
                CaptureRequest.Builder b = cameraDevice.createCaptureRequest
                        (CameraDevice.TEMPLATE_PREVIEW);
                b.addTarget(s);
                currentCaptureRequest = b.build();
                currentCameraDevice = cameraDevice;

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
}
