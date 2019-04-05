package nl.ddq.ispexcamera;

import android.content.DialogInterface;
import android.graphics.Rect;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.LensShadingMap;
import android.hardware.camera2.params.BlackLevelPattern;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Range;
import android.util.SizeF;
import android.view.View;
import android.view.Menu;
import android.os.Build;
import android.view.MenuItem;
import com.parse.Parse;
import com.parse.ParseClassName;
import com.parse.ParseObject;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.awt.font.NumericShaper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static nl.ddq.ispexcamera.R.layout.activity_main;
import com.parse.ParseInstallation;


public class MainActivity extends AppCompatActivity {



    private static final String TAG = "AndroidCameraApi";
    private Button takePictureButton;
    private Button sendCharButton;
    private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);



        Parse.enableLocalDatastore(this);

        Parse.initialize(new Parse.Configuration.Builder(this)
                .applicationId("")
                // if defined
                .clientKey("")
                .server("https://XXX/parse/")
                .build()
        );


       ParseInstallation.getCurrentInstallation().saveInBackground();

        setContentView(activity_main);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);



        takePictureButton = (Button) findViewById(R.id.btn_takepicture);
        assert takePictureButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });



        sendCharButton = (Button) findViewById(R.id.btn_getcharacteristics);
        assert sendCharButton != null;
        sendCharButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getCharacteristics();
            }
        });





    }
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };
    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    // Get API version

    public static int getAPIVersion() {

int f=0;
        try {
            f= Build.VERSION.SDK_INT;


            Log.e("App", "api version" + f);

        } catch (NumberFormatException e) {
            Log.e("App", "error retrieving api version" + e.getMessage());
        }
        return f;
    }



