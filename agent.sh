#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$REPO_DIR/logs"
LLM_PID="$LOG_DIR/agent-llm.pid"
TEMPLATE_PID="$LOG_DIR/agent-template.pid"
LLM_LOG="$LOG_DIR/agent-llm.log"
TEMPLATE_LOG="$LOG_DIR/agent-template.log"
PORT=8000

mkdir -p "$LOG_DIR"

# ── helpers ──────────────────────────────────────────────────────────────────

_port_pid() { lsof -ti:"$PORT" 2>/dev/null || true; }

_running_agent() {
  local pid
  pid="$(_port_pid)"
  [[ -z "$pid" ]] && { echo "none"; return; }
  if [[ -f "$LLM_PID" ]] && grep -q "^$pid$" "$LLM_PID" 2>/dev/null; then
    echo "llm"
  elif [[ -f "$TEMPLATE_PID" ]] && grep -q "^$pid$" "$TEMPLATE_PID" 2>/dev/null; then
    echo "template"
  else
    echo "unknown($pid)"
  fi
}

_wait_for_port() {
  local i=0
  while ! lsof -ti:"$PORT" >/dev/null 2>&1; do
    sleep 1; i=$((i+1))
    [[ $i -ge 5 ]] && return 1
  done
  return 0
}

# ── setup ─────────────────────────────────────────────────────────────────────

_setup_one() {
  local agent="$1"
  local dir=""

  if [[ "$agent" == "llm" ]]; then
    dir="$REPO_DIR/agent"
  else
    echo "Unknown agent '$agent'. Use: llm" >&2
    return 1
  fi

  if ! command -v python3 >/dev/null 2>&1; then
    echo "ERROR: python3 not found. Install Python 3 and try again." >&2
    exit 1
  fi

  echo "==> Setting up $agent agent..."

  if [[ -d "$dir/.venv" ]]; then
    echo "     venv already exists -- reinstalling requirements..."
  else
    echo "     Creating venv in $dir/.venv ..."
    python3 -m venv "$dir/.venv"
  fi

  echo "     Installing requirements..."
  (
    cd "$dir"
    source .venv/bin/activate
    pip install -q --upgrade pip
    pip install -q -r requirements.txt
  )
  echo "OK:  $agent agent setup complete."
}

_setup() {
  local target="${1:-}"
  if [[ -z "$target" ]]; then
    echo "Usage: $0 setup llm" >&2; exit 1
  fi
  case "$target" in
    llm)
      _setup_one llm
      ;;
    *)
      echo "Unknown target '$target'. Use: llm" >&2; exit 1 ;;
  esac
}

# ── start ─────────────────────────────────────────────────────────────────────

_start() {
  local agent="${1:-}"
  if [[ -z "$agent" ]]; then
    echo "Usage: $0 start llm" >&2; exit 1
  fi

  local already
  already="$(_running_agent)"
  if [[ "$already" != "none" ]]; then
    echo "❌  An agent is already running on port $PORT ($already). Stop it first with: $0 stop" >&2
    exit 1
  fi

  case "$agent" in
    llm)
      [[ -d "$REPO_DIR/agent/.venv" ]] || { echo "⚙  No venv found — running setup first…"; _setup_one llm; }
      echo "▶  Starting LLM agent (serves both /chat/stream and /chat/stream/template)…"
      (
        cd "$REPO_DIR/agent"
        source .venv/bin/activate
        exec python agent.py
      ) >> "$LLM_LOG" 2>&1 &
      echo $! > "$LLM_PID"
      ;;
    *)
      echo "Unknown agent '$agent'. Use: llm" >&2; exit 1 ;;
  esac

  if _wait_for_port; then
    echo "✅  Agent '$agent' is up on http://localhost:$PORT"
  else
    echo "⚠️  Agent '$agent' started (PID $(cat "$LLM_PID")) but port $PORT not yet open — check logs with: $0 logs" >&2
  fi
}

# ── stop ──────────────────────────────────────────────────────────────────────

