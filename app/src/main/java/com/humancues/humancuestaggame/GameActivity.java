package com.humancues.humancuestaggame;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.support.constraint.ConstraintLayout;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class GameActivity extends AppCompatActivity {
    // Camera information
    private static final int CAMERA_PERMISSIONS = 0;
    private final Size camera_size = new Size(1280, 720);
    private CameraManager camera_manager = null;
    private String camera_id = null;
    private CameraCharacteristics camera_characteristics = null;
    private CameraDevice camera_device = null;
    private CaptureRequest camera_capture_request = null;
    private CameraCaptureSession camera_capture_session = null;

    private final int IMAGE_FORMAT = ImageFormat.JPEG;
    private ImageReader image_reader = null;

    // Background thread for processing
    private HandlerThread camera_thread = null;
    private Handler camera_handler = null;

    // Tag detection information for the app
    private boolean detecting = false;
    private Detection current_detection = null;

    // Experimental trial configurations (static)
    public final String EXPERIMENTS_FOLDER = "human_cues_tag_experiments";

    // Experimental trial configurations (dynamically loaded / selected)
    public ArrayList<ExperimentDefinition> available_experiments = new ArrayList<>();
    public ExperimentDefinition current_experiment = null;

    // Data structures for representing an experiment
    // TODO should probably be moved out into its own file...
    enum SYMBOL_TYPE { EMPTY, LABEL, INFO }

    private class ExperimentDefinition {
       public final String name;

       public ArrayList<MappingDefinition> mappings = new ArrayList<>();
       public MappingDefinition last_mapping = null;

       public ArrayList<String> labels = new ArrayList<>();
       public String goal = null;

       ExperimentDefinition(String name, ArrayList<MappingDefinition> mappings) {
           this(name);
           for (MappingDefinition m : mappings) {
               addMapping(m);
           }
       }

       ExperimentDefinition(String name) {
            this.name = name;
        }

       public void addMapping(MappingDefinition m) {
            // Add to the list of labels if it is a new label
           if (m.type == SYMBOL_TYPE.LABEL && !labels.contains(m.text)) {
                labels.add(m.text);
           }

           // Add it the list of mappings
           mappings.add(m);
       }

       public boolean isAtGoal() {
           return goal != null && last_mapping != null && last_mapping.type == SYMBOL_TYPE.LABEL &&
                   last_mapping.text.equals(goal);
       }
    }

    private class MappingDefinition {
        public int id;
        public SYMBOL_TYPE type;
        public String text;

        MappingDefinition(int id, SYMBOL_TYPE type, String text) {
            this.id = id;
            this.type = type;
            this.text = text;
        }

        public int correspondingColour(String goal) {
            switch (type) {
                case EMPTY:
                    return R.color.feedbackNone;
                case LABEL:
                    return text.equals(goal) ? R.color.feedbackGreen : R.color.feedbackRed;
                case INFO:
                    return R.color.feedbackOrange;
                default:
                    return R.color.colorPrimary;
            }
        }
    }

    // Drawing variables
    private Paint highlight_paint = null;

    static {
        System.loadLibrary("native-lib");
    }

    /**
     * Lifecycle implementations
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // Configure the interface (interactivity and content)
        initialiseDrawing();
        configureInteractivity();
        loadExperiments();

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
                // Pull all state from the intro screen UI (shouldn't need to redo this, but lets
                // just do it again for overkill...
                current_experiment = available_experiments.get(((Spinner) findViewById(R.id
                        .experiment_spinner))
                        .getSelectedItemPosition());
                current_experiment.goal = current_experiment.labels.get(((Spinner) findViewById(R.id
                        .goal_spinner)).getSelectedItemPosition());

                Toast.makeText(GameActivity.this, "Starting experiment '" + current_experiment
                        .name + "', with goal: " + current_experiment.goal, Toast.LENGTH_LONG).show();

                // Update the UI
                ViewSwitcher vs = findViewById(R.id.switcher);
                vs.showNext();

                ((TextView) findViewById(R.id.task_info)).setText("find " + current_experiment.goal);
                findViewById(R.id.background_mask).setVisibility(View.GONE);
                updateTagDisplay();
            }
        });

        Spinner s_exp = findViewById(R.id.experiment_spinner);
        s_exp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectExperiment(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner s_goal = findViewById(R.id.goal_spinner);
        s_goal.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectGoal(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        ImageButton ib = findViewById(R.id.tag_dismiss);
        ib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean done = current_experiment.isAtGoal();
                current_experiment.last_mapping = null;

                if (done) {
                    findViewById(R.id.detection_view).setVisibility(View.GONE);
                    findViewById(R.id.background_mask).setVisibility(View.VISIBLE);
                    ((ViewSwitcher) findViewById(R.id.switcher)).showPrevious();
                } else {
                    updateTagDisplay();
                }
            }
        });

    }

    /**
     * Configure tag management and access
     */
    private void updateTagDisplay() {
        // Hide and exit if there is no current detection
        if (current_experiment.last_mapping == null) {
            findViewById(R.id.task_panel).setBackgroundResource(R.color.colorPrimary);
            findViewById(R.id.tag_panel).setVisibility(View.GONE);
            findViewById(R.id.detection_view).setVisibility(View.GONE);
            detecting = true;
            return;
        }

        // Apply correct colours
        final int colour_id = current_experiment.last_mapping.correspondingColour(current_experiment.goal);
        findViewById(R.id.task_panel).setBackgroundResource(colour_id);
        findViewById(R.id.tag_panel).setBackgroundResource(colour_id);

        // Update the icon
        findViewById(R.id.tag_panel).setVisibility(View.VISIBLE);
        ((ImageView) findViewById(R.id.tag_dismiss)).setImageResource(
                (current_experiment.isAtGoal()) ?
                        R.drawable.done_24dp : R.drawable.close_24dp);
        ((Button) findViewById(R.id.tag_info_button)).setText(
                (current_experiment.last_mapping.type == SYMBOL_TYPE.EMPTY) ?
                        "<empty tag>" : current_experiment.last_mapping.text);

        // Update the text (split the string, add elements to linear layout, & populate)
        String[] ss = ((current_experiment.last_mapping.type == SYMBOL_TYPE.EMPTY) ?
                "<empty tag>" : current_experiment.last_mapping.text).split("\\\\n");
        LinearLayout ll = findViewById(R.id.tag_info_layout);
        ll.removeAllViews();
        for (String s : ss) {
            ConstraintLayout cl = (ConstraintLayout) GameActivity.this.getLayoutInflater().inflate(
                    R.layout.tag_info_item, null);
            ((TextView) cl.findViewById(R.id.tag_item_text)).setText(s);
            ll.addView(cl);
        }

        // Show the detection view
        findViewById(R.id.detection_view).setVisibility(View.VISIBLE);

        // We have some tag info being displayed, turn detecting off
        detecting = false;
    }

    /**
     * Camera view and processing control
     */
    private void startCameraThread() {
        camera_thread = new HandlerThread("CameraBackground");
        camera_thread.start();
        camera_handler = new Handler(camera_thread.getLooper());
    }

    private void configureCameraView() {
        SurfaceView cv = findViewById(R.id.camera_view);
        cv.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                // Set size and format to match what we will be getting out
                // of the camera
                surfaceHolder.setFixedSize(camera_size.getWidth(), camera_size
                        .getHeight());

                // Create the image reader
                image_reader = ImageReader.newInstance(camera_size.getWidth(),
                        camera_size.getHeight(), IMAGE_FORMAT, 2);
                image_reader.setOnImageAvailableListener(new
                        ImageReaderCallback(), camera_handler);

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
            camera_manager = (CameraManager) this.getSystemService(Context
                    .CAMERA_SERVICE);
            String[] cameras = camera_manager.getCameraIdList();
            for (final String s : cameras) {
                CameraCharacteristics cc = camera_manager.getCameraCharacteristics(s);
                if (cc.get(CameraCharacteristics.LENS_FACING) ==
                        CameraMetadata.LENS_FACING_BACK) {
                    camera_id = s;
                    camera_characteristics = cc;
                }
            }
            if (camera_id == null) throw new Exception();
        } catch (Exception e) {
            Toast.makeText(this, "Back facing camera access failed!", Toast
                    .LENGTH_LONG).show();
        }
    }

    private void attachToCamera() {
        try {
            // Bail if we don't have the valid info (i.e. initialise camera info
            // has not been called), or permissions fail
            if (camera_manager == null || camera_id == null || camera_characteristics
                    == null || ContextCompat.checkSelfPermission(this, Manifest
                    .permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
            }
            camera_manager.openCamera(camera_id, new CameraDeviceCallback(),
                    camera_handler);
        } catch (CameraAccessException e) {
            Toast.makeText(this, "The app failed to access the camera",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void detachFromCamera() {
        if (camera_capture_session != null) {
            try {
                camera_capture_session.stopRepeating();
                camera_capture_session.abortCaptures();
            } catch (Exception e) {}
            camera_capture_session.close();
        }
        if (camera_device != null) camera_device.close();
        camera_capture_session = null;
        camera_device = null;
    }

    class CameraDeviceCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            try {
                GameActivity.this.camera_device = cameraDevice;

                // Get a list of the Surfaces we want to push the camera data to
                List<Surface> ls = Arrays.asList(((SurfaceView) findViewById
                        (R.id.camera_view)).getHolder().getSurface(),
                        image_reader.getSurface());

                // Build a capture request, with the target surfaces included
                CaptureRequest.Builder b = cameraDevice.createCaptureRequest
                        (CameraDevice.TEMPLATE_PREVIEW);
                for (final Surface s : ls) b.addTarget(s);
                camera_capture_request = b.build();

                // Generate the capture session
                cameraDevice.createCaptureSession(ls, new
                        CameraSessionCallback(), camera_handler);
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
                GameActivity.this.camera_capture_session = cameraCaptureSession;

                // Start capturing
                cameraCaptureSession.setRepeatingRequest(camera_capture_request,
                        null, camera_handler);
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
            // Always get an image, ensuring it is closed even if we
            // terminate early
            Image i = imageReader.acquireLatestImage();
            if (i == null) return;
            if (!detecting) {
                i.close();
                return;
            }

            // Step into native code, passing the JPEG byte buffer down to be decoded and checked
            // for April Tags
            Image.Plane p = i.getPlanes()[0];
            byte[] bytes = new byte[p.getBuffer().remaining()];
            p.getBuffer().get(bytes);
            current_detection = searchForAprilTags(bytes, bytes.length);
            if (current_detection != null) {
                // Extract symbols from the mapping database in the experiment definition
                for (final MappingDefinition d : current_experiment.mappings)
                    if (current_detection.id == d.id) current_experiment.last_mapping = d;

                // Get a mutable copy of the bitmap
                Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0,
                        bytes.length).copy(Bitmap.Config.ARGB_8888, true);

                // Configure the highlight paint to the type of detection
                highlight_paint.setColor(GameActivity.this.getResources()
                        .getColor(current_experiment.last_mapping.correspondingColour
                                (current_experiment.goal)));

                // Draw to the canvas
                final Path box = current_detection.path();
                final Canvas c = new Canvas(bm);
                highlight_paint.setStyle(Paint.Style.STROKE);
                c.drawPath(box, highlight_paint);
                highlight_paint.setStyle(Paint.Style.FILL);
                highlight_paint.setAlpha(128);
                c.drawPath(box, highlight_paint);

                // Finish by rotating the bitmap 90 degress
                Matrix m = new Matrix();
                m.postRotate(90);
                final Bitmap bmRotated = Bitmap.createBitmap(bm, 0, 0,
                        bm.getWidth(), bm.getHeight(), m, true);
                GameActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((ImageView) findViewById(R.id.detection_view))
                                .setImageBitmap(bmRotated);
                        updateTagDisplay();
                    }
                });
            }

            // Clean up things when we are done
            i.close();
        }
    }

    /**
     * Drawing helpers
     */
    private void initialiseDrawing() {
        highlight_paint = new Paint();
        highlight_paint.setAntiAlias(true);
        highlight_paint.setStrokeWidth(5);
    }

    /**
     * Use the experiments lists to populate the spinners
     */
    private void loadExperiments() {
        // Get the list of experiment definition files
        final AssetManager am = getAssets();
        String[] assets = {};
        try {
            assets = am.list(EXPERIMENTS_FOLDER);
            if (assets == null) {
               assets = new String[]{};
            }

            // Load the data from each of the found experiments
            for (String asset_filename : assets) {
                if (asset_filename.endsWith(".xml")) {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    loadExperiment(asset_filename, am.open(new File(EXPERIMENTS_FOLDER,
                            asset_filename).getPath(), AssetManager.ACCESS_BUFFER), dbf);
                } else {
                    Log.w("HuC", "Skipped: " + asset_filename);
                }
                Log.w("HuC", "Loaded: " + asset_filename);
            }
        } catch (Exception e) {
            Log.e("HuC", "Failed: " + e);
        }


        // When we are done loading the experiments, refresh the experiments spinner
        refreshExperimentSpinner();
    }

    private void loadExperiment(String asset_name, InputStream asset_stream, DocumentBuilderFactory
            dbf) {
        // Load the XML data from the file
        String name = null;
        ArrayList<MappingDefinition> defs = new ArrayList<>();
        try {
            // Load the document
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document d = db.parse(asset_stream);

            // Get the node containing the experiment's mappings
            NodeList ns = d.getElementsByTagName("mappings");
            if (ns.getLength() == 0) {
                return; // We found no experiment in the xml file, there is nothing more to do here
            }
            Node node_experiment = ns.item(0);  // Only use the first experiment per file

            // Pull out the experiment name if possible
            Node node_name = node_experiment.getAttributes().getNamedItem("name");
            name = node_name == null ? asset_name : node_name.getNodeValue();

            // Create mapping definitions from each found mapping & add it to the list
            ns = node_experiment.getChildNodes();
            for (int i = 0; i< ns.getLength(); i++) {
                // Don't process unless it is a mapping
                Node n = ns.item(i);
                if (!n.getNodeName().equals("mapping")) {
                    continue;
                }

                // Use the attributes to construct a mapping definition object
                NamedNodeMap attrs = n.getAttributes();
                defs.add(new MappingDefinition(
                        Integer.parseInt(attrs.getNamedItem("tag_id").getNodeValue()),
                        SYMBOL_TYPE.valueOf(attrs.getNamedItem("type").getNodeValue().toUpperCase()),
                        attrs.getNamedItem("text").getNodeValue()
                ));
            }
        } catch (Exception e) {
            Log.e("HuC", "Experiment @ '" + asset_name + "' failed to load with: " + e.getMessage());
            return;
        }

        // Create an experiment definition object & add it to the list of loaded experiments
        available_experiments.add(new ExperimentDefinition(name, defs));
    }

    private void selectExperiment(int number) {
        current_experiment = available_experiments.get(number);
        refreshGoalSpinner();
    }

    private void selectGoal(int number) {
        current_experiment.goal = current_experiment.labels.get(number);
    }

    private void refreshExperimentSpinner() {
        // Configure the spinner
        Spinner s = findViewById(R.id.experiment_spinner);
        ArrayList<String> experiment_names = new ArrayList<>();
        for (ExperimentDefinition e : available_experiments) {
            experiment_names.add(e.name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, experiment_names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);

        // Always reselect the first experiment (which will in turn update the list of goals)
        selectExperiment(0);
    }

    private void refreshGoalSpinner() {
        // Configure the spinner
        Spinner s = findViewById(R.id.goal_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, current_experiment.labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);

        // Always select the first goal by default
        selectGoal(0);
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

        public Path path() {
            Path p = new Path();
            p.moveTo((float) x1, (float) y1);
            p.lineTo((float) x2, (float) y2);
            p.lineTo((float) x3, (float) y3);
            p.lineTo((float) x4, (float) y4);
            p.close();
            return p;
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
