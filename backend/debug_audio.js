import handler from './api/voice.js';

// Simulate Twilio fetching the Audio (POST + mode=audio)
const req = {
    method: 'POST',
    headers: { host: 'localhost:3000' },
    query: { mode: 'audio' }
};

const res = {
    setHeader: (k, v) => console.log(`[HEADER] ${k}: ${v}`),
    status: (code) => ({
        send: (data) => console.log(`[SEND] ${code}`, data instanceof Buffer ? `<Buffer len=${data.length}>` : String(data).substring(0, 100) + "..."), // Truncate
        json: (data) => console.log(`[JSON] ${code}`, data)
    })
};

console.log("Testing Audio Request...");
handler(req, res).catch(err => console.error("Error:", err));
