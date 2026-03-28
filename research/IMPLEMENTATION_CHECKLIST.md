# GitHub Models API Agent - Implementation Checklist

**Timeline:** 2-3 hours for complete end-to-end integration

---

## Phase 1: Setup (30 minutes)

### GitHub Token Creation (5 min)
- [ ] Navigate to https://github.com/settings/tokens
- [ ] Click "Generate new token (classic)"
- [ ] Name: `github-models-api`
- [ ] Select scope: `models:read` (ONLY this one)
- [ ] Click "Generate token"
- [ ] **Copy and save token** (shows only once)
- [ ] Verify token format: starts with `ghp_`

### Python Environment Setup (15 min)
- [ ] Create project directory: `mkdir ~/a2ui-agent && cd ~/a2ui-agent`
- [ ] Create virtual environment: `python3 -m venv venv`
- [ ] Activate venv: `source venv/bin/activate` (or `venv\Scripts\activate` on Windows)
- [ ] Install dependencies: `pip install fastapi uvicorn openai pydantic python-dotenv`
- [ ] Create `.env` file with:
  ```
  GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxx
  MODEL=gpt-4o
  AGENT_PORT=8000
  ```
- [ ] Verify env file not committed: `echo .env >> .gitignore`

### FastAPI Agent Setup (10 min)
- [ ] Copy `agent.py` code from guide (Part 3)
- [ ] Save as: `~/a2ui-agent/agent.py`
- [ ] Make executable: `chmod +x agent.py`
- [ ] Verify file syntax: `python -m py_compile agent.py`

---

## Phase 2: Testing Agent (30 minutes)

### Local Agent Testing (20 min)
- [ ] Start agent: `export GITHUB_TOKEN="ghp_..." && python agent.py`
- [ ] Verify startup message includes:
  - [ ] Model name (e.g., "gpt-4o")
  - [ ] Endpoint: https://models.inference.ai.azure.com
  - [ ] Port: 8000
- [ ] Keep agent running in Terminal 1

### Agent Endpoint Testing (10 min)
In Terminal 2, run these curl tests:
- [ ] **Test 1: Health check**
  ```bash
  curl http://localhost:8000/health
  ```
  Expected: `{"status": "ok", "service": "a2ui-agent-server", ...}`

- [ ] **Test 2: List models**
  ```bash
  curl http://localhost:8000/models
  ```
  Expected: List of available models

- [ ] **Test 3: Simple message**
  ```bash
  curl -X POST "http://localhost:8000/chat?message=Show+hello+world"
  ```
  Expected: SSE stream with JSONL operations starting with:
  ```
  data: {"beginRendering": ...}
  data: {"surfaceUpdate": ...}
  ```

- [ ] **Test 4: Complex message**
  ```bash
  curl -X POST "http://localhost:8000/chat?message=Create+a+card+with+my+name+Alice+and+email+alice%40example.com"
  ```
  Expected: Valid A2UI operations with surfaceUpdate containing components

### Validation Checks (10 min)
- [ ] All responses are valid JSONL (one JSON object per line)
- [ ] Each operation has exactly one top-level key (beginRendering, surfaceUpdate, etc.)
- [ ] beginRendering appears first
- [ ] All component IDs are unique
- [ ] Data bindings use either `{"literalString": "..."}` or `{"path": "/..."}`
- [ ] No circular references in component children

---

## Phase 3: Android Integration (60 minutes)

### Copy Repository Class (10 min)
- [ ] Create file: `app/src/main/java/com/example/a2ui/chat/data/repository/RealChatRepository.kt`
- [ ] Copy `RealChatRepository` code from guide (Part 4)
- [ ] Verify imports are available
- [ ] Check class extends `ChatRepository` interface

### Update ViewModel Factory (10 min)
- [ ] Open: `app/src/main/java/com/example/a2ui/chat/presentation/ChatViewModel.kt`
- [ ] Find `Factory` companion object
- [ ] Update repository initialization:
  ```kotlin
  // OLD:
  val repository = MockChatRepository()
  
  // NEW (for emulator):
  val repository = RealChatRepository("http://10.0.2.2:8000")
  
  // OR (for physical device, get IP):
  val repository = RealChatRepository("http://192.168.1.X:8000")
  ```
- [ ] Verify change compiles

### Build Android App (10 min)
- [ ] Open Terminal 3
- [ ] Run: `./gradlew clean build`
- [ ] Wait for build to complete
- [ ] Verify: `BUILD SUCCESSFUL`

### Start Android Emulator (20 min)
- [ ] Option A: Use Android Studio
  - [ ] Click "Run" button
  - [ ] Select emulator (e.g., "Pixel 6 API 30")
  - [ ] Wait for emulator to boot
  
- [ ] Option B: Command line
  - [ ] Run: `emulator -avd Pixel_6_API_30 &`
  - [ ] Wait for emulator window to appear
  
- [ ] Verify emulator is responsive (can see home screen)

