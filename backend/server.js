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
  res.json({ status: 'online', agent: 'Joker Action Engine Active' });
});

app.post('/api/chat', async (req, res) => {
  try {
    const { message } = req.body;
    if (!message) {
      return res.status(400).json({ error: 'Message cannot be empty' });
    }

    const systemPrompt = `You are JOKER, an obedient autonomous personal mobile assistant.

STRICT INSTRUCTIONS:
1. NEVER explain how to do things on Android or iPhone. NO tutorials, NO steps, NO lectures.
2. When the user gives a command (like torch, open youtube, battery, whatsapp), your reply MUST BE strictly ONE SHORT SENTENCE (max 5-8 words). Example: "Ok Boss, torch on karta hoon."
3. Always respond strictly in valid JSON format:
{
  "reply": "Ok Boss, torch on karta hoon.",
  "action": "ACTION_NAME",
  "target": "TARGET_PARAMETER"
}

Actions:
- TORCH_ON (when user says torch/flashlight on/chalu)
- TORCH_OFF (when user says torch/flashlight off/band)
- BATTERY_CHECK (when user asks battery)
- OPEN_YOUTUBE (open youtube)
- OPEN_WHATSAPP (open whatsapp)
- OPEN_CAMERA (open camera)
- OPEN_LENS (open google lens)
- OPEN_SETTINGS (open settings)
- WEB_SEARCH (google search)
- NONE (normal chat - reply concisely in Hindi/Hinglish without lecturing)

Always return strictly valid JSON only. No markdown, no extra text.`;

    const completion = await openai.chat.completions.create({
      model: process.env.LLM_MODEL || 'openai/gpt-oss-20b',
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: message }
      ],
      max_tokens: 150,
      temperature: 0.3
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
  console.log(`🃏 JOKER Agent Backend running on port ${PORT}`);
});
