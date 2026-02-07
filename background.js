// Background Service Worker
// Acts as a relay between the Content Script (Spy) and the Hub Page (Controller)

// Keep track of the Hub tab ID if possible, or just broadcast to all tabs
let hubTabId = null;

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    console.log("Background received:", message);

    if (message.type === 'HUB_REGISTER') {
        hubTabId = sender.tab.id;
        console.log("Hub registered at tab:", hubTabId);
        sendResponse({ status: "registered" });
    }
    else if (message.type === 'ITEM_OPENED') {
        // Broadcast to all tabs to ensure the Hub gets it
        // Or send specifically to hubTabId if we are sure it's alive
        chrome.tabs.query({}, (tabs) => {
            tabs.forEach(tab => {
                chrome.tabs.sendMessage(tab.id, message).catch(err => {
                    // Ignore errors for tabs that don't listen
                });
            });
        });
    }
});
