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
                injectDetectionScript(false);
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

        // Manual Extract Button
        android.widget.Button manualScanBtn = findViewById(R.id.manual_scan_btn);
        manualScanBtn.setOnClickListener(v -> {
            Log.d("MagicBrowser", "Manual Extract Requested");
            Toast.makeText(BrowserActivity.this, "Scanning page...", Toast.LENGTH_SHORT).show();
            injectDetectionScript(true); // Force run
        });
    }

    private void injectDetectionScript(boolean runNow) {
        Log.d("MagicBrowser", "Injecting Smart Script...");
        String js = 
            "(function() {" +
            "   console.log('Smart Script Active! scrapePage() defined.');" +
            "   " +
            "   window.scrapePage = function() {" +
            "       console.log('Scraping Page...');" +
            "       var name = '';" +
            "       var price = '';" +
            "       " +
            "       /* Try finding a container card */" +
            "       /* Page Level Extraction (PDP style) */" +
            "       var titleEl = document.querySelector('#productTitle, #title, h1.product-title-word-break, span#productTitle');" +
            "       if (titleEl) name = titleEl.innerText;" +
            "       " +
            "       var priceEl = document.querySelector('#priceblock_ourprice, #priceblock_dealprice, .a-price .a-offscreen, span.a-price span.a-offscreen, .price, .money');" +
            "       if (priceEl) price = priceEl.innerText || priceEl.textContent;" +
            "       " +
            "       /* Fallback to Meta Tags */" +
            "       if (!name) {" +
            "           var meta = document.querySelector('meta[property=\"og:title\"]');" +
            "           if (meta) name = meta.content;" +
            "       }" +
            "       if (!price) {" +
            "            var metaP = document.querySelector('meta[property=\"product:price:amount\"], meta[property=\"og:price:amount\"]');" +
            "            if (metaP) price = metaP.content;" +
            "       }" +
            "       " +
            "       if (name) {" +
            "           name = name.trim();" +
            "           if(price) price = price.trim();" +
            "           console.log('Found Item: ' + name + ' @ ' + price);" +
            "           if(window.Android) window.Android.onItemSelected(name, price);" +
            "       } else {" +
            "           console.log('No Item Found');" +
            "           if(window.Android) window.Android.onItemSelected('No Item Found', '');" +
            "       }" +
            "   };" +
            "   " +
            "   document.addEventListener('click', function(e) {" +
            "       var target = e.target;" +
            "       var btn = target.closest('button, a, input[type=\"submit\"], input[type=\"button\"]');" +
            "       if (!btn) return;" +
            "       " +
            "       var text = (btn.innerText || btn.value || btn.id || '').toLowerCase();" +
            "       var isAmazonBtn = (btn.id === 'add-to-cart-button' || btn.name === 'submit.add-to-cart');" +
            "       " +
            "       if (isAmazonBtn || text.includes('add') || text.includes('cart') || text.includes('buy') || text.includes('checkout')) {" +
            "           console.log('Shopping Action Detected! Running Scraper...');" +
            "           window.scrapePage();" +
            "       }" +
            "   }, true);" +
            "   " +
            (runNow ? "window.scrapePage();" : "") + 
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

        // SANITIZE DATA
        // QR Code can handle more data, but let's keep it reasonable.
        String safeName = name.replace("|", " ").trim();
        if (safeName.length() > 64) safeName = safeName.substring(0, 64) + "...";
        
        String safePrice = price.replace("|", "").trim();
        
        String data = safeName + "|" + safePrice;

        boolean rotate = false;
        // If portrait mode, swap dimensions to generate a wider (higher resolution) layout, then rotate
        if (targetHeight > targetWidth) {
            int temp = targetWidth;
            targetWidth = targetHeight; // Make it wide (e.g. 250)
            targetHeight = temp;        // Make it short (e.g. 122)
            rotate = true;
        }

        // 1. Generate QR Code Bitmap matched to display
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
            // Use QR Code for robust data capacity
            MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
            
            // Layout: 
            // If Landscape-ish (Width > Height): [Text] [QR]
            // QR Size = Height (e.g. 122)
            // Text Area = Width - Height (e.g. 250 - 122 = 128)
            
            int qrSize = Math.min(targetWidth, targetHeight);
            if (qrSize > 250) qrSize = 250; 
            
            // DEBUG: Force simple content if needed, but let's try to catch the error
            // content = "DEBUG"; 
            
            BitMatrix bitMatrix = multiFormatWriter.encode(content, BarcodeFormat.QR_CODE, qrSize, qrSize);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap qrBitmap = barcodeEncoder.createBitmap(bitMatrix);
            
            Bitmap combined = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(combined);
            canvas.drawColor(Color.WHITE);
            
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(18);
            paint.setAntiAlias(true);
             
            // ROBUST SPLITTING (Fixes IndexOutOfBoundsException)
            String[] parts = content.split("\\|", -1);
            String displayName = parts.length > 0 ? parts[0] : content;
            String displayPrice = parts.length > 1 ? parts[1] : "";
            
            // Draw Layout
            if (targetWidth > targetHeight) {
                // Landscape: Text Left, QR Right
                // Draw QR aligned right
                canvas.drawBitmap(qrBitmap, targetWidth - qrSize, 0, null);
                
                // Draw Text Left
                // Simple wrapping or just 2 lines
                canvas.drawText(displayPrice, 10, 30, paint); // Price Top
                
                // Name (wrap manually roughly)
                paint.setTextSize(14);
                int y = 60;
                int lineWidth = targetWidth - qrSize - 10;
                // crude wrapping
                String[] words = displayName.split(" ");
                String line = "";
                for(String word : words) {
                   if (paint.measureText(line + word) < lineWidth) {
                       line += word + " ";
                   } else {
                       canvas.drawText(line, 10, y, paint);
                       y += 18;
                       line = word + " ";
                   }
                   if (y > targetHeight) break;
                }
                canvas.drawText(line, 10, y, paint);
                
            } else {
               // Portrait: Text Top, QR Bottom
               // Not implemented for now as we enforce Landscape rotation logic
               // Fallback: Just center QR
               int xPos = (targetWidth - qrBitmap.getWidth()) / 2;
               canvas.drawBitmap(qrBitmap, xPos, targetHeight - qrSize, null);
            }

            return combined;
        } catch (Exception e) {
            e.printStackTrace();
            final String err = e.getMessage();
            runOnUiThread(() -> Toast.makeText(BrowserActivity.this, "Enc Err: " + err, Toast.LENGTH_LONG).show());
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
