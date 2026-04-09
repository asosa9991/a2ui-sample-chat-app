# A2UI Sample Chat App

A reference implementation of the **A2UI protocol** — a streaming SSE protocol that lets an AI agent drive a native Android chat UI by sending structured UI operations alongside natural-language text.

The project ships a unified Python agent server and an Android Jetpack Compose front-end. The server serves both LLM and template routes; the Android app's runtime toggle chip selects which route to use — no server restart needed.

---

## Repository Structure

```
a2ui-sample-chat-app/
├── agent/                  # Unified agent server: LLM + template routes on port 8000
├── agent-templates/        # ⚠️  DEPRECATED — merged into agent/ as of v0.8.0
├── app/                    # Android app (Jetpack Compose, SSE consumer)
├── mockdata/               # Pre-formatted display JSON injected into LLM prompts
├── logs/                   # Agent PID files and log files (gitignored)
├── agent.sh                # Service management script (start, stop, status, logs)
└── research/               # Architecture docs and protocol notes
```

---

## Running the Agent Server

A single server in `agent/` exposes **two routes** on **port 8000**:

| Route | Description |
|-------|-------------|
| `POST /chat/stream` | LLM path — GitHub Copilot SDK (`claude-sonnet-4.6`), requires `GITHUB_TOKEN` |
| `POST /chat/stream/template` | Template path — deterministic, no API key, instant response |

The Android app's **runtime toggle chip** in `ChatTopBar` switches between routes — no server restart is needed.

`agent.sh` is the single entry-point for setup, start, stop, and observability. Run it with no arguments to see the full command reference:

```bash
./agent.sh
```

### Quick-start: LLM Agent (requires `GITHUB_TOKEN`)

Starting `agent/` gives you **both** the LLM and template routes simultaneously.

**1. Create `agent/.env`** (copy from the example):

```bash
cp agent/.env.example agent/.env
# Then open agent/.env and set:
#   GITHUB_TOKEN=ghu_your_token_here
```

**2. Set up and start:**

```bash
./agent.sh setup llm
./agent.sh start llm
# Server is now up on http://localhost:8000
# Both /chat/stream and /chat/stream/template are available
```

> **First-time convenience:** `start` auto-runs setup if `.venv` is missing, so the explicit `setup` step above is optional.
>
> **Template route without a token:** The `/chat/stream/template` route is deterministic and requires no `GITHUB_TOKEN`. You can still set `GITHUB_TOKEN` to an empty string in `agent/.env` if you only intend to use the template route.

### Managing the Running Agent

| Goal | Command |
|------|---------|
| Check which agent is running, its PID, uptime, and last 20 log lines | `./agent.sh status` |
| Stop the agent on port 8000 | `./agent.sh stop` |
| Restart the agent | `./agent.sh restart llm` |
| Follow the live log | `./agent.sh logs` |
| Follow the LLM agent log explicitly | `./agent.sh logs llm` |

> **Port guard:** `start` refuses to run if port 8000 is already in use. Use `./agent.sh stop` first.

### Full Command Reference

```
./agent.sh setup llm          # Create .venv + pip install (idempotent)
./agent.sh start llm          # Start server in background (both routes available)
./agent.sh stop               # Stop agent running on port 8000
./agent.sh restart llm        # stop → start
./agent.sh status             # PID, uptime, last 20 log lines
./agent.sh logs [llm]         # tail -f log
```

Log files are written to `logs/agent-llm.log`.

---

## Android App

The Jetpack Compose app connects to the agent server running on port 8000.

| Device | Base URL |
|--------|----------|
| Emulator | `http://10.0.2.2:8000` (hardcoded in `RealChatRepository.kt`) |
| Physical device | Update `RealChatRepository.kt` with your machine's LAN IP |

**Two independent toggles control how the app behaves:**

| Toggle | Where | What it controls |
|--------|-------|-----------------|
| **`USE_REAL_AGENT`** build flag | `app/build.gradle.kts` | `true` → talk to the live server on port 8000; `false` → use offline mock data (no server needed) |
| **LLM ↔ Template chip** | `ChatTopBar` UI at runtime | Selects which server route to call — `/chat/stream` (LLM) or `/chat/stream/template` (deterministic). No code change or server restart needed. |

```kotlin
// app/build.gradle.kts — controls real server vs. offline mock data:
buildConfigField("Boolean", "USE_REAL_AGENT", "true")
```

---

## Agent Comparison

Both routes are served by the **same process** (`agent/agent.py`). The Android toggle chip selects the route at runtime.

| | LLM route (`/chat/stream`) | Template route (`/chat/stream/template`) |
|--|----------------------------|------------------------------------------|
| **API key needed** | `GITHUB_TOKEN` in `agent/.env` | None |
| **Determinism** | ❌ Non-deterministic | ✅ Same input → same output |
| **Latency** | Seconds (model inference) | < 10 ms render + simulated stream |
| **Dependencies** | FastAPI + GitHub Copilot SDK | FastAPI only (bundled in `agent/`) |
| **Compliance** | Generated content | Pre-approved templates only |
| **Port** | 8000 | 8000 |

---

## Further Reading

- [`agent/README.md`](agent/README.md) — LLM agent endpoints, auth options, mock data injection, integration tests
- [`agent-templates/README.md`](agent-templates/README.md) — ⚠️ Deprecated. Template engine merged into `agent/` as of v0.8.0. Preserved for historical reference only.
