// Hub Script
// Manages Web Bluetooth Connection and listens for background messages

let bluetoothDevice;
let bluetoothCharacteristic;

// Replace with your device's service and characteristic UUIDs
// Standard Serial Port Profile or Custom Service
const SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"; // Example: UART Service
const CHARACTERISTIC_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"; // RX Characteristic

const connectBtn = document.getElementById('connectBtn');
const statusDiv = document.getElementById('status');
const logDiv = document.getElementById('log');

function log(msg) {
    const div = document.createElement('div');
    div.className = 'entry';
    div.innerHTML = `<span class="time">${new Date().toLocaleTimeString()}</span> ${msg}`;
    logDiv.prepend(div);
}

// 1. Register with Background
chrome.runtime.sendMessage({ type: 'HUB_REGISTER' }, (response) => {
    log("Registered as Hub");
});

// 2. Listen for messages from Content Script (via Background)
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === 'ITEM_OPENED') {
        log(`Received Item: ${message.payload.text}`);
        sendToDevice(message.payload.text);
    }
});

// 3. Bluetooth Logic
connectBtn.addEventListener('click', async () => {
    try {
        log("Requesting Bluetooth Device...");
        bluetoothDevice = await navigator.bluetooth.requestDevice({
            acceptAllDevices: true,
            optionalServices: [SERVICE_UUID]
        });

        bluetoothDevice.addEventListener('gattserverdisconnected', onDisconnected);

        log(`Connecting to ${bluetoothDevice.name}...`);
        const server = await bluetoothDevice.gatt.connect();

        log("Getting Service...");
        const service = await server.getPrimaryService(SERVICE_UUID);

        log("Getting Characteristic...");
        bluetoothCharacteristic = await service.getCharacteristic(CHARACTERISTIC_UUID);

        statusDiv.innerText = `Connected to ${bluetoothDevice.name}`;
        statusDiv.style.background = "#004d00";
        connectBtn.disabled = true;
        log("Ready to transmit!");

    } catch (error) {
        log(`Error: ${error}`);
        console.error(error);
    }
});

function onDisconnected() {
    statusDiv.innerText = "Disconnected";
    statusDiv.style.background = "#333";
    connectBtn.disabled = false;
    bluetoothCharacteristic = null;
    bluetoothDevice = null;
    log("Device disconnected.");
}

async function sendToDevice(text) {
    if (!bluetoothCharacteristic) {
        log("Cannot send: No device connected.");
        return;
    }

    try {
        const encoder = new TextEncoder();
        // Truncate or format as needed
        const data = encoder.encode(text.substring(0, 18)); // Example limit
        await bluetoothCharacteristic.writeValue(data);
        log(`Sent: "${text}"`);
    } catch (error) {
        log(`Send Error: ${error}`);
        // Try to reconnect?
    }
}
