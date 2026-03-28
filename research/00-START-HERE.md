# 🚀 GitHub Copilot SDK Research - START HERE

## ⚡ TL;DR (30 seconds)

**The `github/copilot-sdk` is the official multi-language SDK** for embedding GitHub's AI agent directly into your apps.

**Quick Facts:**
- 📦 Repo: `github/copilot-sdk` (8,047 ⭐)
- 🌍 Languages: TypeScript, Python, Go, .NET, Java
- 🔑 Auth: GitHub Copilot subscription OR BYOK (Bring Your Own Key)
- 💬 Usage: Create sessions → Define tools → Send messages → Handle events
- 📡 Protocol: JSON-RPC via TCP or stdio with Copilot CLI

**One-liner code example (TypeScript):**
```typescript
const client = new CopilotClient();
const session = await client.createSession({ onPermissionRequest: approveAll });
const response = await session.sendAndWait({ prompt: "Hello!" });
console.log(response?.data.content);
```

---

## 📚 Documentation Guide

### For **10-minute overview** → Read: `RESEARCH_COMPLETE_SUMMARY.md`
- Quick facts about each SDK
- All authentication methods
- Simple code examples
- Architecture overview
- Learning resources

### For **comprehensive technical reference** → Read: `github-copilot-sdk.md`
- Executive summary
- Complete API for all 5 languages
- Authentication deep dive (4 methods)
- How to call LLMs (5 patterns)
- A2UI integration with server code
- Working code examples
- Streaming support details
- Confidence assessment

### For **copy-paste code** → See: `COPY_PASTE_CODE.md`
- Ready-to-use code snippets
- Express.js server example
- FastAPI server example
- Tool definition examples

### For **step-by-step implementation** → See: `IMPLEMENTATION_ROADMAP.txt` + `IMPLEMENTATION_CHECKLIST.md`
- Setup checklist
- Integration steps
- Testing guide

---

## 🎯 What You Need to Know

### The Repository
| Item | Value |
|------|-------|
| **Official Repo** | github/copilot-sdk |
| **URL** | https://github.com/github/copilot-sdk |
| **Status** | Technical Preview |
| **License** | MIT |
| **Stars** | 8,047 |

### Official SDKs

| Language | Package | Install |
|----------|---------|---------|
| **Node.js/TypeScript** | `@github/copilot-sdk` | `npm install @github/copilot-sdk` |
| **Python** | `github-copilot-sdk` | `pip install github-copilot-sdk` |
| **Go** | `github.com/github/copilot-sdk/go` | `go get github.com/github/copilot-sdk/go` |
| **.NET** | `GitHub.Copilot.SDK` | `dotnet add package GitHub.Copilot.SDK` |
| **Java** | `copilot-sdk-java` | Via Maven/Gradle |

### Core Concepts

**Architecture:**
```
Your App → SDK Client → JSON-RPC → Copilot CLI (server) → LLM
```

**Main Classes:**
- `CopilotClient` — Manages lifecycle and sessions
- `CopilotSession` — Single conversation
- `defineTool()` — Define custom tools
- Event handlers — Real-time streaming

**Authentication (4 methods):**
1. **GitHub Signed-in** — Default, uses stored OAuth
2. **OAuth App** — Pass token from your GitHub app
3. **Environment Variables** — Auto-detected from `COPILOT_GITHUB_TOKEN`, `GH_TOKEN`, `GITHUB_TOKEN`
4. **BYOK** — Use your own keys (OpenAI, Azure, Anthropic, Ollama, etc.) — **No Copilot subscription required**

---

## 🔥 Quick Start (Choose Your Language)

### TypeScript
```typescript
import { CopilotClient, approveAll } from "@github/copilot-sdk";

const client = new CopilotClient();
const session = await client.createSession({ 
  onPermissionRequest: approveAll 
});

const response = await session.sendAndWait({
  prompt: "What is 2+2?"
});

console.log(response?.data.content);  // "4"
await session.disconnect();
await client.stop();
```

