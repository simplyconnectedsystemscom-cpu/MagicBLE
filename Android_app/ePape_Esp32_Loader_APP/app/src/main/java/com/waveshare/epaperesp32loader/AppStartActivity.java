package com.waveshare.epaperesp32loader;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
//import android.icu.text.StringPrepParseException;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;


import com.canhub.cropper.CropImage;
import com.canhub.cropper.CropImageView;


import com.waveshare.epaperesp32loader.communication.PermissionHelper;
import com.waveshare.epaperesp32loader.image_processing.EPaperDisplay;

/*
    * <h1>Main activity</h1>
    * The main activity. It leads the steps of e-Paper image uploading:
    * 1. Open Wi-fi or Bluetooth adapter
    * 2. Select the image file
    * 3. Select the type of the display
    * 4. Select the method of pixel format converting
    * 5. Start uploading
    *
    * @author  Waveshare team
    * @version 1.0
    * @since   8/16/2018
*/

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AppStartActivity extends AppCompatActivity
{
    // Request codes
    //-----------------------------
    public static final int REQ_BLUETOOTH_CONNECTION = 2;
    public static final int REQ_OPEN_FILE            = 3;
    public static final int REQ_DISPLAY_SELECTION    = 4;
    public static final int REQ_PALETTE_SELECTION    = 5;
    public static final int REQ_UPLOADING            = 6;

    // Image file name and path
    //-----------------------------
    public static String fileName;
    public static String filePath;

    // Views
    //-----------------------------
    public TextView textBlue;
    public TextView textLoad;
    public TextView textDisp;
    public TextView textFilt;
    public TextView textSend;
//    public TextView textAddr;
    public Button button_file;

    public ImageView pictFile; // View of loaded image
    public ImageView pictFilt; // View of filtered image
    Log log ;
    // Data
    //-----------------------------
    public static Bitmap originalImage; // Loaded image with original pixel format
    public static Bitmap indTableImage; // Filtered image with indexed colors

    // Device
    //-----------------------------
    public static BluetoothDevice btDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_start_activity);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("MAGIC TRICK APP - VERIFIED");
        }

        // Initialize state only if fresh start
        if (fileName == null) fileName = null;
        if (filePath == null) filePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        
        // Views
        textBlue = findViewById(R.id.text_blue);
        textLoad = findViewById(R.id.text_file);
        textDisp = findViewById(R.id.text_disp);
        textFilt = findViewById(R.id.text_filt);
        textSend = findViewById(R.id.text_send);

        pictFile = findViewById(R.id.pict_file);
        pictFilt = findViewById(R.id.pict_filt);
        button_file = findViewById(R.id.Button_file);
        
        // Restore UI state from static variables or SharedPreferences
        if (btDevice == null) {
            String savedAddr = getPreferences(MODE_PRIVATE).getString("BT_ADDR", null);
            if (savedAddr != null) {
                try {
                    android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                    if (adapter != null && adapter.isEnabled()) {
                        btDevice = adapter.getRemoteDevice(savedAddr);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (btDevice != null) {
            textBlue.setText(btDevice.getName() + " (" + btDevice.getAddress() + ")");
        }
        
        // Load saved display index if not already set (e.g. static memory)
        if (EPaperDisplay.epdInd == -1) {
            EPaperDisplay.epdInd = getPreferences(MODE_PRIVATE).getInt("EPD_IND", -1);
        }
        
        if (EPaperDisplay.epdInd != -1) {
            textDisp.setText(EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].title);
            button_file.setEnabled(true);
        } else {
            button_file.setEnabled(false);
        }

    }

    public void onScan(View view)
    {
        // Open bluetooth devices scanning activity
        //-----------------------------------------------------
        startActivityForResult(
            new Intent(this, ScanningActivity.class),
            REQ_BLUETOOTH_CONNECTION);
    }
    
    public void onBrowser(View view) {
        Intent intent = new Intent(this, BrowserActivity.class);
        if (btDevice != null) {
            intent.putExtra("BT_DEVICE", btDevice);
        }
        // Pass the selected display type to ensure persistence
        intent.putExtra("EPD_IND", EPaperDisplay.epdInd);
        startActivity(intent);
    }

    public void onLoad(View view)
    {
        // Use System File Picker (works on Android 10+ Scoped Storage)
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQ_OPEN_FILE);
    }

    public void onDisplay(View view)
    {
        // Open display selection activity
        //-----------------------------------------------------
        startActivityForResult(
                new Intent(this, DisplaysActivity.class),
                REQ_DISPLAY_SELECTION);
    }

    public void onFilter(View view)
    {
        // Check if any image is loaded
        //-----------------------------------------------------
        if (originalImage == null) PermissionHelper.note(this, R.string.no_pict);

        // Check if any display is selected
        //-----------------------------------------------------
        else if (EPaperDisplay.epdInd == -1) PermissionHelper.note(this, R.string.no_disp);

        // Open palette selection activity
        //-----------------------------------------------------
        else startActivityForResult(
                new Intent(this, FilteringActivity.class),
                REQ_PALETTE_SELECTION);
    }

    public void onUpload(View view)
    {
        // Check if any devices is found
        //-----------------------------------------------------
        if (btDevice == null) PermissionHelper.note(this, R.string.no_blue);

        // Check if any palette is selected
        //-----------------------------------------------------
        else if (indTableImage == null) PermissionHelper.note(this, R.string.no_filt);

        // Open uploading activity
        //-----------------------------------------------------
        else startActivityForResult(
            new Intent(this, UploadActivity.class),
            REQ_UPLOADING);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        //-----------------------------------------------------
        //  Messages form ScanningActivity
        //-----------------------------------------------------
        if (requestCode == REQ_BLUETOOTH_CONNECTION)
        {
            // Bluetooth device was found and selected
            //-------------------------------------------------
            if (resultCode == RESULT_OK)
            {
                // Get selected bluetooth device
                //---------------------------------------------
                btDevice = data.getParcelableExtra("DEVICE");

                // Show name and address of the device
                //---------------------------------------------
                textBlue.setText(btDevice.getName() + " (" + btDevice.getAddress() + ")");
                
                // Save selection
                getPreferences(MODE_PRIVATE).edit().putString("BT_ADDR", btDevice.getAddress()).apply();
            }
        }

        //-----------------------------------------------------
        //  Message form open file activity (System Picker)
        //-----------------------------------------------------
        else if (requestCode == REQ_OPEN_FILE)
        {
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
            {
                // Getting file Uri
                //---------------------------------------------
                Uri uri = data.getData();
                String name = uri.getLastPathSegment(); // Simple name extraction
                textLoad.setText(name);

                try
                {
                    // Loading of the selected file via ContentResolver
                    //---------------------------------------------
                    InputStream is = getContentResolver().openInputStream(uri);
                    originalImage  = (new BitmapDrawable(is)).getBitmap();

                    int pictSize = textLoad.getWidth();
                    pictFile.setMaxHeight(pictSize);
                    pictFile.setMinimumHeight(pictSize / 2);
                    pictFile.setImageBitmap(originalImage);
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                    textFilt.setText(R.string.failed_file);
                }
            }
        }

        //-----------------------------------------------------
        //  Message form display selection activity
        //-----------------------------------------------------
        else if (requestCode == REQ_DISPLAY_SELECTION)
        {
            if (resultCode == RESULT_OK) {
                textDisp.setText(EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].title);
                button_file.setEnabled(true);
                
                // Save selection
                getPreferences(MODE_PRIVATE).edit().putInt("EPD_IND", EPaperDisplay.epdInd).apply();
            }
        }

        //-----------------------------------------------------
        //  Message form palette selection activity
        //-----------------------------------------------------
        else if (requestCode == REQ_PALETTE_SELECTION)
        {
            if (resultCode == RESULT_OK)
            {
                textFilt.setText(data.getStringExtra("NAME"));

                try
                {
                    int size = textLoad.getWidth();
                    pictFilt.setMaxHeight(size);
                    pictFilt.setMinimumHeight(size / 2);
                    pictFilt.setImageBitmap(indTableImage);
                }
                catch(Exception e)
                {
                    textFilt.setText(R.string.failed_filt);
                }
            }
        }
        /*
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            // ... (legacy code removed) ...
        }
        */
    }
    public  Bitmap bmp_raw;
}

