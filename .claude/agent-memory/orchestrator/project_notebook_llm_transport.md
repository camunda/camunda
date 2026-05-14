---
name: Operate Notebook LLM transport decision
description: Hackday decision on how the notebook copilot calls the LLM and handles the API key
type: project
---

Operate Notebook copilot calls Anthropic API **directly from the browser**. Key is read from `import.meta.env.VITE_ANTHROPIC_API_KEY`, loaded from `operate/client/.env.local` (gitignored by Vite). No backend proxy, no Java changes.

**Why:** Hackday — simplest path. Stephan's only hard constraint: no keys in git history.

**How to apply:**
- Never commit `.env.local` or any file containing `sk-ant-*`. Grep staged diffs for `sk-ant-` before every commit touching copilot code.
- Copilot UI must degrade gracefully when key is missing (clear message, no crash).
- Add a TODO at the top of the copilot module: "revoke key after hackday — it is exposed in browser bundle/network tab."
- Acknowledge in any demo: the key is visible in devtools at runtime; do not share the branch or a live URL publicly.
- If security-auditor flags this in Phase 3.5, the answer is "accepted hackday risk" — not a redesign.
