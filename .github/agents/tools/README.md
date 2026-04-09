# Shared Agent Tools

This directory contains reusable tools, scripts, and utilities created by engineering agents and shared across the team.

## How to contribute a tool

1. Create the tool file here: `.github/agents/tools/<tool-name>.<ext>`
2. Add an entry to this README with: tool name, what it does, which agent created it, usage example
3. Update your own cheatsheet to reference the new tool
4. Add a Session Log entry to any other agent cheatsheets that would benefit from this tool (Android Expert + iOS Expert cross-notify each other; Python Expert notifies Tester and Debugger; etc.)

## Available Tools

*(No tools yet — add yours here)*

| Tool | Purpose | Created By | Usage |
|---|---|---|---|
| *(empty)* | | | |

## Tool Standards
- Scripts must be executable (`chmod +x`)
- Include a usage comment block at the top of every script
- Tools must be idempotent where possible (safe to run multiple times)
- No secrets or tokens hardcoded — use environment variables