### Python
```python
import asyncio
from copilot import CopilotClient
from copilot.session import PermissionHandler

async def main():
    client = CopilotClient()
    await client.start()
    
    session = await client.create_session(
        on_permission_request=PermissionHandler.approve_all
    )
    
    response = await session.send_and_wait({
        "prompt": "What is 2+2?"
    })
    
    print(response.data.content)  # "4"
    await session.disconnect()
    await client.stop()

asyncio.run(main())
```

### Go
```go
package main

import (
    "context"
    copilot "github.com/github/copilot-sdk/go"
)

func main() {
    ctx := context.Background()
    client := copilot.NewClient(nil)
    defer client.Stop()

    session, _ := client.CreateSession(ctx, &copilot.SessionConfig{
        OnPermissionRequest: func(req *copilot.PermissionRequest) bool {
            return true  // approve
        },
    })

    response, _ := session.SendAndWait(ctx, copilot.MessageOptions{
        Prompt: "What is 2+2?",
    })

    println(*response.Data.Content)  // "4"
}
```

---

## 🏗️ A2UI Integration Pattern

```
A2UI Chat UI
    ↓
Express/FastAPI Server
    ↓ (receives user message)
Copilot SDK Session
    ↓ (sends prompt)
Agent + LLM
    ↓ (returns response)
Parse Response
    ↓
Convert to A2UI Operations JSON
    ↓
Return to UI
```

**Example Express server:**
```typescript
import express from "express";
import { CopilotClient, approveAll } from "@github/copilot-sdk";

const app = express();
let session = null;

app.post("/chat", async (req, res) => {
  const { message } = req.body;
  
  const response = await session.sendAndWait({ prompt: message });
  
  // Convert to A2UI operations
  res.json({
    operations: [
      { type: "message", role: "assistant", content: response?.data.content }
    ],
  });
});

app.listen(3000, async () => {
  const client = new CopilotClient();
  session = await client.createSession({ onPermissionRequest: approveAll });
  console.log("Ready!");
});
```

---

## 🔐 Authentication Options Explained

### Option 1: GitHub Signed-in User (Default)
```typescript
// No code needed - uses stored credentials
const client = new CopilotClient();
```
**Requires:** Copilot CLI login + Copilot subscription

### Option 2: OAuth Token
```typescript
const client = new CopilotClient({
  githubToken: userAccessToken,  // From your GitHub OAuth app
  useLoggedInUser: false,
});
```
**Requires:** Copilot subscription

### Option 3: Environment Variables
```typescript
// Set env var: COPILOT_GITHUB_TOKEN=ghu_...
const client = new CopilotClient();  // Auto-detects
```
**Requires:** Copilot subscription

### Option 4: BYOK (Bring Your Own Key) ⭐
```typescript
const session = await client.createSession({
  model: "gpt-5",
  provider: {
    type: "openai",           // "openai" | "azure" | "anthropic"
    baseUrl: "https://api.openai.com/v1",
    apiKey: process.env.OPENAI_API_KEY,
  },
});
```
**Supported providers:**
- OpenAI
- Azure OpenAI / Azure AI Foundry
- Anthropic (Claude)
- Ollama (local models)
- OpenAI-compatible endpoints

**No Copilot subscription required!**

---

## 📖 How to Read This Documentation

