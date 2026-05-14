---
name: Operate Notebook widget architecture
description: How widgets are structured and dispatched, and how BPMN reuse works
type: project
---

Notebook widgets follow a **registry/dispatch pattern** in `WidgetRenderer`, with one component per widget type.

**Why:** Stephan wants a flexible foundation that can grow beyond BPMN to other visualization types — but without one mega-component. Registry pattern keeps each widget simple and independent. Adding a type = adding a registry entry.

**Findings on existing Diagram component (verified 2026-05-07):**
- `modules/components/Diagram/index.tsx` (257 lines) takes all data via props (`xml`, `overlaysData`, etc.). The `observer()` wrap is for MobX-derived props from consumers, but Diagram itself imports zero stores.
- `modules/components/DiagramShell` (78 lines) is a thin status wrapper.
- **Reuse is clean** — pass XML + overlays as plain props. No shared-code refactor needed. Not in "ask first" territory.

**How to apply:**
- Initial widget types: `table`, `bpmn`, `metric`, `action-panel`. Charts deferred (see Q6).
- `WidgetRenderer` dispatches on `config.type` via a registry map. No giant switch statement creep — adding a type = adding a registry entry.
- Each widget in its own file under `operate/client/src/App/Notebooks/widgets/`.
- BPMN widget: use existing `Diagram` + `DiagramShell` read-only. Fetch XML via React Query. No selection callbacks for hackday.
- `metric` widget: a `<Tile>` showing `page.totalItems` (or a single field) from any `/search` endpoint. Cheap, demo-gold.
