# GitHub Models API - Quick Start (5 Minutes)

**For the impatient:** Build your A2UI agent server in 5 minutes.

---

## TL;DR

Use **GitHub Models API** (not Copilot Extensions SDK).

```
GitHub Models API
├─ Endpoint: https://models.inference.ai.azure.com
├─ Auth: GitHub Personal Access Token
├─ Models: gpt-4o, claude-3.5-sonnet, llama-3.1, mistral, phi
└─ SDK: openai (or azure-ai-inference)

Use Case: ✅ Call LLMs from Python backend ✅ Android app makes HTTP calls
```

---

## Step 1: Get GitHub Token (2 min)

```bash
# Go to: https://github.com/settings/tokens
# Create token:
# - Name: "github-models-api"
# - Scope: check "models:read" ONLY
# - Click "Generate token"

# Save token to env:
export GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

---

## Step 2: Install Python & Dependencies (1 min)

```bash
pip install fastapi uvicorn openai

# Create .env
echo 'GITHUB_TOKEN=ghp_...' > .env
```

---

## Step 3: Copy Agent Code (1 min)

From: `/Users/vijayakella/.copilot/session-state/9257d149-e82c-45ce-8a5b-7b52a0812518/research/github-models-api-agent-guide.md`

Section: **PART 3: Complete FastAPI Agent Server**

Copy the `agent.py` code (starts ~line 450).

---

## Step 4: Run & Test (1 min)

```bash
# Terminal 1: Run agent
export GITHUB_TOKEN="ghp_..."
python agent.py

# Terminal 2: Test
curl -X POST "http://localhost:8000/chat?message=Show+hello+world"

# Should output JSONL:
# data: {"beginRendering": ...}
# data: {"surfaceUpdate": ...}
```

---

## Step 5: Connect Android (Optional)

Update `ChatViewModel.kt`:

```kotlin
val repository = RealChatRepository("http://10.0.2.2:8000")
```

Copy `RealChatRepository.kt` from guide (PART 4).

---

## Key URLs

| Item | URL |
|------|-----|
| GitHub Models API Docs | https://docs.github.com/en/github-models |
| GitHub Models Endpoint | https://models.inference.ai.azure.com |
| GitHub PAT Settings | https://github.com/settings/tokens |
| Complete Guide | See `github-models-api-agent-guide.md` |

---

## Available Models

- `gpt-4o` ⭐ (best for A2UI JSON)
- `claude-3-5-sonnet` (great for structured output)
- `llama-3.1-70b` (open source, cheaper)
- `mistral-large`
- `phi-4` (fast, lightweight)

---

## Environment Variables

```bash
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxx  # Required
MODEL=gpt-4o                                      # Default
AGENT_PORT=8000                                   # Default
```

---

## Common Issues

| Issue | Solution |
|-------|----------|
| `GITHUB_TOKEN not set` | `export GITHUB_TOKEN="ghp_..."` |
| `Connection refused` | Ensure agent running on port 8000 |
| `401 Unauthorized` | Check token scope includes `models:read` |
| `Android: 10.0.2.2:8000` | For emulator; use machine IP for physical device |

---

## What's Different from Anthropic Version?

| Aspect | Anthropic | GitHub Models |
|--------|-----------|---------------|
| API Key | Anthropic API key | GitHub PAT |
| SDK | `anthropic` package | `openai` package |
| Endpoint | `api.anthropic.com` | `models.inference.ai.azure.com` |
| Cost | $0.003/1K tokens | Free tier + pay-as-you-go |
| Models | Claude only | 10+ to choose from |

---

## Next: Full Implementation

See: `github-models-api-agent-guide.md` (complete 1500+ line guide)

- Part 1: GitHub Models API overview
- Part 2: Python SDK options
- Part 3: Complete FastAPI agent server ← START HERE
- Part 4: Android integration
- Part 5: Environment setup
- Part 6: Testing workflow
- Part 7: Troubleshooting
- Part 8: Cost analysis
- Part 9: Production deployment

---

**Status:** Ready to implement!

