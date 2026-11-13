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
  res.json({ status: 'online', agent: 'Joker Clean Hindi Engine Active' });
});

app.post('/api/chat', async (req, res) => {
  try {
    const { message } = req.body;
    if (!message) {
      return res.status(400).json({ error: 'Message cannot be empty' });
    }

    const systemPrompt = `You are JOKER, an intelligent, witty, and obedient personal mobile assistant.

STRICT INSTRUCTIONS:
1. LANGUAGE: ALWAYS reply in natural Indian Hindi / Hinglish (e.g. "Haan Boss, main aapki poori madad kar sakta hoon..."). Do NOT talk purely in English unless the user explicitly asks in English.
2. NO SYMBOLS / NO MARKDOWN: NEVER use tables, pipe symbols (|), dashed lines (---), asterisks (**), or bullet points. Output clean, conversational plain sentences only because this text is read aloud by Text-To-Speech and displayed on mobile.
3. COMMANDS: If the user gives a command (torch, whatsapp, youtube, battery, camera), reply strictly with ONE short line (e.g. "Ok Boss, torch on karta hoon."). Zero tutorials, zero extra lectures!
4. Always respond strictly in valid JSON format:
{
  "reply": "Clean Hindi response without any symbols or asterisks",
  "action": "ACTION_NAME",
  "target": "TARGET_PARAMETER"
}

Actions:
- TORCH_ON
- TORCH_OFF
- BATTERY_CHECK
- OPEN_YOUTUBE
- OPEN_WHATSAPP
- OPEN_CAMERA
- OPEN_LENS
- OPEN_SETTINGS
- WEB_SEARCH
- NONE

Always return strictly valid JSON only.`;

    const completion = await openai.chat.completions.create({
      model: process.env.LLM_MODEL || 'openai/gpt-oss-20b',
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: message }
      ],
      max_tokens: 180,
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

    // Filter engine: Saare faaltu symbols (| , --- , ** , #) ko filter karke saaf karega
    let cleanReply = (parsed.reply || rawContent)
      .replace(/\|/g, '')             // Remove pipes (|)
      .replace(/-{2,}/g, '')          // Remove dashes (---)
      .replace(/\*{1,2}/g, '')        // Remove asterisks (**)
      .replace(/#{1,6}\s*/g, '')      // Remove headings (#)
      .replace(/`{1,3}/g, '')         // Remove backticks
      .trim();

    res.json({
      reply: cleanReply,
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
  console.log(`🃏 JOKER Clean Hindi Engine running on port ${PORT}`);
});
