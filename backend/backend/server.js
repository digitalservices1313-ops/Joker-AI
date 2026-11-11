const express = require('express');
const cors = require('cors');
const { OpenAI } = require('openai');
require('dotenv').config();

const app = express();
app.use(cors());
app.use(express.json());

// Multi-provider OpenAI compatible client (OpenAI / Groq / NVIDIA / OpenRouter)
const openai = new OpenAI({
  apiKey: process.env.LLM_API_KEY || 'dummy_key',
  baseURL: process.env.LLM_BASE_URL || 'https://api.openai.com/v1'
});

// Health check endpoint (Server live hai ya nahi check karne ke liye)
app.get('/health', (req, res) => {
  res.json({
    status: 'online',
    agent: 'Joker AI Brain Active',
    timestamp: new Date()
  });
});

// Chat endpoint (App se message receive karke AI reply dega)
app.post('/api/chat', async (req, res) => {
  try {
    const { message } = req.body;
    if (!message) {
      return res.status(400).json({ error: 'Message cannot be empty' });
    }

    const completion = await openai.chat.completions.create({
      model: process.env.LLM_MODEL || 'gpt-4o-mini',
      messages: [
        {
          role: 'system',
          content: 'You are Joker AI, a witty, brilliant, and autonomous personal mobile assistant. Speak with personality, keep answers sharp, concise, and helpful.'
        },
        { role: 'user', content: message }
      ],
      max_tokens: 300,
      temperature: 0.7
    });

    const reply = completion.choices[0].message.content;
    res.json({ reply });

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
  console.log(`🃏 Joker AI Backend running on port ${PORT}`);
});