**If you have 5 minutes:**
1. Read this file (you're reading it!)
2. Skim `RESEARCH_COMPLETE_SUMMARY.md`

**If you have 15 minutes:**
1. Read this file
2. Read `RESEARCH_COMPLETE_SUMMARY.md` completely
3. Review the quick code examples in `COPY_PASTE_CODE.md`

**If you have 1 hour:**
1. Read `github-copilot-sdk.md` from top to bottom
2. Review relevant language sections deeply
3. Study the server integration examples

**If you're implementing:**
1. Start with `IMPLEMENTATION_ROADMAP.txt`
2. Follow `IMPLEMENTATION_CHECKLIST.md`
3. Copy from `COPY_PASTE_CODE.md`
4. Reference `github-copilot-sdk.md` as needed

---

## ✅ Implementation Checklist

- [ ] Read documentation above
- [ ] Install Copilot CLI: https://docs.github.com/en/copilot/how-tos/set-up/install-copilot-cli
- [ ] Install SDK for your language
- [ ] Choose authentication method
- [ ] Create minimal test script (copy from examples)
- [ ] Test LLM calls work
- [ ] Design your custom tools
- [ ] Build agent server (Express/FastAPI)
- [ ] Test A2UI integration
- [ ] Deploy

---

## 🎓 Learning Resources

### Official
- **Main README:** https://github.com/github/copilot-sdk#readme
- **Getting Started:** https://github.com/github/copilot-sdk/blob/main/docs/getting-started.md
- **Auth Docs:** https://github.com/github/copilot-sdk/blob/main/docs/auth/index.md
- **BYOK Setup:** https://github.com/github/copilot-sdk/blob/main/docs/auth/byok.md

### Cookbooks (Practical Examples)
- **TypeScript:** https://github.com/github/awesome-copilot/blob/main/cookbook/copilot-sdk/nodejs/README.md
- **Python:** https://github.com/github/awesome-copilot/blob/main/cookbook/copilot-sdk/python/README.md
- **Go:** https://github.com/github/awesome-copilot/blob/main/cookbook/copilot-sdk/go/README.md

### Community
- **Awesome Copilot:** https://github.com/github/awesome-copilot
- **Issue Tracker:** https://github.com/github/copilot-sdk/issues

---

## ⚠️ Important Notes

1. **Technical Preview** — SDK subject to breaking changes
2. **CLI Required** — Must install Copilot CLI separately
3. **Copilot Subscription** — Required unless using BYOK
4. **Async-first** — All APIs are async/await
5. **Streaming** — Use event handlers for real-time responses
6. **Session Management** — Always disconnect and stop client properly

---

## 🎯 Key File Purposes

| File | Size | Purpose | Read Time |
|------|------|---------|-----------|
| **This file** | - | Overview & quick start | 5 min |
| `RESEARCH_COMPLETE_SUMMARY.md` | 10 KB | Detailed summary of all findings | 10 min |
| `github-copilot-sdk.md` | 32 KB | Comprehensive technical reference | 30 min |
| `COPY_PASTE_CODE.md` | 19 KB | Ready-to-use code examples | 5 min |
| `IMPLEMENTATION_ROADMAP.txt` | 14 KB | Step-by-step integration guide | 15 min |
| `IMPLEMENTATION_CHECKLIST.md` | 8 KB | Task-by-task checklist | 10 min |

---

## 🚀 Next Steps

1. **Install Copilot CLI** (if not already done)
2. **Install SDK** for your language
3. **Run minimal example** from documentation
4. **Define your tools** for your use case
5. **Build server** integrating SDK + your tools
6. **Test LLM calls** with real prompts
7. **Deploy** to your environment

---

## 💡 Pro Tips

- ✅ Always use `onPermissionRequest` handler for security
- ✅ Use `sendAndWait()` for simple patterns, event handlers for streaming
- ✅ Define tools with Zod schemas (TypeScript) for validation
- ✅ Use BYOK if you want to avoid Copilot subscription costs
- ✅ Start with `approveAll` for testing, implement proper permission logic later
- ✅ Test with environment variables for CI/CD integration
- ✅ Keep CLI updated: `copilot update`

---

**Status: ✅ Research Complete**  
**Confidence: 99% on core functionality**  
**Ready to implement: YES**

Start with `RESEARCH_COMPLETE_SUMMARY.md` next →

