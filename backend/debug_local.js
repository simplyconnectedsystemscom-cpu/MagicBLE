import handler from './api/voice.js';

// Mock Request and Response
const req = {
    method: 'POST',
    headers: { host: 'localhost:3000' },
    query: { mode: 'audio' } // Test audio mode first as it triggers external API calls
};

const res = {
    setHeader: (k, v) => console.log(`[HEADER] ${k}: ${v}`),
    status: (code) => ({
        send: (data) => console.log(`[SEND] ${code}`, data instanceof Buffer ? `<Buffer len=${data.length}>` : data),
        json: (data) => console.log(`[JSON] ${code}`, data)
    })
};

console.log("Starting Debug Run...");
handler(req, res).then(() => console.log("Done")).catch(err => console.error("Unhandled Error:", err));
