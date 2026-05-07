---
name: Operate Notebook LLM model & prompt defaults
description: Starting-point model/prompt choices for the notebook copilot, all easily swappable
type: project
---

Notebook copilot starting defaults (all revisitable once interface is testable):
- Model: Claude Sonnet 4.5
- Single-shot (no streaming)
- Tool-use for enforced JSON output (not freeform "respond with JSON")
- System prompt: hand-curated cheat-sheet of the ~10 widget-relevant V2 endpoints, ~1-2 KB

**Why:** Stephan wants to defer the real model/prompt tuning until we can actually type prompts and see results. So pick sensible defaults that won't embarrass us, but make swapping trivial.

**How to apply:**
- All of {model name, system prompt text, endpoint cheat-sheet, tool schema} live in **one config file** (e.g. `copilot/config.ts`). One-line swap to change model.
- Revisit after Phase 3 is wired: if latency >5s → try Haiku; if JSON quality poor → tighten tool schema; if curated endpoints insufficient → expand list.
- Do not scatter model strings or prompt fragments across the codebase.