_stop() {
  local agent running pid
  running="$(_running_agent)"

  if [[ "$running" == "none" ]]; then
    echo "ℹ️  No agent is running on port $PORT."
    return 0
  fi

  # prefer PID file; fall back to lsof
  if [[ "$running" == "llm" && -f "$LLM_PID" ]]; then
    pid="$(cat "$LLM_PID")"
  elif [[ "$running" == "template" && -f "$TEMPLATE_PID" ]]; then
    pid="$(cat "$TEMPLATE_PID")"
  else
    pid="$(_port_pid)"
  fi

  echo "⏹  Stopping $running agent (PID $pid)…"
  kill "$pid" 2>/dev/null || true
  local i=0
  while kill -0 "$pid" 2>/dev/null; do
    sleep 1; i=$((i+1))
    [[ $i -ge 5 ]] && { kill -9 "$pid" 2>/dev/null || true; break; }
  done

  rm -f "$LLM_PID" "$TEMPLATE_PID"
  echo "✅  Stopped."
}

# ── status ────────────────────────────────────────────────────────────────────

_status() {
  local running pid uptime log_file
  running="$(_running_agent)"

  echo "═══════════════════════════════════════"
  echo "  A2UI Agent Status"
  echo "═══════════════════════════════════════"

  if [[ "$running" == "none" ]]; then
    echo "  Status : stopped"
    echo "  Port   : $PORT (free)"
  else
    pid="$(_port_pid)"
    uptime="$(ps -o etime= -p "$pid" 2>/dev/null | tr -d ' ' || echo '?')"
    log_file="$LOG_DIR/agent-${running}.log"
    echo "  Status : running"
    echo "  Agent  : $running"
    echo "  PID    : $pid"
    echo "  Uptime : $uptime"
    echo "  Port   : $PORT"
    echo "  Log    : $log_file"
    echo ""
    echo "── Last 20 log lines ──────────────────"
    [[ -f "$log_file" ]] && tail -20 "$log_file" || echo "(no log yet)"
  fi
  echo "═══════════════════════════════════════"
}

# ── logs ──────────────────────────────────────────────────────────────────────

_logs() {
  local agent="${1:-}"
  local log_file

  if [[ -z "$agent" ]]; then
    # auto-detect most recent log
    if [[ -f "$LLM_LOG" ]]; then
      log_file="$LLM_LOG"
    elif [[ -f "$TEMPLATE_LOG" ]]; then
      log_file="$TEMPLATE_LOG"
    else
      echo "No log files found in $LOG_DIR" >&2; exit 1
    fi
  else
    case "$agent" in
      llm)      log_file="$LLM_LOG" ;;
      *) echo "Unknown agent '$agent'. Use: llm" >&2; exit 1 ;;
    esac
  fi

  [[ -f "$log_file" ]] || { echo "Log not found: $log_file" >&2; exit 1; }
  echo "Tailing $log_file (Ctrl-C to stop)…"
  tail -f "$log_file"
}

# ── restart ───────────────────────────────────────────────────────────────────

_restart() {
  _stop
  sleep 1
  _start "${1:-}"
}

# ── usage ─────────────────────────────────────────────────────────────────────

_usage() {
  cat <<EOF
Usage: $(basename "$0") <command> [args]

Commands:
  setup llm             Create venv and install requirements (auto-runs on first start)
  start llm             Start the server in the background (serves both routes)
  stop                  Stop the agent running on port $PORT
  restart llm           Stop then start the agent
  status                Show running agent, PID, uptime, and last 20 log lines
  logs [llm]            Tail the log (defaults to agent-llm.log)

The merged server exposes two routes — the Android toggle chip selects which to use at runtime:
  POST /chat/stream           LLM path  (GitHub Copilot SDK, requires GITHUB_TOKEN)
  POST /chat/stream/template  Template path (deterministic, no API key, instant)

Examples:
  ./agent.sh setup llm        # one-time setup (creates .venv + installs deps)
  ./agent.sh start llm        # starts server with both LLM + template routes
                               # Android toggle chip selects which route to use
  ./agent.sh status
  ./agent.sh stop
  ./agent.sh logs
EOF
}

# ── dispatch ──────────────────────────────────────────────────────────────────

CMD="${1:-}"
shift || true

case "$CMD" in
  setup)   _setup "$@" ;;
  start)   _start "$@" ;;
  stop)    _stop ;;
  restart) _restart "$@" ;;
  status)  _status ;;
  logs)    _logs "$@" ;;
  "")      _usage ;;
  *)       echo "Unknown command: $CMD" >&2; _usage >&2; exit 1 ;;
esac
