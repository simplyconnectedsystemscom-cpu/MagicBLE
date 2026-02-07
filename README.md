# Magic BLE Android Extension

This project is a Chrome Extension designed to run on **Android** (using browsers like Kiwi Browser) to bridge web interactions to a Bluetooth Low Energy (BLE) device.

## How it Works
1.  **Hub Page**: A dedicated page (`hub.html`) that you open once. You click "Connect" to pair with your BLE device (e.g., ESP32, Arduino, Magic Wand). This page stays open in the background to maintain the connection.
2.  **Spy Script**: A script runs on specific shopping websites (configurable in `content.js`). It detects when you click on items.
3.  **Relay**: When an item is clicked, the Spy tells the Hub, and the Hub sends the data via BLE.

## Installation on Android
1.  **Browser**: Install **Kiwi Browser** from the Play Store.
2.  **Transfer Files**: Copy this `MagicBLE` folder to your Android device (or zip it).
3.  **Load Extension**:
    *   Open Kiwi Browser.
    *   Go to `menu` -> `Extensions`.
    *   Enable `Developer mode`.
    *   Click `+(from .zip/.crx/.user.js)` if you zipped it, or `Load unpacked`. Note: Kiwi might prefer `.zip` files for local loading if not using USB debugging.
    *   **Best method**: Zip the contents of `MagicBLE` into `magicble.zip` and select that file in Kiwi.

## Usage
1.  Open the extension menu and click "Open Hub" (or navigate to `chrome-extension://<id>/hub.html`).
2.  Click "Connect to Cart Device" and select your Bluetooth module.
3.  Leave that tab open.
4.  Open a new tab and go to your target shopping site.
5.  Click on items. Watching the Hub tab, you should see "Sent: ..." logs.

## Configuration
*   **Target Site**: Edit `content.js` `SELECTORS` to match the CSS classes of the shop you are using.
*   **Bluetooth UUIDs**: Edit `hub.js` `SERVICE_UUID` and `CHARACTERISTIC_UUID` to match your hardware.
