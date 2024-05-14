package com.example.cameraapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.TextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SaveImageFileClass implements Runnable{
    private final static String TAG = "SaveImageFileClass";

    //em tese, não precisa deste membro, mas para ser mais rápido na run e mais fácil
    private final boolean mFlagSaveImage;
    private final Image mImage;
    private final TextureView mTextureView;
    private final String mAbsoluteFilePath;
    private final Context mContext;

    private SingleMediaScanner mSingleMediaScanner;

    public SaveImageFileClass(Image image, String absoluteFilePath, Context context){
        Log.d(TAG, "Constructor with Image");
        mFlagSaveImage = true;
        mImage = image;
        mTextureView = null;
        mAbsoluteFilePath = absoluteFilePath;
        mContext = context;
    }

    public SaveImageFileClass(TextureView textureView, String absoluteFilePath, Context context){
        Log.d(TAG, "Constructor with TextureView");
        mFlagSaveImage = false;
        mImage = null;
        mTextureView = textureView;
        mAbsoluteFilePath = absoluteFilePath;
        mContext = context;
    }


    @Override
    public void run(){
        Log.d(TAG, "Run");
        if(mFlagSaveImage){
            saveImageToFile(mImage,mAbsoluteFilePath, mContext);
        }else{
            saveTextureViewToFile(mTextureView, mAbsoluteFilePath, mContext);
        }
    }


    public static File createFileName(String suffix, String fileFormat, File folder) throws IOException{
        if(suffix == null || fileFormat == null || folder == null){
            return null;
        }
        String timestamp = new SimpleDateFormat("yyyymmdd_hhmmss").format(new Date());
        String name = suffix + "_" + timestamp;
        File file = File.createTempFile(name, fileFormat, folder);
        return file;
    }

    public static File createFolder(String directory, String name){
        if(directory == null || name == null){
            return null;
        }
        File auxFile = Environment.getExternalStoragePublicDirectory(directory);
        File folder = new File(auxFile, name);
        if(!folder.exists()){
            folder.mkdirs();
        }
        return folder;
    }

    public static void saveImageToFile(Image image, String absoluteFilePath, Context context){
        if(image == null || absoluteFilePath == null){
            Log.d(TAG, "Unable to saveImageToFile, mImage == null || absoluteFilePath == null");
            return;
        }

        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();

        byte[] bytes = new byte[byteBuffer.remaining()];

        byteBuffer.get(bytes);

        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(absoluteFilePath);
            fileOutputStream.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {//limpar recursos
            image.close();
            if(fileOutputStream != null){
                try {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        attachFileToGallery(context, absoluteFilePath);
    }

    public static void saveTextureViewToFile(TextureView textureView, String absoluteFilePath, Context context){
        if(textureView == null || absoluteFilePath == null || context == null){
            Log.d(TAG, "Unable to saveTextureViewToFile, textureView == null || absoluteFilePath == null || context == null");
            return;
        }

        Bitmap bitmap = textureView.getBitmap();
        FileOutputStream out = null;

        try {
            out = new FileOutputStream(absoluteFilePath);
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,out);
            out.flush();
            out.close();
            MediaStore.Images.Media.insertImage(context.getContentResolver(),absoluteFilePath,absoluteFilePath,absoluteFilePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        attachFileToGallery(context, absoluteFilePath);

    }

    private static void attachFileToGallery(Context context, String absoluteFilePath){
        if(context == null){
            Log.d(TAG, "Unable to attach file to gallery, context == null");
            return;
        }
        SingleMediaScanner singleMediaScanner = new SingleMediaScanner(context, new File(absoluteFilePath));
    }

}