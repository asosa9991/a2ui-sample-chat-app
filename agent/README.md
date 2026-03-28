# A2UI Agent Server

Python FastAPI server that uses `github-copilot-sdk` to call an LLM and return A2UI `UiDefinition` JSON for the Android chat app.

## Setup

```bash
cd agent
pip install -r requirements.txt
cp .env.example .env
# Edit .env and add your GITHUB_TOKEN
```

## Auth options

**Option A — GitHub Copilot subscription:**
```
GITHUB_TOKEN=ghu_your_token_here
```

**Option B — GitHub Models API (free tier, no Copilot needed):**
```
GITHUB_MODELS_TOKEN=github_pat_your_token_here
```
Get a token at https://github.com/settings/tokens (needs `models:read` scope or just public access).

## Run

```bash
python agent.py
```

Server starts at http://localhost:8000.

## Test

```bash
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "show my account activity"}'
```

## Android Connection

The Android emulator connects to the host machine at `10.0.2.2:8000`.
Physical device: use your machine's local IP (e.g., `192.168.1.x:8000`).
