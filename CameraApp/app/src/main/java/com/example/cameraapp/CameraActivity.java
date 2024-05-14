package com.example.cameraapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.InputType;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    public static final int PERMISSION_CODE_CAMERA = 0;
    public static final int PERMISSION_CODE_STORAGE = 1;

    private static final String TAG = "CameraActivity";

    //se falso, salva textureview, se verdadeiro, salva a imagem pela captura da câmera
    private final boolean mFlagSaveImage = true;

    //private final Long mShutterFrequency = Long.valueOf(10000);
    //private Long mShutterFrequency;
    private Long mShutterNanoSecOpen;
    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback;

    private TextureView mTextureView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener;

    private CaptureRequest.Builder mCaptureRequestBuilder;

    private ImageReader mImageReader;

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener;

    private CameraCaptureSession mCameraCaptureSession;
    private CameraCaptureSession.CaptureCallback mCaptureCallback;

    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;

    private String mCameraId;
    private String mBackgroundThreadName;

    private Size mTextureViewPreviewSize;
    private Size mCameraSize;
    private Size mSavableImageSize;

    private double[] mBestRatios;
    private int mLensFacing;
    private int mImageFormat;
    private String mFileFormat = ".jpg";
    private String mFileNameSuffix = "IMG";

    private File mImgFolder;

    //private boolean fullSupport;

    private String mDesiredCameraId;
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0,0);
        ORIENTATIONS.append(Surface.ROTATION_90,90);
        ORIENTATIONS.append(Surface.ROTATION_180,180);
        ORIENTATIONS.append(Surface.ROTATION_270,270);
    }

    CameraCharacteristicsHolder mCameraCharacteristicsHolder;

    AlertDialog mAlertDialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        checkWriteStoragePermission();

        findLayoutComponents();
        initializeConstants();
        setupListeners();




    }

    @Override
    protected void onResume(){
        super.onResume();

        startBackgroundThread();
        if(mTextureView.isAvailable()){
            openCamera();
        }else{
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause(){

        closeCamera();
        stopBackgroundThread();

        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.camera_menu,menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem){
        AlertDialog.Builder adBuilder;
        switch(menuItem.getItemId()){

            case R.id.menu_main_capture_photo:
                captureImage();
                break;
            case R.id.menu_main_specifications:
                //AlertDialog alertDialog = null;
                adBuilder = new AlertDialog.Builder(this);
                adBuilder.setTitle(R.string.specifications);
                adBuilder.setMessage(mCameraCharacteristicsHolder.toString());
                adBuilder.setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mAlertDialog.dismiss();
                    }
                });
                mAlertDialog = adBuilder.create();
                mAlertDialog.show();
                break;
            case R.id.menu_main_next_camera:
                //meio burro essa maneira de passar o id da camera mas fazer o que
                CameraManager cameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
                //String newCameraId = null;
                boolean b = false;
                try {
                    for (String id : cameraManager.getCameraIdList()) {
                        if(b){
                            mDesiredCameraId = id;
                            b = false;
                            break;
                        }
                        if(id.equals(mCameraId)){
                            b = true;
                        }
                    }
                    if(b){
                        mDesiredCameraId = cameraManager.getCameraIdList()[0];
                    }
                } catch (CameraAccessException e) {
                    throw new RuntimeException(e);
                }
                closeCamera();
                openCamera();
                break;
            case R.id.menu_main_shutter_speed:



                final EditText editText = new EditText(this);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                editText.setLayoutParams(layoutParams);

                adBuilder = new AlertDialog.Builder(this);
                adBuilder.setTitle(R.string.change_shutter_speed);
                String auxString;
                if(mShutterNanoSecOpen == Long.valueOf(0)){
                    auxString = getResources().getString(R.string.default_string);
                }else{
                    auxString = mShutterNanoSecOpen.toString();
                }
                String messageString = getApplicationContext().getString(R.string.current) + " " + auxString + "\n" +
                        getApplicationContext().getString(R.string.range) + " " + mCameraCharacteristicsHolder.getExposureTimeRange().toString() + "\n" +
                        getApplicationContext().getString(R.string.shutter_zero_value);
                adBuilder.setMessage(messageString);
                adBuilder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String etString = editText.getText().toString();
                        if(!etString.isEmpty()){
                            Long newValueOfNanoSeconds = Long.parseLong(etString);

                            if(newValueOfNanoSeconds == Long.valueOf(0) || mCameraCharacteristicsHolder.getExposureTimeRange().contains(newValueOfNanoSeconds)){
                                //Toast.makeText(getApplicationContext(),"Yes", Toast.LENGTH_LONG).show();
                                mShutterNanoSecOpen = newValueOfNanoSeconds;
                                startPreview();
                            }else{
                                Toast.makeText(getApplicationContext(),R.string.invalid_value, Toast.LENGTH_LONG).show();
                            }
                        }else{
                            Toast.makeText(getApplicationContext(),R.string.invalid_value, Toast.LENGTH_LONG).show();
                        }
                        //Toast.makeText(getApplicationContext(),R.string.invalid_value, Toast.LENGTH_LONG).show();

                        mAlertDialog.dismiss();
                    }
                });
                mAlertDialog = adBuilder.create();


                mAlertDialog.setView(editText);
                mAlertDialog.show();

                break;
            default:
                break;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch(requestCode){
            case PERMISSION_CODE_CAMERA:
                if(grantResults[0] == PackageManager.PERMISSION_DENIED){
                    Toast.makeText(this,getResources().getString(R.string.camera_permission_denied),
                            Toast.LENGTH_SHORT).show();
                }
                break;
            case PERMISSION_CODE_STORAGE:
                if(grantResults[0] == PackageManager.PERMISSION_DENIED){
                    Toast.makeText(this,getResources().getString(R.string.storage_permission_denied),
                            Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    private void checkWriteStoragePermission(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED) {
                if(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, getResources().getString(R.string.storage_permission_needed), Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_CODE_STORAGE);
            }
        }
    }

    private void findLayoutComponents(){
        mTextureView = (TextureView) findViewById(R.id.camera_textureView);
    }

    private void initializeConstants(){
        mBestRatios = new double[]{(double) 16 / (double) 9, (double) 4 / (double) 3};

        mLensFacing = CameraMetadata.LENS_FACING_BACK;
        mImageFormat = ImageFormat.JPEG;

        mBackgroundThreadName = "CameraActivity_backgroundThread";

        String folderName = getResources().getString(R.string.app_name);
        mImgFolder = SaveImageFileClass.createFolder(Environment.DIRECTORY_PICTURES, folderName);


        mDesiredCameraId = null;
        mAlertDialog = null;
        //mShutterFrequency = Long.valueOf(0);
        mShutterNanoSecOpen = Long.valueOf(0);
    }


    private void openCamera(){
        setupCamera();
        connectCamera();
    }

    private void closeCamera(){
        if(mCameraDevice != null){
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }



    private void captureImage(){
        if(mFlagSaveImage){//salva imagem da camera
            try {
                mCameraCaptureSession.capture(mCaptureRequestBuilder.build(),
                        mCaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
        }else{
            try {//salva imagem do preview
                File imgFile = SaveImageFileClass.createFileName(mFileNameSuffix, mFileFormat, mImgFolder);
                mBackgroundHandler.post(new SaveImageFileClass(mTextureView, imgFile.getAbsolutePath(), getApplicationContext()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    /*
    /startBackgroundThread
    /Descrição: Inicia a thread que cuidará de tarefas de processamento em background
    /           Chamada a partir de resume()
     */
    private void startBackgroundThread(){
        mBackgroundHandlerThread = new HandlerThread(mBackgroundThreadName);
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    /*
    /stopBackgroundThread
    /Descrição: Mata a thread que cuidará de tarefas de processamento em background
    /           Chamada a partir de pause()
     */
    private void stopBackgroundThread(){
        //sai assim que terminar as tarefas
        mBackgroundHandlerThread.quitSafely();
        try{
            //limpar recursos
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        }catch(InterruptedException e){
            e.printStackTrace();
        }
    }

    /*
    /setupListeners
    /Descrição: Define os listener de
    /               CameraDevice.StateCallback
    /               TextureView.SurfaceTextureListener
    /               ImageReader.OnImageAvailableListener
    /               CameraCaptureSession.CaptureCallback
    */
    private void setupListeners() {
        mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                mCameraDevice = cameraDevice;
                startPreview();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                cameraDevice.close();
                mCameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int i) {
                cameraDevice.close();
                mCameraDevice = null;
            }
        };

        mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                mCameraSize = new Size(width, height);
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

            }
        };

        /*
        if(!mTextureView.isAvailable()){
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
        */
        mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                Log.d(TAG,"onImageAvailable");

                File imgFile = null;
                try {
                    imgFile = SaveImageFileClass.createFileName(mFileNameSuffix, mFileFormat, mImgFolder);
                    mBackgroundHandler.post(new SaveImageFileClass(imageReader.acquireLatestImage(), imgFile.getAbsolutePath(), getApplicationContext()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        };


        mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);

                startStillCaptureRequest();
            }
        };

    }

    private void setupCamera(){
        CameraManager cameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        String newCameraId = null;
        try {
            if(BuildConfig.DEBUG) {
                for (String id : cameraManager.getCameraIdList()) {
                    CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
                    CameraCharacteristicsHolder  cch= new CameraCharacteristicsHolder(cameraCharacteristics, id);
                    Log.d(TAG, cch.toString());
                }
            }
            if(mDesiredCameraId != null){
                for(String id : cameraManager.getCameraIdList()){
                    if(mDesiredCameraId.equals(id)){
                        newCameraId = id;
                        break;
                    }
                }
            }
            if(newCameraId == null){
                for(String id : cameraManager.getCameraIdList()){
                    CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
                    if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == mLensFacing) {
                        newCameraId = id;
                        break;
                    }
                }
            }
            if(newCameraId != null){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(newCameraId);

                mCameraId = newCameraId;

                mCameraCharacteristicsHolder = new CameraCharacteristicsHolder(cameraCharacteristics, mCameraId);


                //Range<Long> exposure_time_range = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                //long freq = 1000;
/*                if(exposure_time_range != null && exposure_time_range.contains(1000000000/mShutterFrequency)){
                    //set shutter speed
                    fullSupport = true;
                }else{
                    fullSupport = false;
                }*/



                //pegar todas as resoluções da câmera
                StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                mTextureViewPreviewSize = chooseOptimalSize(streamConfigurationMap.getOutputSizes(
                        SurfaceTexture.class));

                calculateTextureViewTransform();

                mSavableImageSize = chooseOptimalSize(streamConfigurationMap.getOutputSizes(
                        mImageFormat));
                //max images deve ser no mínimo 2 para usar o método ImageReader.acquireLatestImage();
                mImageReader = ImageReader.newInstance(mSavableImageSize.getWidth(), mSavableImageSize.getHeight(), mImageFormat, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
            }
/*            for(String id : cameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == mLensFacing){
*//*
                    Range<Long> exposure_time_range = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                    if(isHardwareLevelSupported(cameraCharacteristics,CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)){
                        Log.d(TAG, "Hardware Support Level = FULL");
                    }else{
                        Log.d(TAG, "Hardware Support Level = NOT FULL");
                    }
*//*

                    Range<Long> exposure_time_range = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                    //long freq = 1000;
                    if(exposure_time_range != null && exposure_time_range.contains(1000000000/mShutterFrequency)){
                        //set shutter speed
                        fullSupport = true;
                    }else{
                        fullSupport = false;
                    }

                    mCameraId = id;

                    //pegar todas as resoluções da câmera
                    StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    mTextureViewPreviewSize = chooseOptimalSize(streamConfigurationMap.getOutputSizes(
                            SurfaceTexture.class));

                    calculateTextureViewTransform();

                    mSavableImageSize = chooseOptimalSize(streamConfigurationMap.getOutputSizes(
                            mImageFormat));
                    //max images deve ser no mínimo 2 para usar o método ImageReader.acquireLatestImage();
                    mImageReader = ImageReader.newInstance(mSavableImageSize.getWidth(), mSavableImageSize.getHeight(), mImageFormat, 2);
                    mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                    return;
                }
            }*/


        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    /*
    /connectCamera
    /Descrição: Conecta à câmera, ou simplesmente a abre, após setupCamera
    /
     */

    private void connectCamera(){
        CameraManager cameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            //versão marshmallow ou acima do android requer que cheque permissão
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){

                if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED){
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
                }else{
                    //checa se já foi recusada a permissão
                    if(shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)){
                        Toast.makeText(this, getResources().getString(R.string.camera_permission_denied),
                                Toast.LENGTH_LONG).show();
                    }
                    //requisita permissão novamente, que retornará com resultado com código PERMISSION_CODE_CAMERA
                    requestPermissions(new String[] {Manifest.permission.CAMERA}, PERMISSION_CODE_CAMERA);
                }

            }else{
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void startPreview(){
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mTextureViewPreviewSize.getWidth(), mTextureViewPreviewSize.getHeight());

        Surface previewSurface = new Surface(surfaceTexture);

        try{
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            configureCaptureRequestBuilder();

            mCameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCameraCaptureSession = session;
                            //caso dê certo
                            try{
                                //para fazer algo com os dados, é necessário ter um captureCallback, pelo
                                //que parece, no lugar do null
                                mCameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                        null,mBackgroundHandler);
                            }catch(CameraAccessException e){
                                e.printStackTrace();
                            }

                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            //caso dê errado
                            Toast.makeText(getApplicationContext(),
                                    "Capture Session Configuration Failed",
                                    Toast.LENGTH_LONG).show();

                        }
                    },
                    null);
        }catch(CameraAccessException e){
            e.printStackTrace();
        }

    }

    private void startStillCaptureRequest(){
        Log.d(TAG, "startStillCaptureRequest");
        if(mCameraDevice == null){
            closeCamera();
            openCamera();
            Log.d(TAG, "mCameraDevice == null");
            return;
        }
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());

            configureCaptureRequestBuilder();

            //para a rotação
            //captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, totalRotation);

            CameraCaptureSession.CaptureCallback stillCaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
/*
                    try {
                        //File imageFile = createImageFileName();
                        createImageFileName();

                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
*/
                }



            };

            //null porque já está na thread em background
            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void configureCaptureRequestBuilder(){
        //if(mShutterFrequency == Long.valueOf(0))  return;
        if(mShutterNanoSecOpen == Long.valueOf(0))  return;

        //checa se esta dentro do intervalo
        //Long nanoSecondsOpen = 1000000000 / mShutterFrequency;

        if(mCameraCharacteristicsHolder.getExposureTimeRange().contains(mShutterNanoSecOpen)){
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_OFF);
            //mCaptureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,Long.valueOf(1000000000)/mShutterFrequency);
            mCaptureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,mShutterNanoSecOpen);
        }

        /*if(fullSupport){
            //control ae mode off, para que não seja sobrescito o valor em sensor exposure time
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_OFF);
            mCaptureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,Long.valueOf(1000000000)/mShutterFrequency);//1ms = 1khz
        }
*/
        if(BuildConfig.DEBUG){
            Long j = mCaptureRequestBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
            if(j != null){
                Log.d(TAG, j.toString());
            }
        }


    }



    /*
    /chooseOptimalSize
    /Descrição: Escolhe melhor tamanho.
    /           Usada para escolher o tamanho de preview de TextureView ou o tamanho da imagem salva.
    /           É escolhida de acordo com a razão de câmera ou com razões definidas em bestRatios.
     */
    private Size chooseOptimalSize(Size[] sizes){

        class CompareSizeByArea implements Comparator<Size> {

            //compara dois objetos
            //se size1>size2 retorna 1
            //se size1<size2 retorna -1
            //se size1==size2 retorna 0
            @Override
            public int compare(Size size1,Size size2){
                int result = size1.getWidth() * size1.getHeight() -
                        size2.getWidth() * size2.getHeight();

                if(result<0){
                    result = -1;
                } else if (result>0) {
                    result = 1;
                }

                return result;
            }

        }

        Size returnSize;

        //armazenar possíveis tamanhos
        List<Size> possibleSizeList = new ArrayList<Size>();
        List<Size> croppableSizeList = new ArrayList<Size>();

        double mainRatio = (double) mCameraSize.getWidth() / (double) mCameraSize.getHeight();

        Size displaySize = new Size(Resources.getSystem().getDisplayMetrics().widthPixels,
                Resources.getSystem().getDisplayMetrics().heightPixels);


        double actualRatio;
        double errorPercentage = 0.01;

        CompareSizeByArea compareSizeByArea = new CompareSizeByArea();

        for(Size size : sizes){
            actualRatio = (double) size.getWidth() / (double) size.getHeight();

            if(mainRatio <= actualRatio * (1 + errorPercentage) &&
                    mainRatio >= actualRatio * (1 - errorPercentage)){
                possibleSizeList.add(size);
            }else if(compareSizeByArea.compare(displaySize,size) == 1){
                for(int i = 0; i < mBestRatios.length; i++){
                    if(actualRatio <= mBestRatios[i] * (1 + errorPercentage) &&
                            actualRatio >= mBestRatios[i] * (1 - errorPercentage)){
                        croppableSizeList.add(size);
                    }
                }
            }
        }
        //achou tamanho na mesma proporção
        if(possibleSizeList.size() > 0){
            returnSize =  Collections.max(possibleSizeList, compareSizeByArea);
        }else {
            if (croppableSizeList.size() > 0) {
                //provavelmente eu pegue o de maior tamanho, que deve estar inicialmente na lista,
                //e encontre os valores de cropp
                returnSize = croppableSizeList.get(0);
            }else{
                returnSize =  sizes[0];
            }
        }
        return returnSize;
    }

    /*
    /calculateTextureViewTransform
    /Descrição: Calcula as razões do tamanho da câmera e textureView para que a imagem não fique
    /           esticada.
     */
    private void calculateTextureViewTransform(){

        Matrix transformMatrix;

        double imageRatio = (double) mCameraSize.getWidth() / (double) mCameraSize.getHeight();
        double previewRatio = (double) mTextureViewPreviewSize.getWidth() / (double) mTextureViewPreviewSize.getHeight();

        double rotation = ORIENTATIONS.get(mTextureView.getDisplay().getRotation());

        double scaleX;
        double scaleY;
        if(previewRatio < imageRatio){
            scaleX = 1 / (imageRatio * previewRatio);
            scaleY = 1;
        }else{
            scaleX = 1;
            scaleY = (imageRatio * previewRatio);
        }

        transformMatrix = new Matrix();
        transformMatrix.setScale( (float) scaleX, (float) scaleY, mTextureViewPreviewSize.getWidth() / 2, mTextureViewPreviewSize.getHeight() / 2);
        transformMatrix.postRotate((float) - rotation, mTextureViewPreviewSize.getWidth() / 2, mTextureViewPreviewSize.getHeight() / 2);

        mTextureView.setTransform(transformMatrix);
    }

}