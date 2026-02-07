// Content Script (Spy)
// 1. Detects when the user is on a shopping cart or product page
// 2. Extracts item info
// 3. Sends to Background

console.log("MagicBLE Spy Active");

// CONFIG: Selectors for the target site (Example: generic schema.org or common classes)
const SELECTORS = {
    productTitle: 'h1, .product-title, .cart-item-name',
    cartItem: '.cart-item, .product-card' // Elements that represent an item
};

// Function to send data
function broadcastItem(text) {
    if (!text) return;
    console.log("Broadcasting Item:", text);
    chrome.runtime.sendMessage({
        type: 'ITEM_OPENED',
        payload: {
            text: text,
            url: window.location.href,
            timestamp: Date.now()
        }
    });
}

// Global click listener to catch user interactions
document.addEventListener('click', (e) => {
    // 1. Check if user clicked a product card/link
    const item = e.target.closest(SELECTORS.cartItem);
    if (item) {
        // Try to find a title inside
        const titleEl = item.querySelector(SELECTORS.productTitle) || item.innerText;
        const title = typeof titleEl === 'string' ? titleEl : titleEl.innerText;

        // Clean up text
        const cleanTitle = title.split('\n')[0].trim();
        broadcastItem(cleanTitle);
    }
});

// Also observe DOM mutations for dynamic carts (Single Page Apps)
const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
        if (mutation.addedNodes.length) {
            // Check for new modals or side-carts
            // This needs specific tuning for the target site
        }
    }
});

observer.observe(document.body, { childList: true, subtree: true });
