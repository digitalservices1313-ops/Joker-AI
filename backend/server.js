const express = require('express');
const cors = require('cors');
const { OpenAI } = require('openai');
require('dotenv').config();

const app = express();
app.use(cors());
app.use(express.json());

const openai = new OpenAI({
  apiKey: process.env.LLM_API_KEY || 'dummy_key',
  baseURL: process.env.LLM_BASE_URL || 'https://api.groq.com/openai/v1'
});

app.get('/health', (req, res) => {
  res.json({ status: 'online', agent: 'Joker AI Hardware Agent Active' });
});

app.post('/api/chat', async (req, res) => {
  try {
    const { message } = req.body;
    if (!message) {
      return res.status(400).json({ error: 'Message cannot be empty' });
    }

    const systemPrompt = `You are Joker AI, an autonomous personal mobile intelligence agent.
You can converse wittily and control phone hardware & apps.

Always respond in pure JSON format:
{
  "reply": "Your witty response or confirmation to speak aloud",
  "action": "ACTION_NAME",
  "target": "TARGET_PARAMETER"
}

Supported Actions:
1. TORCH_ON (Turn on phone flashlight/torch)
2. TORCH_OFF (Turn off phone flashlight/torch)
3. BATTERY_CHECK (Check phone battery level)
4. OPEN_YOUTUBE (Open YouTube app)
5. OPEN_WHATSAPP (Open WhatsApp app)
6. OPEN_CAMERA (Open Camera app)
7. OPEN_LENS (Open Google Lens)
8. OPEN_SETTINGS (Open Phone Settings)
9. WEB_SEARCH (Google web search, target: query)
10. NONE (Normal conversation)

Always return strictly valid JSON only. No markdown, no extra text.`;

    const completion = await openai.chat.completions.create({
      model: process.env.LLM_MODEL || 'openai/gpt-oss-20b',
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: message }
      ],
      max_tokens: 250,
      temperature: 0.5
    });

    const rawContent = completion.choices[0].message.content.trim();
    let parsed;
    try {
      const cleanJson = rawContent.replace(/```json/g, '').replace(/```/g, '').trim();
      parsed = JSON.parse(cleanJson);
    } catch (e) {
      parsed = { reply: rawContent, action: 'NONE', target: '' };
    }

    res.json({
      reply: parsed.reply || rawContent,
      action: parsed.action || 'NONE',
      target: parsed.target || ''
    });

  } catch (error) {
    console.error('AI Processing Error:', error);
    res.status(500).json({
      error: 'Brain communication error',
      details: error.message
    });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`🃏 Joker AI Agent Backend running on port ${PORT}`);
});
