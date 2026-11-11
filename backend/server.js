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
  res.json({ status: 'online', agent: 'Joker AI Agent Action Engine Active' });
});

app.post('/api/chat', async (req, res) => {
  try {
    const { message } = req.body;
    if (!message) {
      return res.status(400).json({ error: 'Message cannot be empty' });
    }

    const systemPrompt = `You are Joker AI, an autonomous mobile intelligence agent.
You can converse wittily and also execute Android actions.

If the user wants to perform an action, you MUST respond in pure JSON format:
{
  "reply": "Your witty confirmation message to speak to user",
  "action": "ACTION_NAME",
  "target": "TARGET_PARAMETER"
}

Supported actions:
1. OPEN_YOUTUBE (target: optional search term or empty)
2. OPEN_WHATSAPP (target: empty)
3. OPEN_CAMERA (target: empty)
4. OPEN_SETTINGS (target: empty)
5. WEB_SEARCH (target: search query)

If the user is just chatting normally and NOT asking to open an app or search, respond in pure JSON format:
{
  "reply": "Your witty response here",
  "action": "NONE",
  "target": ""
}
Always return strictly valid JSON only. No markdown ticks, no extra text.`;

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
      // Clean JSON if model added backticks
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
