---
name: Operate Notebook endpoint & mutation policy
description: Hackday decision on what V2 endpoints widgets can call and how mutations are gated
type: project
---

Notebook widgets can call **any V2 endpoint** the user is authorized for. No allowlist, no confirmation modals on mutations.

**Why:** Hackday demo runs against toy clusters. Stephan explicitly accepted the risk. He noted that a productionized version would later restrict to read-only + a specific allowlist of writes — but not now.

**How to apply:**
- WidgetRenderer passes whatever endpoint/method/body the config specifies straight through Operate's existing `request/` utility (inherits CSRF + auth).
- One free structural guardrail: `query` runs on widget render (reads); `actions[]` runs only on user click. The schema and system prompt should encode "mutations belong in `actions`," but the renderer does not enforce this — if a mutation ends up in `query`, it fires on render. Stephan accepted this.
- If security-auditor flags this in Phase 2.8: answer is "accepted hackday risk, toy clusters only, productionization will add allowlist." Not a redesign.
- Add a TODO at the top of WidgetRenderer: "productionize: restrict to read endpoints + allowlist of writes."
