package com.agomezmoron.saveImageGallery;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Arrays;
import java.util.List;

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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

/**
 * SaveImageGallery.java
 *
 * Extended Android implementation of the Base64ToGallery for iOS.
 * Inspirated by StefanoMagrassi's code
 * https://github.com/Nexxa/cordova-base64-to-gallery
 *
 * @author Alejandro Gomez <agommor@gmail.com>
 */
public class SaveImageGallery extends CordovaPlugin {

    // Consts
    public static final String EMPTY_STR = "";

    public static final String JPG_FORMAT = "JPG";
    public static final String PNG_FORMAT = "PNG";

    // actions constants
    public static final String SAVE_BASE64_ACTION = "saveImageDataToLibrary";
    public static final String REMOVE_IMAGE_ACTION = "removeImageFromLibrary";

    public static final int WRITE_PERM_REQUEST_CODE = 1;
    private final String WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    private JSONArray _args;
    private CallbackContext _callback;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if(action.equals(REMOVE_IMAGE_ACTION)) {
            this.removeImage(args, callbackContext);
        }
        else {
            this._args = args;
            this._callback = callbackContext;

            if (PermissionHelper.hasPermission(this, WRITE_EXTERNAL_STORAGE) || Build.VERSION.SDK_INT >= 30) {
                Log.d("SaveImageGallery", "Permissions already granted, or Android version is 11 or higher");
                saveBase64Image(this._args, this._callback);
            } else {
                Log.d("SaveImageGallery", "Requesting permissions for WRITE_EXTERNAL_STORAGE");
                PermissionHelper.requestPermission(this, WRITE_PERM_REQUEST_CODE, WRITE_EXTERNAL_STORAGE);
            }
        }

        return true;
    }

    /**
     * It deletes an image from the given path.
     */
    private void removeImage(JSONArray args, CallbackContext callbackContext) throws JSONException {
        String filename = args.optString(0);

        // isEmpty() requires API level 9
        if (filename.equals(EMPTY_STR)) {
            callbackContext.error("Missing filename string");
        }

        File file = new File(filename);
        if (file.exists()) {
            try {
                file.delete();
            } catch (Exception ex) {
                callbackContext.error(ex.getMessage());
            }
        }

        callbackContext.success(filename);

    }

    /**
     * It saves a Base64 String into an image.
     */
    private void saveBase64Image(JSONArray args, CallbackContext callbackContext) throws JSONException {
        String base64 = args.optString(0);
        String filePrefix = args.optString(1);
        boolean mediaScannerEnabled = args.optBoolean(2);
        String format = args.optString(3);
        int quality = args.optInt(4);

        List<String> allowedFormats = Arrays.asList(new String[] { JPG_FORMAT, PNG_FORMAT });

        // isEmpty() requires API level 9
        if (base64.equals(EMPTY_STR)) {
            callbackContext.error("Missing base64 string");
        }

        // isEmpty() requires API level 9
        if (format.equals(EMPTY_STR) || !allowedFormats.contains(format.toUpperCase())) {
            format = JPG_FORMAT;
        }

        if (quality <= 0) {
            quality = 100;
        }

        // Create the bitmap from the base64 string
        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
        Bitmap bmp = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

        if (bmp == null) {
            callbackContext.error("The image could not be decoded");

        } else {

            // Save the image
            Uri imageUri = savePhoto(bmp, filePrefix, format, quality);

            if (imageUri == null) {
                callbackContext.error("Error while saving image");
            }

            // Update image gallery
            if (mediaScannerEnabled && imageUri != null) {
                scanPhoto(imageUri);
            }

            callbackContext.success(imageUri.toString());
        }
    }

    /**
     * Private method to save a {@link Bitmap} into the photo library/temp folder with a format, a prefix and with the given quality.
     */
    private Uri savePhoto(Bitmap bmp, String prefix, String format, int quality) {
        OutputStream outStream = null;
        Uri imageUri = null;
        try {
            Calendar c = Calendar.getInstance();
            String date = EMPTY_STR + c.get(Calendar.YEAR) + c.get(Calendar.MONTH) + c.get(Calendar.DAY_OF_MONTH)
                    + c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND);

            String fileName = prefix + date;
            Bitmap.CompressFormat compressFormat = null;
            String mimeType = "";
            // switch for String is not valid for java < 1.6, so we avoid it
            if (format.equalsIgnoreCase(JPG_FORMAT)) {
                fileName += ".jpeg";
                compressFormat = Bitmap.CompressFormat.JPEG;
                mimeType = "image/jpeg";
            } else if (format.equalsIgnoreCase(PNG_FORMAT)) {
                fileName += ".png";
                compressFormat = Bitmap.CompressFormat.PNG;
                mimeType = "image/png";
            } else {
                // default case
                fileName += ".jpeg";
                compressFormat = Bitmap.CompressFormat.JPEG;
                mimeType = "image/jpeg";
            }

            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

                imageUri = cordova.getActivity().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (imageUri != null) {
                    outStream = cordova.getActivity().getContentResolver().openOutputStream(imageUri);
                    bmp.compress(compressFormat, quality, outStream);
                    outStream.close();
                }
            } else {
                File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                if (!folder.exists()) {
                    folder.mkdirs();
                }

                File imageFile = new File(folder, fileName);
                outStream = new FileOutputStream(imageFile);
                bmp.compress(compressFormat, quality, outStream);
                outStream.flush();
                outStream.close();

                imageUri = Uri.fromFile(imageFile);
            }

        } catch (Exception e) {
            Log.e("SaveImageToGallery", "An exception occurred while saving image: " + e.toString());
        } finally {
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e) {
                    Log.e("SaveImageToGallery", "Error closing OutputStream: " + e.toString());
                }
            }
        }

        return imageUri;
    }

    /**
     * Invoke the system's media scanner to add your photo to the Media Provider's database,
     * making it available in the Android Gallery application and to other apps.
     */
    private void scanPhoto(Uri imageUri) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(imageUri);
        cordova.getActivity().sendBroadcast(mediaScanIntent);
    }

    /**
     * Callback from PermissionHelper.requestPermission method
     */
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                Log.d("SaveImageGallery", "Permission not granted by the user");
                _callback.error("Permissions denied");
                return;
            }
        }

        switch (requestCode) {
        case WRITE_PERM_REQUEST_CODE:
            Log.d("SaveImageGallery", "User granted the permission for WRITE_EXTERNAL_STORAGE");
            saveBase64Image(this._args, this._callback);
            break;
        }
    }
}
