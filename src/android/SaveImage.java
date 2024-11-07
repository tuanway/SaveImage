package com.quiply.cordova.saveimage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.json.JSONArray;
import org.json.JSONException;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.MediaStore;
import android.util.Log;
import android.os.Build;
import android.net.Uri;
import android.os.Environment;



/**
 * The SaveImage class offers a method for saving an image to the device's media gallery.
 */
public class SaveImage extends CordovaPlugin {
    public static final int WRITE_PERM_REQUEST_CODE = 1;
    private final String ACTION = "saveImageToGallery";
    private final String READ_MEDIA_IMAGES = Manifest.permission.READ_MEDIA_IMAGES;
    private final String WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private CallbackContext callbackContext;
    private String filePath;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals(ACTION)) {
            saveImageToGallery(args, callbackContext);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check saveImage arguments and app permissions
     *
     * @param args              JSON Array of args
     * @param callbackContext   callback id for optional progress reports
     *
     * args[0] filePath         file path string to image file to be saved to gallery
     */  
    private void saveImageToGallery(JSONArray args, CallbackContext callback) throws JSONException {
        this.filePath = args.getString(0);
        this.callbackContext = callback;
        Log.d("SaveImage", "SaveImage in filePath: " + filePath);

        if (filePath == null || filePath.equals("")) {
            callback.error("Missing filePath");
            return;
        }

        // Request permissions based on Android version
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            if (PermissionHelper.hasPermission(this, READ_MEDIA_IMAGES)) {
                performImageSave();
            } else {
                PermissionHelper.requestPermission(this, WRITE_PERM_REQUEST_CODE, READ_MEDIA_IMAGES);
            }
        } else {
            // Android 13 and below: request WRITE_EXTERNAL_STORAGE permission
            if (PermissionHelper.hasPermission(this, WRITE_EXTERNAL_STORAGE)) {
                performImageSave();
            } else {
                PermissionHelper.requestPermission(this, WRITE_PERM_REQUEST_CODE, WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    /**
     * Save image to device gallery
     */
    private void performImageSave() {
        File srcFile = new File(filePath);

        if (!srcFile.exists()) {
            callbackContext.error("Source file does not exist: " + filePath);
            return;
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            // Use MediaStore for Android 14 and above
            saveImageToMediaStore(srcFile);
        } else {
            // Use traditional file copying for Android 13 and below
            saveImageToLegacyGallery(srcFile);
        }
    }

    /**
     * Save image to MediaStore (Android 14+)
     */
    private void saveImageToMediaStore(File srcFile) {
        ContentValues values = new ContentValues();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_" + timeStamp + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YourAppFolder");

        Context context = cordova.getActivity().getApplicationContext();
        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            try (FileInputStream inStream = new FileInputStream(srcFile);
                 OutputStream outStream = context.getContentResolver().openOutputStream(uri)) {

                if (outStream == null) {
                    callbackContext.error("Error opening output stream for URI");
                    return;
                }

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inStream.read(buffer)) > 0) {
                    outStream.write(buffer, 0, bytesRead);
                }

                callbackContext.success(uri.toString());
            } catch (IOException e) {
                callbackContext.error("IOException occurred: " + e.getMessage());
            }
        } else {
            callbackContext.error("Failed to save image to gallery.");
        }
    }

    /**
     * Save image using traditional file system for Android 13 and below
     */
    private void saveImageToLegacyGallery(File srcFile) {
        File dstFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        // Generate image file name using current date and time
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
        File newFile = new File(dstFolder.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");

        try (FileChannel inChannel = new FileInputStream(srcFile).getChannel();
             FileChannel outChannel = new FileOutputStream(newFile).getChannel()) {

            inChannel.transferTo(0, inChannel.size(), outChannel);

            // Update gallery using legacy scan method
            scanPhoto(newFile);

            callbackContext.success(newFile.toString());
        } catch (IOException e) {
            callbackContext.error("Error saving file: " + e.getMessage());
        }
    }

    /**
     * Invoke the system's media scanner to add your photo to the Media Provider's database
     * for older Android versions.
     *
     * @param imageFile The image file to be scanned by the media scanner
     */
    private void scanPhoto(File imageFile) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(imageFile);
        mediaScanIntent.setData(contentUri);
        cordova.getActivity().sendBroadcast(mediaScanIntent);
    }

    /**
     * Callback from PermissionHelper.requestPermission method
     */
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                Log.d("SaveImage", "Permission not granted by the user");
                callbackContext.error("Permissions denied");
                return;
            }
        }

        if (requestCode == WRITE_PERM_REQUEST_CODE) {
            performImageSave();
        }
    }
}