### Install & Run App (10 min)
- [ ] Install: `./gradlew installDebug`
- [ ] Wait for installation
- [ ] Launch app: `adb shell am start -n com.example.a2ui.chat/.MainActivity`
- [ ] Verify app opens without crashing

---

## Phase 4: End-to-End Testing (30 minutes)

### Setup Test Environment (5 min)
- [ ] Terminal 1: Agent running (`python agent.py`)
- [ ] Terminal 2: Available for debug (if needed)
- [ ] Terminal 3: Available for debug (if needed)
- [ ] Terminal 4: View Android logs
  ```bash
  adb logcat RealChatRepository:D "*:S"
  ```

### Send First Message (10 min)
- [ ] In Android app, type message: "Show hello world"
- [ ] Tap "Send" button
- [ ] Watch for:
  - [ ] Message appears in chat history
  - [ ] Loading indicator appears
  - [ ] Agent log shows: "Generating A2UI operations for: Show hello world"
  - [ ] Android log shows: "Sending message to GitHub Models agent"
  - [ ] After 3-5 seconds, UI appears on screen
  - [ ] UI says "Hello!"

### Test Multiple Messages (10 min)
- [ ] Message 2: "Show my account balance of $48291.73"
  - [ ] Expected: Card with balance displayed
  
- [ ] Message 3: "Create a form with fields for name and email"
  - [ ] Expected: TextField widgets for input
  
- [ ] Message 4: "Show a list of colors: red, green, blue"
  - [ ] Expected: List widget with items

### Verify Integration (5 min)
- [ ] Android logs show "Processing operation: ..." for each operation
- [ ] UI renders without errors
- [ ] No crashes in logcat
- [ ] Agent server logs show completion for each request

---

## Phase 5: Troubleshooting (As Needed)

### Agent Issues

**Problem:** "GITHUB_TOKEN environment variable is required!"
- [ ] Check env var: `echo $GITHUB_TOKEN`
- [ ] If empty: `export GITHUB_TOKEN="ghp_..."`
- [ ] Restart agent

**Problem:** "401 Unauthorized" from GitHub Models API
- [ ] Check token: `echo $GITHUB_TOKEN` (should start with `ghp_`)
- [ ] Go to https://github.com/settings/tokens
- [ ] Verify token has `models:read` scope
- [ ] If expired, create new token
- [ ] Update GITHUB_TOKEN and restart agent

**Problem:** "Model not found: gpt-4o"
- [ ] GitHub Models API might be down (rare)
- [ ] Try different model: `MODEL=claude-3-5-sonnet python agent.py`
- [ ] Check https://docs.github.com/en/github-models for available models

**Problem:** "Bad JSON" / "Validation error"
- [ ] Check agent logs for LLM output
- [ ] Try simpler message: "Show hello"
- [ ] Try different model (more capable): `gpt-4o` or `claude-3-5-sonnet`
- [ ] Add more examples to system prompt

### Android Issues

**Problem:** Android: "Connection refused: 10.0.2.2:8000"
- [ ] Verify agent running: `ps aux | grep agent.py`
- [ ] Verify port: `lsof -i :8000`
- [ ] If using physical device, get machine IP: `ipconfig getifaddr en0` (macOS)
- [ ] Update RealChatRepository URL: `RealChatRepository("http://192.168.1.X:8000")`

**Problem:** Android: "Bad JSON" in logs
- [ ] Check LLM output in agent logs
- [ ] Increase system prompt examples
- [ ] Try simpler queries first

**Problem:** App crashes after sending message
- [ ] Check Android logcat for stack trace
- [ ] Verify RealChatRepository imported correctly
- [ ] Check ChatViewModel Factory is using RealChatRepository

---

## Phase 6: Optimization (Optional)

- [ ] Add conversation history (pass previous messages to LLM)
- [ ] Implement event handling (handle user clicks on generated UI)
- [ ] Add data validation (validate LLM output before rendering)
- [ ] Improve system prompt (add more A2UI examples)
- [ ] Consider WebSocket instead of SSE (for lower latency)
- [ ] Add caching (cache system prompt to save tokens)
- [ ] Monitor costs (track GitHub Models API usage)

---

## Sign-Off

- [ ] All phases complete
- [ ] End-to-end testing successful
- [ ] Multiple user queries tested
- [ ] No crashes or errors
- [ ] UI renders correctly from LLM output
- [ ] Ready for production? (Yes/No)

---

## Reference Materials

| File | Purpose |
|------|---------|
| `github-models-api-agent-guide.md` | Complete implementation guide (1500+ lines) |
| `GITHUB_MODELS_QUICK_START.md` | 5-minute quick reference |
| `agent.py` | FastAPI server code |
| `RealChatRepository.kt` | Android client code |

---

## Estimated Time

- Phase 1 (Setup): 30 min
- Phase 2 (Testing Agent): 30 min
- Phase 3 (Android Integration): 60 min
- Phase 4 (End-to-End): 30 min
- Phase 5 (Troubleshooting): 0-60 min (as needed)
- **Total: 2.5-3.5 hours**

---

**Status:** Ready to implement!

Keep this checklist as you work through implementation. Check off items as you complete them.

