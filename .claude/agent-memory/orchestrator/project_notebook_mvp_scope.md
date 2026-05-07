---
name: Operate Notebook MVP scope (overrides earlier phase plan)
description: Stripped-down MVP — route, prompt, LLM, two widgets, localStorage. Iterate from there.
type: project
---

**MVP scope — overrides the earlier multi-phase plan.** Stephan's direction: stop planning everything up front, build the thinnest end-to-end loop, then iterate based on real friction.

**What's in MVP (target ~3.5h agent work):**
1. Route `/notebooks/:id` — loads from localStorage if exists, auto-creates empty notebook if not
2. Page: prompt input + widget area, debounced save on every change
3. LLM call: Anthropic browser-direct (key from `.env.local`), tool-use returns array of widget configs
4. `MetricWidget` (Carbon `<Tile>` + TanStack Query)
5. `TableWidget` (existing `DataTable` + TanStack Query)
6. `WidgetRenderer` dispatch — metric/table only
7. Phase 0 seed: deploy 1 BPMN + ~10 instances (10 min, not the elaborate version)

**What's NOT in MVP — restore as iteration candidates in priority order:**
1. `chart` widget (Carbon Charts — defer dep approval until needed)
2. `bpmn` widget with heatmap overlay
3. Multi-widget cascade animation
4. Zod validation + retry loop (MVP: render what LLM returns; failures are visible and fixed reactively)
5. Notebook switcher / delete / rename
6. Polish pass (empty state, prompt input styling, animations)
7. Error boundaries
8. `refreshMs` auto-refresh and refresh buttons

**Why:** Earlier I (orchestrator) was building a 13-17h plan up front. Stephan wants a 3-4h MVP that proves the architecture, then iterate. All the design decisions still apply when their feature is added — they just don't all ship in MVP.

**How to apply:**
- Don't dispatch agents for items not in MVP scope.
- After MVP smoke-tests end-to-end, have an explicit "what hurt the most?" check with Stephan before deciding the next iteration.
- Resist the urge to add "while I'm in there" features during MVP build — agents should be told MVP scope explicitly.
