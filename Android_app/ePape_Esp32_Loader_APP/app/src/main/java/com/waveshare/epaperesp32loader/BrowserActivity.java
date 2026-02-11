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
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
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
    private ProgressBar progressBar;
    private String lastUploadedName = "";
    private long lastUploadTime = 0;
    
    /* Magic Trick Constants */
    private static final String PATTER_PHONE = "234-635-1121"; // User to Update
    private static final String CLOUD_BACKEND_URL = "https://magic-ble-backend.vercel.app/api/item";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        
        // Restore State from Intent
        int ind = getIntent().getIntExtra("EPD_IND", -1);
        if (ind != -1) {
            EPaperDisplay.epdInd = ind;
        }
        
        // Recover Bluetooth
        BluetoothDevice intentDevice = getIntent().getParcelableExtra("BT_DEVICE");
        if (intentDevice != null) {
            AppStartActivity.btDevice = intentDevice;
        }

        // Initialize Views
        urlBar = findViewById(R.id.url_bar);
        progressBar = findViewById(R.id.web_progress);
        webView = findViewById(R.id.webview);
        ImageButton magicBtn = findViewById(R.id.manual_scan_btn);
        ImageButton navBack = findViewById(R.id.nav_back);
        ImageButton navFwd = findViewById(R.id.nav_fwd);
        ImageButton navReload = findViewById(R.id.nav_reload);
        ImageButton navHome = findViewById(R.id.nav_home);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide(); 
        }

        // WebView Settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // WebView Client
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d("MagicBrowser", "Page Loaded: " + url);
                
                // Don't show internal asset URLs in the bar
                if (url != null && url.startsWith("file:///android_asset/")) {
                    urlBar.setText("");
                } else {
                    urlBar.setText(url);
                }
                
                injectDetectionScript(false);
            }
        });

        // WebChrome Client
        webView.setWebChromeClient(new WebChromeClient() {
             @Override
             public void onProgressChanged(WebView view, int newProgress) {
                 if (newProgress == 100) {
                     progressBar.setVisibility(View.INVISIBLE);
                 } else {
                     progressBar.setVisibility(View.VISIBLE);
                     progressBar.setProgress(newProgress);
                 }
             }
             @Override
             public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                 Log.d("MagicBrowser", "JS: " + consoleMessage.message());
                 return true;
             }
        });

        // URL Bar
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                
                String url = urlBar.getText().toString();
                if (!url.startsWith("http") && !url.startsWith("file:")) {
                    url = "https://" + url;
                }
                webView.loadUrl(url);
                
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
                
                return true;
            }
            return false;
        });

        // Magic Btn
        magicBtn.setOnClickListener(v -> {
            Log.d("MagicBrowser", "Magic Wand Activated");
            Toast.makeText(BrowserActivity.this, "Scanning page...", Toast.LENGTH_SHORT).show();
            injectDetectionScript(true);
        });

        // Navigation
        navBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        navFwd.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        navReload.setOnClickListener(v -> webView.reload());
        navHome.setOnClickListener(v -> webView.loadUrl("https://www.wawa.com"));

        // Load Default
        webView.loadUrl("https://www.wawa.com");
    }

    private void injectDetectionScript(boolean manual) {
        Log.d("MagicBrowser", "Injecting Smart Script (Manual: " + manual + ")...");
        String js = 
            "(function() {" +
            "   console.log('Smart Script Active! scrapePage() defined.');" +
            "   var retryTimer = null;" +
            "   " +
            "   window.scrapePage = function(isManual) {" +
            "       console.log('Scraping Page (Manual: ' + isManual + ')...');" +
            "       var name = '';" +
            "       var price = '';" +
            "       " +
            "       /* Try finding a container card */" +
            "       /* Page Level Extraction (PDP style) */" +
            "       /* Added Best Buy selectors (.sku-title h1, .heading-5, .priceView-hero-price span) */" +
            "       /* Added Target selectors ([data-test=\"product-title\"]) */" +
            "       /* Added Walmart, eBay, Etsy, Shopify selectors */" +
            "       /* 1. Extract Name (Title) */" +
            "       var name = '';" +
            "       /* Strategy 1: Document Title (Cleaned) */" +
            "       var docTitle = document.title;" +
            "       if (docTitle) {" +
            "           /* Remove known suffixes for Wawa and others */" +
            "           docTitle = docTitle.replace(/\\s*[-|]\\s*(Wawa|Order Online|Menu|Official Site).*/i, '').trim();" +
            "           /* Check for generic titles */" +
            "           if (docTitle.length > 2 && !/^(Menu|Order|Customize|Start|Home|Welcome|Start Your Order)$/i.test(docTitle)) {" +
            "               name = docTitle;" +
            "           }" +
            "       }" +
            "       " +
            "       /* Strategy 2: Wawa URL Extraction (Fallback) */" +
            "       if ((!name || name.length < 3) && window.location.host.includes('wawa') && window.location.href.includes('/menu/')) {" +
            "           var path = window.location.pathname;" +
            "           var segments = path.split('/').filter(function(s){return s.length>0;});" +
            "           var menuIndex = segments.indexOf('menu');" +
            "           if (menuIndex >= 0 && menuIndex + 1 < segments.length) {" +
            "               var slug = segments[menuIndex + 1];" +
            "               name = slug.replace(/-/g, ' ').replace(/\\b\\w/g, function(l){return l.toUpperCase();});" +
            "           } else if (segments.length > 0) {" +
            "               var last = segments[segments.length-1];" +
            "               if (/[a-zA-Z]/.test(last)) {" +
            "                   name = last.replace(/-/g, ' ').replace(/\\b\\w/g, function(l){return l.toUpperCase();});" +
            "               }" +
            "           }" +
            "       }" +
            "       " +
            "       /* DOM Fallback */" +
            "       if (!name) {" +
            "           var titleEl = document.querySelector('.modal-title, .dialog-title, .popup-title, .overlay-title, [role=\"dialog\"] h1, [role=\"dialog\"] h2, h1, h2, h3, h4, [role=\"heading\"], [aria-level], .product-name, .item-name, [class*=\"product-title\"], [class*=\"item-title\"], #productTitle, #title, h1.product-title-word-break, span#productTitle, .sku-title h1, .product-title, .heading-5, [data-test=\"product-title\"], h1#main-title, [itemprop=\"name\"], .x-item-title__mainTitle, [data-buy-box-listing-title], h1.product-single__title, .product_title, h1.product-details__title, h1.od-product-title, h1.page-title span, .productView-title, h1.product-title, .ProductItem-details-title');" +
            "           if (titleEl) name = titleEl.innerText;" +
            "       }" +
            "       /* Fallback: Extract Name from URL Slug (e.g. wawa.com/.../5-big-breakfast-deal) */" +
            "       if (!name) {" +
            "           var path = window.location.pathname;" +
            "           var segments = path.split('/').filter(function(s){return s.length>0;});" +
            "           if (segments.length > 0) {" +
            "               var last = segments[segments.length-1];" +
            "               if (/[a-zA-Z]/.test(last)) {" +
            "                   name = last.replace(/-/g, ' ').replace(/\\b\\w/g, function(l){return l.toUpperCase();});" +
            "                   name = name.replace(/\\.(html|php|aspx)$/, '');" +
            "               }" +
            "           }" +
            "       }" +
            "       " +
            "       var priceEl = document.querySelector('#priceblock_ourprice, #priceblock_dealprice, .a-price .a-offscreen, span.a-price span.a-offscreen, .price, .money, .priceView-hero-price span, .large-price, .price-block .price, [data-test=\"product-price\"], [itemprop=\"price\"], .x-price-primary, .wt-text-title-03, .product-price, .price-item--regular, .woocommerce-Price-amount, .price__format, .od-price-value, .product-info-price .price, .price--withTax, .retail-price, .sale-price, .current-price span.price, .sqs-money-native');" +
            "       if (priceEl) price = priceEl.getAttribute('content') || priceEl.innerText || priceEl.textContent;" +
            "       " +
            "       /* Fallback to Meta Tags */" +
            "       if (!name) {" +
            "           var meta = document.querySelector('meta[property=\"og:title\"]');" +
            "           if (meta) name = meta.content;" +
            "       }" +
            "       if (!price) {" +
            "            var metaP = document.querySelector('meta[property=\"product:price:amount\"], meta[property=\"og:price:amount\"], meta[itemprop=\"price\"]');" +
            "            if (metaP) price = metaP.content;" +
            "       }" +
            "       " +
            "       /* Check for Open Graph Product Type OR URL Signal */" +
            "       var ogType = document.querySelector('meta[property=\"og:type\"]');" +
            "       var isProduct = (ogType && ogType.content === 'product') || window.location.href.includes('/p/') || window.location.href.includes('/products/') || window.location.href.includes('/product/') || window.location.href.includes('/ip/') || window.location.href.includes('/dp/') || (window.location.host.includes('wawa') && window.location.href.includes('/menu/'));" +
            "       " +
            "       /* VALIDATE PRODUCT PAGE: Must have 'Add to Cart' button (to avoid Home Page false positives) */" +
            "       var buyBtn = document.querySelector('#add-to-cart-button, .add-to-cart, [name=\"add\"], .btn-add-to-cart, [data-test=\"shipItButton\"], [data-test=\"add-to-cart\"], .x-atc-action, .single_add_to_cart_button, .product-form__cart-submit, .add-to-cart-button, [data-sku-id]');" +
            "       if (!buyBtn) {" +
            "           /* Try text search for 'Add to Cart', 'Add to Bag', 'Buy Now', 'Add to Order', 'Start Order' */" +
            "           var xpath = \"//button[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to cart') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to bag') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to basket') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'buy now') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start your order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'order now')] | \" +" +
            "                       \"//a[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to cart') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to bag') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to basket') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'buy now') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start your order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'order now')] | \" +" +
            "                       \"//input[contains(translate(@value, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to cart') or contains(translate(@value, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to bag') or contains(translate(@value, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'buy now') or contains(translate(@value, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to order') or contains(translate(@value, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start order') or contains(translate(@value, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start your order') or contains(translate(@value, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'order now')] | \" +" +
            "                       \"//*[@role='button' and (contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to cart') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to bag') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to basket') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'buy now') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'add to order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start your order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'order now'))] | \" +" +
            "                       \"//div[(string-length(normalize-space(text())) < 30) and (contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start your order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'order now'))] | \" +" +
            "                       \"//span[(string-length(normalize-space(text())) < 30) and (contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'start your order') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'order now'))]\";" +
            "           try {" +
            "               var result = document.evaluate(xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);" +
            "               buyBtn = result.singleNodeValue;" +
            "           } catch(e) {}" +
            "       }" +
            "       " +
            "       /* Auto-Detect Logic: Manual OR IsProduct OR (Name + Price + HasButton) */" +
            "       if (name && (isManual || isProduct || (price && buyBtn))) {" +
            "           if (retryTimer) clearTimeout(retryTimer);" +
            "           name = name.trim();" +
            "           if(price) price = price.trim();" +
            "           console.log('Found Item: ' + name + ' @ ' + price);" +
            "           if(window.Android) window.Android.onItemSelected(name, price, isManual);" +
            "       } else if (isManual) {" +
            "           var msg = 'Manual Fail: ';" +
            "           if (!name) msg += 'No Name Found. ';" +
            "           else msg += 'Name Found (' + name + '), but Logic Failed. ';" +
            "           window.Android.showToast(msg);" +
            "       } else {" +
            "           console.log('Ignored: Name found but no Buy Button?');" +
            "           /* Silent Fail for Auto */" +
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
            "           window.scrapePage(true);" +
            "       }" +
            "   }, true);" +
            "   " +
            "   window.scrapePage(" + manual + ");" + 
            "   if (!" + manual + ") {" +
            "       retryTimer = setTimeout(function() { console.log('Retrying scrape...'); window.scrapePage(false); }, 5000);" +
            "   }" + 
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

        @JavascriptInterface
        public void showToast(String toast) {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
        }

        /**
         * Called when an item is selected in the web view.
         * This is the "Recognition" step.
         */
        @JavascriptInterface
        public void onItemSelected(String itemName, String itemPrice, boolean manual) {
            // Deduplication: If Auto-Detect and same item, IGNORE.
            if (!manual && itemName != null && itemName.equals(lastUploadedName)) {
                Log.d("MagicBrowser", "Duplicate Item Ignored: " + itemName);
                return;
            }
            if (itemName != null && !itemName.equals("No Item Found")) {
                lastUploadedName = itemName;
            }

            // 1. Get Current URL (on UI thread to be safe, or direct?) 
            // WebAppInterface is run on background thread? No, JavaBridge is separate.
            // But we can't access webView UI methods strictly? 
            // We'll do it in runOnUiThread.

            runOnUiThread(() -> {
                String message = "Recognized: " + itemName;
                Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
                
                String currentUrl = webView.getUrl();
                String storeName = getStoreName(currentUrl);
                
                // 2. Send to Cloud (Critical Step before QR)
                sendItemToCloud(itemName);
                
                // 3. Generate "Lost Luggage" Message
                // TODO: Update PATTER_PHONE with real number
                String qrMessage = "Thanks for finding my luggage at " + storeName + " please call " + PATTER_PHONE + " for further instructions";
                
                // 4. Send to E-Paper
                processAndSendToEpaper(itemName, itemPrice, qrMessage);
            });
        }
        
        private String getStoreName(String urlStr) {
            try {
                String host = new java.net.URL(urlStr).getHost();
                host = host.replace("www.", "");
                String name = host.split("\\.")[0];
                if (name.length() > 0)
                    return name.substring(0, 1).toUpperCase() + name.substring(1);
                return name;
            } catch (Exception e) {
                return "The Store";
            }
        }

        private void sendItemToCloud(final String itemName) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        java.net.URL url = new java.net.URL(CLOUD_BACKEND_URL);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json; utf-8");
                        conn.setDoOutput(true);
                        String jsonInputString = "{\"item\": \"" + itemName + "\"}"; // Simplified JSON
                        try(java.io.OutputStream os = conn.getOutputStream()) {
                            byte[] input = jsonInputString.getBytes("utf-8");
                            os.write(input, 0, input.length);
                        }
                        Log.d("MagicCloud", "Sent item: " + itemName + " Response: " + conn.getResponseCode());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }
    
    private void processAndSendToEpaper(String name, String price, String qrContent) {
        // ... (Checks) ...
        if (AppStartActivity.btDevice == null) {
            Toast.makeText(this, "Error: Connect Bluetooth in Main Menu first!", Toast.LENGTH_LONG).show();
            return;
        }
        if (EPaperDisplay.epdInd < 0 || EPaperDisplay.epdInd >= EPaperDisplay.getDisplays().length) {
            Toast.makeText(this, "Error: Select Display Type in Main Menu first!", Toast.LENGTH_LONG).show();
            return;
        }
        if (name == null || price == null) return;
        
        EPaperDisplay selectedDisplay = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd];
        int targetWidth = selectedDisplay.width;
        int targetHeight = selectedDisplay.height;
        
        String safeName = name.replace("|", " ").trim();
        if (safeName.length() > 64) safeName = safeName.substring(0, 64) + "...";
        String safePrice = price.replace("|", "").trim();
        
        String displayText = safeName + "|" + safePrice;

        boolean rotate = false;
        if (targetHeight > targetWidth) {
            int temp = targetWidth;
            targetWidth = targetHeight; 
            targetHeight = temp;        
            rotate = true;
        }

        // 1. Generate QR Code Bitmap matched to display
        // Pass custom QR Content + Display Text separately
        Bitmap barcodeInfo = createBarcodeImage(qrContent, displayText, targetWidth, targetHeight);

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
            // 4. Launch Silent Upload
            new BackgroundUploader(BrowserActivity.this).startUpload(AppStartActivity.indTableImage);

        } catch (Exception e) {
            Toast.makeText(this, "Image Process Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private Bitmap createBarcodeImage(String qrContent, String displayText, int targetWidth, int targetHeight) {
        try {
            MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
            
            // MAXIMIZED QR CODE
            int qrSize = Math.min(targetWidth, targetHeight);
            
            BitMatrix bitMatrix = multiFormatWriter.encode(qrContent, BarcodeFormat.QR_CODE, qrSize, qrSize);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap qrBitmap = barcodeEncoder.createBitmap(bitMatrix);
            
            Bitmap combined = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(combined);
            canvas.drawColor(Color.WHITE);
            
            // CENTER THE QR CODE
            int x = (targetWidth - qrSize) / 2;
            int y = (targetHeight - qrSize) / 2;
            
            canvas.drawBitmap(qrBitmap, x, y, null);
            
            // Text Removed completely
            
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
