# A2UI Sample Chat App

A reference implementation of the **A2UI protocol** — a streaming SSE protocol that lets an AI agent drive a native Android chat UI by sending structured UI operations alongside natural-language text.

The project ships two interchangeable Python agent servers and an Android Jetpack Compose front-end.

---

## Repository Structure

```
a2ui-sample-chat-app/
├── agent/                  # LLM agent (GitHub Copilot SDK, claude-sonnet-4.6)
├── agent-templates/        # Template agent (deterministic, no LLM, no API key)
├── app/                    # Android app (Jetpack Compose, SSE consumer)
├── mockdata/               # Pre-formatted display JSON injected into LLM prompts
├── logs/                   # Agent PID files and log files (gitignored)
├── agent.sh                # Service management script for both agents
└── research/               # Architecture docs and protocol notes
```

---

## Running the Agent Server

Both agents expose the same `/chat/stream` SSE endpoint on **port 8000**. Only one can run at a time.

`agent.sh` is the single entry-point for setup, start, stop, and observability. Run it with no arguments to see the full command reference:

```bash
./agent.sh
```

### Quick-start: Template Agent (no API key required)

The template agent is fully deterministic and needs no credentials — the fastest way to get the Android app talking to a real server.

```bash
# One-time setup (creates .venv + installs dependencies)
./agent.sh setup template

# Start in the background
./agent.sh start template
```

> **First-time convenience:** `start` auto-runs setup if `.venv` is missing, so the explicit `setup` step above is optional.

### Quick-start: LLM Agent (requires `GITHUB_TOKEN`)

The LLM agent uses the GitHub Copilot SDK to call `claude-sonnet-4.6`.

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
```

### Managing the Running Agent

| Goal | Command |
|------|---------|
| Check which agent is running, its PID, uptime, and last 20 log lines | `./agent.sh status` |
| Stop whichever agent is on port 8000 | `./agent.sh stop` |
| Stop then restart with a different agent | `./agent.sh restart template` |
| Follow the live log | `./agent.sh logs` |
| Follow a specific agent's log | `./agent.sh logs llm` |

> **Port guard:** `start` refuses to run if port 8000 is already in use. Use `./agent.sh stop` first.

### Full Command Reference

```
./agent.sh setup <llm|template|all>   # Create .venv + pip install (idempotent)
./agent.sh start <llm|template>       # Start agent in background
./agent.sh stop                       # Stop agent running on port 8000
./agent.sh restart <llm|template>     # stop → start
./agent.sh status                     # PID, uptime, last 20 log lines
./agent.sh logs [llm|template]        # tail -f log (default: most recent)
```

Log files are written to `logs/agent-llm.log` and `logs/agent-template.log`.

---

## Android App

The Jetpack Compose app connects to whichever agent is running on port 8000.

| Device | Base URL |
|--------|----------|
| Emulator | `http://10.0.2.2:8000` (hardcoded in `RealChatRepository.kt`) |
| Physical device | Update `RealChatRepository.kt` with your machine's LAN IP |

Toggle between the real agent and offline mock data in `app/build.gradle.kts`:

```kotlin
// In the debug buildType:
buildConfigField("Boolean", "USE_REAL_AGENT", "true")
```

---

## Agent Comparison

| | Template Agent (`agent-templates/`) | LLM Agent (`agent/`) |
|--|--------------------------------------|----------------------|
| **API key needed** | None | `GITHUB_TOKEN` in `agent/.env` |
| **Determinism** | ✅ Same input → same output | ❌ Non-deterministic |
| **Latency** | < 10 ms render + simulated stream | Seconds (model inference) |
| **Dependencies** | FastAPI + uvicorn only | + GitHub Copilot SDK |
| **Compliance** | Pre-approved templates only | Generated content |
| **Port** | 8000 | 8000 |

---

## Further Reading

- [`agent/README.md`](agent/README.md) — LLM agent endpoints, auth options, mock data injection, integration tests
- [`agent-templates/README.md`](agent-templates/README.md) — Template authoring guide, SSE protocol details, intent routing