// MOD Norbert Send characteristics to backend

    protected void getCharacteristics() {
        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());



            // Now submit to parse
            String buildmodel = Build.MODEL.replaceAll("[\\s\\-()]", "");
            if (buildmodel==null) buildmodel="unknown";
            ParseObject parse_cameraCharacteristics = new ParseObject(buildmodel);



            ParseObject parse_deviceCharacteristics = new ParseObject("android_deviceCharacteristics");

            parse_cameraCharacteristics.put("manufacturer", Build.MANUFACTURER);
            parse_cameraCharacteristics.put("model", Build.MODEL);
            parse_cameraCharacteristics.put("sdk", Build.VERSION.SDK_INT);

            // Check for capabilities
            // Available on all devices
            int[] int_REQUEST_AVAILABLE_CAPABILITIES = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

            // Check if RAW is supported by iterating over available capabilities
            int rawSupported=0;
            for (int i=0;i<int_REQUEST_AVAILABLE_CAPABILITIES.length;i++)  {
                if (int_REQUEST_AVAILABLE_CAPABILITIES[i]==3) rawSupported=1;
            }

            parse_cameraCharacteristics.put("request_available_capabilities", Arrays.toString(int_REQUEST_AVAILABLE_CAPABILITIES));
            parse_cameraCharacteristics.put("raw_supported", rawSupported);

            // API version
            // Available on all devices
            int api = getAPIVersion();

            // Lens distortions
            // Not available on all devices
            // float[], save as string, default "-"
            // Android 23-27: LENS_RADIAL_DISTORTION
            // Android 28-  : LENS_DISTORTION
            // TODO: Add Android 28- functionality
            if (api >= 23 && api <= 27) {
                float[] lens_distortion = characteristics.get(CameraCharacteristics.LENS_RADIAL_DISTORTION);
                if (lens_distortion == null) parse_cameraCharacteristics.put("lens_distortion", "-");
                else parse_cameraCharacteristics.put("lens_distortion", Arrays.toString(lens_distortion));
            } else parse_cameraCharacteristics.put("lens_distortion", "-");

            // Lens facing
            // Available on all devices
            // Integer
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            parse_cameraCharacteristics.put("lens_facing", facing);

            // Available apertures
            // Not available on all devices
            // float[], save as string, default "-"
            // TODO: save as list?
            float[] lens_available_apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
            if (lens_available_apertures == null) parse_cameraCharacteristics.put("lens_available_apertures", "");
            else parse_cameraCharacteristics.put("lens_available_apertures", Arrays.toString(lens_available_apertures));

            // Available filter densities
            // Not available on all devices
            // float[], save as string, default "-"
            // TODO: save as list?
            float[] lens_available_FILTER_DENSITIES = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FILTER_DENSITIES);
            if (lens_available_FILTER_DENSITIES == null) parse_cameraCharacteristics.put("lens_available_filter_densities","-");
            else parse_cameraCharacteristics.put("lens_available_filter_densities", Arrays.toString(lens_available_FILTER_DENSITIES));

            // Available focal lengths
            // Available on all devices
            // float[], save as string
            // TODO: save as list?
            float[] lens_available_FOCAL_LENGTHS = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (lens_available_FOCAL_LENGTHS == null) parse_cameraCharacteristics.put("lens_available_focal_lenghts", "-");
            else parse_cameraCharacteristics.put("lens_available_focal_lenghts", Arrays.toString(lens_available_FOCAL_LENGTHS));

            // Hyperfocal distance
            // Not available on all devices
            // Float, default "-"
            // TODO: change default to float
            Float lens_available_HYPERFOCAL_DISTANCE = characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);
            if (lens_available_HYPERFOCAL_DISTANCE == null) parse_cameraCharacteristics.put("lens_available_hyperfocal_distance", "-");
            else parse_cameraCharacteristics.put("lens_available_hyperfocal_distance", Float.toString(lens_available_HYPERFOCAL_DISTANCE));

            // Minimum focus distance
            // Not available on all devices
            // Float, default "-"
            // TODO: change default to float
            Float lens_available_LENS_INFO_MINIMUM_FOCUS_DISTANCE = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            if (lens_available_LENS_INFO_MINIMUM_FOCUS_DISTANCE == null) parse_cameraCharacteristics.put("lens_info_minimum_focus_distance","-");
            else parse_cameraCharacteristics.put("lens_info_minimum_focus_distance",Float.toString(lens_available_LENS_INFO_MINIMUM_FOCUS_DISTANCE));

            // Intrinsic lens calibration
            // Not available on all devices
            // float[], save as string, default "-"
            // TODO: save as list?
            if (api >= 23) {
                float[] lens_available_LENS_INTRINSIC_CALIBRATION = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
                if (lens_available_LENS_INTRINSIC_CALIBRATION == null) parse_cameraCharacteristics.put("lens_intrinsic_calibration", "-");
                else parse_cameraCharacteristics.put("lens_intrinsic_calibration", Arrays.toString(lens_available_LENS_INTRINSIC_CALIBRATION));
            }
            else parse_cameraCharacteristics.put("lens_intrinsic_calibration", "-");

            // Lens pose rotation
            // Not available on all devices
            // float[], save as string, default "-"
            // TODO: save as list?
            if (api >= 23) {
                float[] lens_available_LENS_POSE_ROTATION = characteristics.get(CameraCharacteristics.LENS_POSE_ROTATION);
                if (lens_available_LENS_POSE_ROTATION == null) parse_cameraCharacteristics.put("lens_pose_rotation", "-");
                else parse_cameraCharacteristics.put("lens_pose_rotation", Arrays.toString(lens_available_LENS_POSE_ROTATION));
            }
            else parse_cameraCharacteristics.put("lens_pose_rotation", "-");

            // Lens pose translation
            // Not available on all devices
            // float[], save as string, default "-"
            // TODO: save as list?
            if (api >= 23) {
                float[] lens_available_LENS_POSE_TRANSLATION = characteristics.get(CameraCharacteristics.LENS_POSE_TRANSLATION);
                if (lens_available_LENS_POSE_TRANSLATION == null) parse_cameraCharacteristics.put("lens_pose_translation", "-");
                else parse_cameraCharacteristics.put("lens_pose_translation", Arrays.toString(lens_available_LENS_POSE_TRANSLATION));
            }
            else parse_cameraCharacteristics.put("lens_pose_translation", "-");

            // Sensor black level pattern
            // Not available on all devices
            // BlackLevelPattern, save as string, default "-"
            // TODO: save as list?
            BlackLevelPattern sensor_black_level_pattern = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
            if (sensor_black_level_pattern == null) parse_cameraCharacteristics.put("sensor_black_level_pattern", "-");
            else parse_cameraCharacteristics.put("sensor_black_level_pattern", sensor_black_level_pattern.toString());

            // Active array size
            // Available on all devices
            // Rect, save as string
            // TODO: save as numbers (length, width, total)?
            Rect sensor_info_active_array_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if (sensor_info_active_array_size == null) parse_cameraCharacteristics.put("sensor_info_active_array_size", "-");
            else parse_cameraCharacteristics.put("sensor_info_active_array_size", sensor_info_active_array_size.toString());

            // Color filter arrangement
            // Not available on all devices
            // Integer, default "-"
            // TODO: change default to Integer
            Integer sensor_info_color_filter_arrangement = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
            if (sensor_info_color_filter_arrangement == null) parse_cameraCharacteristics.put("sensor_info_color_filter_arrangement", "-");
            else parse_cameraCharacteristics.put("sensor_info_color_filter_arrangement", sensor_info_color_filter_arrangement.toString());

            // Reprocess max capture stall
            // Not available on all devices
            // Integer, default "-"
            // TODO: change default to Integer
            if (api >= 23) {
                Integer reprocess_max_capture_stall = characteristics.get(CameraCharacteristics.REPROCESS_MAX_CAPTURE_STALL);
                if (reprocess_max_capture_stall == null) parse_cameraCharacteristics.put("reprocess_max_capture_stall", 0);
                else parse_cameraCharacteristics.put("reprocess_max_capture_stall", reprocess_max_capture_stall);
            }
            else parse_cameraCharacteristics.put("reprocess_max_capture_stall", 0);

            // Exposure time range
            // Not available on all devices
            // Range<Long>, save as string, default "-"
            // Save lower and upper limits as Long, default -9999L
            Range sensor_info_exposure_time_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            String sensor_info_exposure_time_range_as_string = "-";
            Long exposure_time_min = -9999L;
            Long exposure_time_max = -9999L;
            if (sensor_info_exposure_time_range != null) {
                sensor_info_exposure_time_range_as_string = sensor_info_exposure_time_range.toString();
                exposure_time_min = (Long) sensor_info_exposure_time_range.getLower();
                exposure_time_max = (Long) sensor_info_exposure_time_range.getUpper();
            }


            parse_cameraCharacteristics.put("sensor_info_exposure_time_range", sensor_info_exposure_time_range_as_string);



            parse_cameraCharacteristics.put("exposure_time_min", exposure_time_min);




            parse_cameraCharacteristics.put("exposure_time_max", exposure_time_max);






            // Sensor physical size
            // Available on all devices
            // SizeF, save as string, default "-"
            // TODO: remove default, instead save two values?
            SizeF sensor_info_physical_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (sensor_info_physical_size == null) parse_cameraCharacteristics.put("sensor_info_physical_size",  "-");
            else parse_cameraCharacteristics.put("sensor_info_physical_size", sensor_info_physical_size.toString());

            // Sensor pixel array size
            // Available on all devices
            // Size, save as string, default "-"
            // TODO: remove default, instead save two values?
            Size sensor_info_pixel_array_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
            if (sensor_info_pixel_array_size == null) parse_cameraCharacteristics.put("sensor_info_pixel_array_size",  "-");
            else parse_cameraCharacteristics.put("sensor_info_pixel_array_size", sensor_info_pixel_array_size.toString());

            // Sensor pre-correction active array size
            // Available on all devices for Android 23-
            // Rect, save as string, default "-"
            // TODO: save as separate numbers?
            if (api >= 23) {
                Rect sensor_info_pre_correction_active_array_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
                parse_cameraCharacteristics.put("sensor_info_pre_correction_active_array_size", sensor_info_pre_correction_active_array_size.toString());
            }
            else parse_cameraCharacteristics.put("sensor_info_pre_correction_active_array_size", "-");

            // Sensor sensitivity (ISO) range
            // Not available on all devices
            // Range<Long>, save as string, default "-"
            // TODO: change to save min and max separately?
            Range sensor_info_sensitivity_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (sensor_info_sensitivity_range == null) parse_cameraCharacteristics.put("sensor_info_sensitivity_range", "-");
            else parse_cameraCharacteristics.put("sensor_info_sensitivity_range", sensor_info_sensitivity_range.toString());

            // Sensor max analog sensitivity (ISO)
            // Not available on all devices
            // Integer, default "-"
            // TODO: change default to Integer
            Integer sensor_max_analog_sensitivity = characteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY);
            if (sensor_max_analog_sensitivity == null) parse_cameraCharacteristics.put("sensor_max_analog_sensitivity", 0);
            else parse_cameraCharacteristics.put("sensor_max_analog_sensitivity", sensor_max_analog_sensitivity);

            // Sensor white level
            // Not available on all devices
            // Integer, default "-"
            // TODO: change default to Integer
            Integer sensor_info_white_level = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);

            if (sensor_info_white_level == null) parse_cameraCharacteristics.put("sensor_info_white_level", 0);
            else {
                parse_cameraCharacteristics.put("sensor_info_white_level", sensor_info_white_level);
            }

            // Sensor orientation
            // Available on all devices
            // Integer
            Integer sensor_orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            if (sensor_orientation == null) parse_cameraCharacteristics.put("sensor_orientation", -9999);
            else parse_cameraCharacteristics.put("sensor_orientation", sensor_orientation);


            // Available lens shading map modes
            // Not available on all devices, even Android 23-
            // int[], save as string, default "-"
            // TODO: save as list?
            if (api >= 23) {
                int[] lens_available_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES);
                if (lens_available_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES == null) parse_cameraCharacteristics.put("statistics_info_available_lens_shading_map_modes", "-");
                else parse_cameraCharacteristics.put("statistics_info_available_lens_shading_map_modes", Arrays.toString(lens_available_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES));
            }
            else parse_cameraCharacteristics.put("statistics_info_available_lens_shading_map_modes", "-");

            // Shading available modes
            // Available on all devices, Android 23-
            // int[], save as string, default "-"
            // Not sure how this differs from the one above
            if (api >= 23) {
                int[] shading_available_modes = characteristics.get(CameraCharacteristics.SHADING_AVAILABLE_MODES);
                parse_cameraCharacteristics.put("shading_available_modes", Arrays.toString(shading_available_modes));
            }
            else parse_cameraCharacteristics.put("shading_available_modes", "-");

            // Whether or not shading is applied in RAW images
            // Not available on all devices, even Android 23-
            // Boolean, default false
            if (api >= 23) {
                Boolean sensor_info_lens_shading_applied = characteristics.get(CameraCharacteristics.SENSOR_INFO_LENS_SHADING_APPLIED);
                if (sensor_info_lens_shading_applied == null) parse_cameraCharacteristics.put("sensor_info_lens_shading_applied", false);
                else parse_cameraCharacteristics.put("sensor_info_lens_shading_applied", sensor_info_lens_shading_applied);
            }
            else parse_cameraCharacteristics.put("sensor_info_lens_shading_applied", false);

            // TODO: Use CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP ?

            // Supported hardware level
            // Available on all devices
            // Integer
            Integer info_supported_hardware_level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            parse_cameraCharacteristics.put("info_supported_hardware_level", info_supported_hardware_level);

            // save camera to parse server
            parse_cameraCharacteristics.saveEventually();

            // device characteristics
            parse_deviceCharacteristics.put("Manufacturer", Build.MANUFACTURER);
            parse_deviceCharacteristics.put("Model", Build.MODEL);
            parse_deviceCharacteristics.put("API_Level", api);

            // save device to parse server
            parse_deviceCharacteristics.saveEventually();


            // Show dialog
            AlertDialog alertDialog;
            alertDialog= new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle("iSPEX Test");
            alertDialog.setMessage("Thank you for submitting your " + Build.MODEL + " camera specs");
            alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    return;
                } });
            alertDialog.show();




        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    protected void takePicture() {
        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());


            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);



            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.RAW10);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            final File file = new File(Environment.getExternalStorageDirectory()+"/pic.jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }







    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }
    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        //closeCamera();
        stopBackgroundThread();
        super.onPause();
    }










    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
