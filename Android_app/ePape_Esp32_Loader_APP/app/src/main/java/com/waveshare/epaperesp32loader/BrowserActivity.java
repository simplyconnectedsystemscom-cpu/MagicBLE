package com.waveshare.epaperesp32loader;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.waveshare.epaperesp32loader.image_processing.EPaperDisplay;
import com.waveshare.epaperesp32loader.image_processing.EPaperPicture;

public class BrowserActivity extends AppCompatActivity {
    private WebView webView;
    private EditText urlBar;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        
        // Recover Bluetooth from Intent
        BluetoothDevice intentDevice = getIntent().getParcelableExtra("BT_DEVICE");
        if (intentDevice != null) {
            AppStartActivity.btDevice = intentDevice;
        }

        urlBar = findViewById(R.id.url_bar);
        webView = findViewById(R.id.webview);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Magic Browser v2.5 - Ready");
        }

        // Configure WebView settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        
        // Ensure links open in the WebView, not external browser
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d("MagicBrowser", "Page Loaded: " + url);
                injectDetectionScript();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("MagicBrowser", "JS: " + consoleMessage.message());
                return true;
            }
        });

        // Add the Javascript Interface for "Recognition"
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // Load the local test shopping cart
        webView.loadUrl("file:///android_asset/shopping_test.html");
        
        // Proper Status Update
        if (AppStartActivity.btDevice != null) {
             // Toast.makeText(this, "Connected: " + AppStartActivity.btDevice.getName(), Toast.LENGTH_SHORT).show();
             // Silent success
        } else {
             Toast.makeText(this, "Wait! Bluetooth Disconnected!", Toast.LENGTH_LONG).show();
        }

        // URL Bar Logic
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                
                String url = urlBar.getText().toString();
                if (!url.startsWith("http") && !url.startsWith("file:")) {
                    url = "https://" + url;
                }
                webView.loadUrl(url);
                return true;
            }
            return false;
        });
    }

    private void injectDetectionScript() {
        Log.d("MagicBrowser", "Injecting Smart Script...");
        String js = 
            "(function() {" +
            "   console.log('Smart Script Active!');" +
            "   document.addEventListener('click', function(e) {" +
            "       console.log('Click detected on: ' + e.target.tagName);" +
            "       var target = e.target;" +
            "       var btn = target.closest('button') || (target.tagName === 'BUTTON' ? target : null);" +
            "       if (!btn && target.tagName === 'INPUT' && target.type === 'submit') btn = target;" +
            "       if (!btn) return;" +
            "       var text = (btn.innerText || btn.value || '').toLowerCase();" +
            "       console.log('Button text: ' + text);" +
            "       if (text.includes('add') || text.includes('cart') || text.includes('buy') || text.includes('select')) {" +
            "           console.log('Shopping Action Detected!');" +
            "           var card = btn.closest('.product-card, .item, .product');" +
            "           if (card) {" +
            "               var nameEl = card.querySelector('.product-title, .title, h1, h2, h3, .name');" +
            "               var priceEl = card.querySelector('.product-price, .price, .cost');" +
            "               var name = nameEl ? nameEl.innerText : 'Unknown Item';" +
            "               var price = priceEl ? priceEl.innerText : 'Unknown Price';" +
            "               console.log('Found Item: ' + name + ' @ ' + price);" +
            "               if(window.Android) window.Android.onItemSelected(name, price);" +
            "           } else {" +
            "             console.log('No Pattern Match, trying meta tags...');" +
            "               var metaTitle = document.querySelector('meta[property=\"og:title\"]');" +
            "               var metaPrice = document.querySelector('meta[property=\"product:price:amount\"]');" +
            "               if (metaTitle && window.Android) {" +
            "                   window.Android.onItemSelected(metaTitle.content, metaPrice ? metaPrice.content : 'On Request');" +
            "               }" +
            "           }" +
            "       }" +
            "   }, true);" +
            "})();";
            
        webView.evaluateJavascript(js, null);
    }

    /**
     * Interface for JavaScript to communicate with Android.
     */
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        /**
         * Called when an item is selected in the web view.
         * This is the "Recognition" step.
         */
        @JavascriptInterface
        public void onItemSelected(String itemName, String itemPrice) {
            // Run on UI thread to show Toast or update UI
            runOnUiThread(() -> {
                String message = "Recognized: " + itemName;
                Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
                
                // Trigger the Magic Send Sequence!
                processAndSendToEpaper(itemName, itemPrice);
            });
        }
    }
    
    private void processAndSendToEpaper(String name, String price) {
        // 0. Pre-Flight Checks
        if (AppStartActivity.btDevice == null) {
            Toast.makeText(this, "Error: Connect Bluetooth in Main Menu first!", Toast.LENGTH_LONG).show();
            return;
        }

        if (EPaperDisplay.epdInd == -1) {
            Toast.makeText(this, "Error: Select Display Type in Main Menu first!", Toast.LENGTH_LONG).show();
            return;
        }

        // Get selected display dimensions
        EPaperDisplay selectedDisplay = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd];
        int targetWidth = selectedDisplay.width;
        int targetHeight = selectedDisplay.height;
        String data = name + "|" + price;

        boolean rotate = false;
        // If portrait mode, swap dimensions to generate a wider (higher resolution) barcode, then rotate
        if (targetHeight > targetWidth) {
            int temp = targetWidth;
            targetWidth = targetHeight; // Make it wide
            targetHeight = temp;
            rotate = true;
        }

        // 1. Generate Barcode Bitmap matched to display
        Bitmap barcodeInfo = createBarcodeImage(data, targetWidth, targetHeight);

        if (barcodeInfo == null) {
             Toast.makeText(this, "Barcode gen failed", Toast.LENGTH_SHORT).show();
             return;
        }

        if (rotate) {
             Matrix matrix = new Matrix();
             matrix.postRotate(90);
             barcodeInfo = Bitmap.createBitmap(barcodeInfo, 0, 0, barcodeInfo.getWidth(), barcodeInfo.getHeight(), matrix, true);
        }

        // 2. Prepare Global State for Upload
        AppStartActivity.originalImage = barcodeInfo;

        // 3. Convert to e-Paper Format
        try {
            AppStartActivity.indTableImage = EPaperPicture.createIndexedImage(false, false);

            // 4. Launch Upload Activity
            Intent intent = new Intent(BrowserActivity.this, UploadActivity.class);
            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(this, "Image Process Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private Bitmap createBarcodeImage(String content, int targetWidth, int targetHeight) {
        try {
            // Determine orientation (Portrait vs Landscape)
            // Most e-paper raw buffers are landscape? Or Portrait? 
            // The library seems to handle rotation, but let's generate a fit image.
            
            // Let's assume we want to draw into the exact buffer size.
            
            MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
            // Create barcode slightly smaller than width
            int bmWidth = (int)(targetWidth * 0.8);
            if (bmWidth < 100) bmWidth = 100;
            
            BitMatrix bitMatrix = multiFormatWriter.encode(content, BarcodeFormat.CODE_128, bmWidth, 80);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap barcodeBitmap = barcodeEncoder.createBitmap(bitMatrix);
            
            Bitmap combined = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(combined);
            canvas.drawColor(Color.WHITE);
            
            // Draw Text
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(20);
            paint.setAntiAlias(true);
             
            // Simplest approach: Text on Top, Barcode on Bottom
            String displayPrice = content.contains("|") ? content.split("\\|")[1] : "";
            String displayName = content.contains("|") ? content.split("\\|")[0] : content;
            if(displayName.length() > 20) displayName = displayName.substring(0, 18) + "..";
            
            // Center text attempt
            float textWidth = paint.measureText(displayName);
            canvas.drawText(displayName, (targetWidth - textWidth) / 2, 25, paint);
            
            float priceWidth = paint.measureText(displayPrice);
            canvas.drawText(displayPrice, (targetWidth - priceWidth) / 2, 50, paint);
             
             // Draw Barcode centered
             int xPos = (targetWidth - barcodeBitmap.getWidth()) / 2;
             if (xPos < 0) xPos = 0;
             canvas.drawBitmap(barcodeBitmap, xPos, 60, null);
             
             // Check if rotation is needed based on display Index?
             // Actually, the original 'Upload' logic might expect the image in a specific orientation
             // The original app allows rotating? No, it just crops.
             // We will send it as is. If it's sideways, we can fix closer to final.
             
            return combined;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
