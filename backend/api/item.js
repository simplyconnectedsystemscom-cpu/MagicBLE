import { kv } from '@vercel/kv';

export default async function handler(request, response) {
    if (request.method === 'POST') {
        const { item } = request.body;
        if (item) {
            await kv.set('current_item', item);
            return response.status(200).json({ success: true, item });
        }
    }
    return response.status(400).json({ error: 'Missing item' });
}
