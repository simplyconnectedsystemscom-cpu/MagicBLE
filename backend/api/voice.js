import { kv } from '@vercel/kv';
import axios from 'axios';
import 'dotenv/config';

// CONFIG: USER TO UPDATE THESE
const ELEVENLABS_API_KEY = process.env.ELEVENLABS_API_KEY;
const VOICE_ID = process.env.ELEVENLABS_VOICE_ID;

export default async function handler(request, response) {
    try {
        console.log("VOICE HANDLER TRIGGERED");
        console.log("ELEVENLABS_API_KEY set:", !!process.env.ELEVENLABS_API_KEY);
        console.log("ELEVENLABS_VOICE_ID:", process.env.ELEVENLABS_VOICE_ID);

        // 1. Get the detected item (Handle KV failures gracefully)
        let item = "luggage";
        try {
            item = await kv.get('current_item') || "luggage";
        } catch (kvError) {
            console.warn("KV Database Error (or not configured):", kvError.message);
            // Fallback to "luggage" is already set
        }

        // If this is the Voice Call Request (POST) from Twilio
        if (request.method === 'POST') {
            const twiml = `<?xml version="1.0" encoding="UTF-8"?>
<Response>
    <Answer/>
    <Play>${`https://${request.headers.host}/api/voice?mode=audio`}</Play>
</Response>`;
            response.setHeader('Content-Type', 'text/xml');
            return response.status(200).send(twiml);
        }

        // If this is the Audio Request (GET/POST with mode=audio)
        if (request.query.mode === 'audio') {
            const textToSay = `Thanks for finding my luggage. It contains a ${item}. Please put it in my suitcase.`;

            try {
                if (!ELEVENLABS_API_KEY || !VOICE_ID) {
                    throw new Error("Missing ElevenLabs configuration");
                }

                const elevenLabsUrl = `https://api.elevenlabs.io/v1/text-to-speech/${VOICE_ID}/stream`;

                const audioResponse = await axios({
                    method: 'POST',
                    url: elevenLabsUrl,
                    data: {
                        text: textToSay,
                        model_id: "eleven_monolingual_v1",
                        voice_settings: { stability: 0.5, similarity_boost: 0.75 }
                    },
                    headers: {
                        'Accept': 'audio/mpeg',
                        'xi-api-key': ELEVENLABS_API_KEY,
                        'Content-Type': 'application/json'
                    },
                    responseType: 'arraybuffer'
                });

                response.setHeader('Content-Type', 'audio/mpeg');
                return response.status(200).send(audioResponse.data);
            } catch (elevenError) {
                console.error("ElevenLabs Error:", elevenError.message);
                if (elevenError.response) {
                    console.error("ElevenLabs Response Data:", elevenError.response.data.toString());
                }

                // Fallback to Twilio <Say> is not possible here because we are in the <Play> URL context which expects AUDIO data, not TwiML.
                // However, user wants to know IF it works.
                // Since <Play> expects an audio file, we can't easily fallback to <Say> inside this specific endpoint without changing the architecture check.
                // Actually, if this fails, Twilio will stop playing. 

                // ALTERNATIVE: Return a standard TwiML <Say> if we were in the main loop, but here we are serving the audio file.
                // We can return a 500, and Twilio might handle it, or we could redirect to a simple TTS service, but let's stick to logging for now as the user wants their CLONED voice.
                // But wait, if we return 500, the call drops.
                // Let's return a simple text error to logs and 500. Twilio will say "An application error has occurred".
                // Better yet, let's try to keep it simple. If it fails, we want to know WHY.

                // Re-throwing to trigger the outer catch
                throw elevenError;
            }
        }

        return response.status(400).json({ error: 'Invalid mode' });

    } catch (error) {
        console.error("General Error:", error);
        response.status(500).send("Error generating audio");
    }
}
