package com.waveshare.epaperesp32loader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.waveshare.epaperesp32loader.communication.BluetoothHelper;
import com.waveshare.epaperesp32loader.image_processing.EPaperDisplay;

public class BackgroundUploader extends Handler {
    private Context context;
    
    // Uploaded data buffer
    private static final int BUFF_SIZE = 128;
    private static byte[]    buffArr = new byte[BUFF_SIZE];
    private static int       buffInd;
    private static int       xLine;
    
    private int   pxInd; // Pixel index in picture
    private int   stInd; // Stage index of uploading
    private int   dSize; // Size of uploaded data by LOAD command
    private int[] array; // Values of picture pixels

    public BackgroundUploader(Context context) {
        this.context = context;
    }

    public void startUpload(Bitmap bmp) {
        // Initialize BT if needed
        BluetoothHelper.initialize(AppStartActivity.btDevice, this);
        
        if (!BluetoothHelper.connect()) {
            Toast.makeText(context, "BT Connection Failed", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Stabilize Connection
        android.os.SystemClock.sleep(500);
        
        // Start Protocol
        if(init(bmp)) {
            Toast.makeText(context, "Uploading to E-Paper...", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(context, "Upload Init Failed", Toast.LENGTH_SHORT).show();
        }
    }

    // Converts picture pixels into selected pixel format
    // and sends EPDx command
    public boolean init(Bitmap bmp) {
        int w = bmp.getWidth(); // Picture with
        int h = bmp.getHeight();// Picture height
        int epdInd = EPaperDisplay.epdInd;
        array = new int[w*h]; // Array of pixels
        int i = 0;            // Index of pixel in the array of pixels

        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++, i++)
                if(epdInd == 25 || epdInd ==37)
                    array[i] = getVal_7color(bmp.getPixel(x, y));
                else
                    array[i] = getVal(bmp.getPixel(x, y));

        pxInd = 0;
        xLine = 0;  //2.13inch
        stInd = 0;
        dSize = 0;

        buffInd = 2;                             // Size of command in bytes
        buffArr[0] = (byte)'I';                  // Name of command (Initialize)
        buffArr[1] = (byte)EPaperDisplay.epdInd; // Index of display
        
        Log.d("MagicUpload", "Sent Init (" + EPaperDisplay.epdInd + ")... Wait");

        // Timeout Watchdog
        final int currentStage = stInd;
        postDelayed(() -> {
            if (stInd == currentStage && stInd == 0) {
                 Log.e("MagicUpload", "Timeout waiting for init response");
                 // Don't toast here to avoid spam, maybe logic stuck
            }
        }, 8000);

        return u_send(false);
    }
    
    // ... Copy logic from SocketHandler ...
    private boolean handleUploadingStage() {
        int epdInd = EPaperDisplay.epdInd;

        // 2.13 e-Paper display
        if ((epdInd == 3) || (epdInd == 39) || (epdInd == 43)) {
            if(stInd == 0) return u_line(0, 0, 100);
            if(stInd == 1) return u_show();
        }
        // 2.13 b V4
        else if ((epdInd == 40)) {
            if(stInd == 0) return u_line(0, 0, 50);
            if(stInd == 1) return u_next();
            if(stInd == 2) return u_line(3, 50, 50);
            if(stInd == 3) return u_show();
        }
        // White-black e-Paper displays
        else if ((epdInd==0)||(epdInd==6)||(epdInd==7)||(epdInd==9)||(epdInd==12)||
                (epdInd==16)||(epdInd==19)||(epdInd==22)||(epdInd==26)||(epdInd==27)||(epdInd==28)) {
            if(stInd == 0) return u_data(0,0,100);
            if(stInd == 1) return u_show();
        }
        // 7.5 colored e-Paper displays
        else if (epdInd>15 && epdInd < 22) {
            if(stInd == 0) return u_data(-1,0,100);
            if(stInd == 1) return u_show();
        }
        // 5.65f colored e-Paper displays
        else if (epdInd == 25 || epdInd == 37) {
            if(stInd == 0) return u_data(-2,0,100);
            if(stInd == 1) return u_show();
        }
        // Other colored e-Paper displays
        else {
            if(stInd == 0)return u_data((epdInd == 1)? -1 : 0,0,50);
            if(stInd == 1)return u_next();
            if(stInd == 2)return u_data(3,50,50);
            if(stInd == 3)return u_show();
        }
        return true;
    }

    public int getVal(int color) {
        int r = Color.red(color);
        int b = Color.blue(color);
        if((r == 0xFF) && (b == 0xFF)) return 1;
        if((r == 0x7F) && (b == 0x7F)) return 2;
        if((r == 0xFF) && (b == 0x00)) return 3;
        return 0;
    }

    public int getVal_7color(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        if((r == 0x00) && (g == 0x00) && (b == 0x00)) return 0;
        if((r == 0xFF) && (g == 0xFF) && (b == 0xFF)) return 1;
        if((r == 0x00) && (g == 0xFF) && (b == 0x00)) return 2;
        if((r == 0x00) && (g == 0x00) && (b == 0xFF)) return 3;
        if((r == 0xFF) && (g == 0x00) && (b == 0x00)) return 4;
        if((r == 0xFF) && (g == 0xFF) && (b == 0x00)) return 5;
        if((r == 0xFF) && (g == 0x80) && (b == 0x00)) return 6;
        return 7;
    }

    private boolean u_send(boolean next) {
        if (!BluetoothHelper.btThread.write(buffArr, buffInd))
            return false;
        if(next) stInd++;
        return true;
    }

    private boolean u_next() {
        buffInd = 1;
        buffArr[0] = (byte)'N';
        pxInd = 0;
        return u_send(true);
    }

    private boolean u_show() {
        buffInd = 1;
        buffArr[0] = (byte)'S';
        boolean success = u_send(true);
        if (success) {
            // FINISHED!
            Log.d("MagicUpload", "Upload Complete!");
            // Close connection after upload to save battery? Or keep open?
            // UploadActivity keeps it open until Destroy.
            // But we might want to close it or keep it for next time.
            // If we close, we must re-connect next time.
            BluetoothHelper.close();
            
            // Show Success Toast
            post(() -> Toast.makeText(context, "E-Paper Updated! ✨", Toast.LENGTH_SHORT).show());
        }
        return success;
    }

    private boolean u_load(int k1, int k2) {
        // Log Progress
        int progress = (k1 + k2*pxInd/array.length);
        if (progress % 10 == 0) { // Log every 10% to avoid spam
             Log.d("MagicUpload", "Progress: " + progress + "%");
        }
        
        dSize += buffInd;
        buffArr[0] = (byte)'L';
        buffArr[1] = (byte)(buffInd     );
        buffArr[2] = (byte)(buffInd >> 8);
        buffArr[3] = (byte)(dSize      );
        buffArr[4] = (byte)(dSize >>  8);
        buffArr[5] = (byte)(dSize >> 16);
        return u_send(pxInd >= array.length);
    }

    private boolean u_data(int c, int k1, int k2) {
        buffInd = 6; 
        if(c == -1) {
            while ((pxInd < array.length) && (buffInd + 1 < BUFF_SIZE)) {
                int v = 0;
                for(int i = 0; i < 16; i += 2) {
                    if (pxInd < array.length) v |= (array[pxInd] << i);
                    pxInd++;
                }
                buffArr[buffInd++] = (byte)(v     );
                buffArr[buffInd++] = (byte)(v >> 8);
            }
        } else if(c == -2) {
            while ((pxInd < array.length) && (buffInd + 1 < BUFF_SIZE)) {
                int v = 0;
                for(int i = 0; i < 16; i += 4) {
                    if (pxInd < array.length) v |= (array[pxInd] << i);
                    pxInd++;
                }
                buffArr[buffInd++] = (byte)(v     );
                buffArr[buffInd++] = (byte)(v >> 8);
            }
        } else {
            while ((pxInd < array.length) && (buffInd < BUFF_SIZE)) {
                int v = 0;
                for (int i = 0; i < 8; i++) {
                    if ((pxInd < array.length) && (array[pxInd] != c)) v |= (128 >> i);
                    pxInd++;
                }
                buffArr[buffInd++] = (byte)v;
            }
        }
        return u_load(k1, k2);
    }

    private boolean u_line(int c, int k1, int k2) {
        buffInd = 6; 
        while ((pxInd < array.length) && (buffInd < BUFF_SIZE)) {
            int v = 0;
            for (int i = 0; (i < 8) && (xLine < 122); i++, xLine++){
                if (array[pxInd++] != c) v |= (128 >> i);
            }
            if(xLine >= 122 )xLine = 0;
            buffArr[buffInd++] = (byte)v;
        }
        return u_load(k1, k2);
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg.what == BluetoothHelper.BT_FATAL_ERROR) {
             Toast.makeText(context, "BT Error: Fatal", Toast.LENGTH_SHORT).show();
             BluetoothHelper.close();
        }
        else if (msg.what == BluetoothHelper.BT_RECEIVE_DATA) {
            String line = new String((byte[]) msg.obj, 0, msg.arg1);
            if (line.contains("Ok!")) {
                 if (handleUploadingStage()) return;
            }
            else if (!line.contains("Error!")) return;
            
            // Retry logic
            Log.w("MagicUpload", "Retrying Upload (Bad Response: " + line + ")");
            BluetoothHelper.close();
            BluetoothHelper.connect();
            
            // Stabilize Retry
            android.os.SystemClock.sleep(500);
            
            init(AppStartActivity.indTableImage);
        }
    }
}
