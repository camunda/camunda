---
name: Operate Notebook visual polish requirement
description: Demo requires "mindblowing super pretty" widgets — polish is a first-class requirement, not nice-to-have
type: project
---

Visual polish is a **first-class demo requirement** for the Operate Notebook hackday. Stephan's exact words: "a few widgets that are mindblowing super pretty." This raises the quality bar above "functional" and shapes what gets built and what gets cut.

**Why:** The demo's wow factor is "type prompt → beautiful widget appears." If widgets look utilitarian or janky, the magic evaporates. This is the difference between a memorable demo and a forgettable one.

**How to apply (priority order — cut from the bottom up if time-pressured):**

1. **Widget appear animation** — when LLM finishes and widget mounts, fade-in/slide-up ~200ms eased. ~20 min. **Do not cut.** This IS the magic moment.
2. **Prompt input polish** — large, centered, friendly placeholder, Carbon `<InlineLoading>` or shimmer while LLM works. ~30 min. **Do not cut.**
3. **Empty state** — welcoming, intentional, not "TODO." First thing demo viewers see. ~30 min. **Do not cut.**
4. **Chart styling** — Carbon Charts defaults are dry. Custom Operate-aligned colors, proper labels, sensible tooltips, no truncated legends. ~30 min × 3 subtypes.
5. **BPMN overlay polish** — heatmap colored by stuck count, saturation by intensity. The "uniquely Operate" widget — must look stunning. ~1h.
6. **Layout/spacing consistency** — 12-col grid, consistent gutters, uniform widget headers. ~30 min upfront.
7. **Loading skeletons** — use Carbon's `<DataTableSkeleton>` etc. properly per widget type.

**Cut order under time pressure:** chart `donut` subtype → BPMN overlay heatmap (ship plain BPMN) → Phase 4 error boundaries (let React Query error states show) → never cut #1-3 above.

**Polish budget:** ~4-5h total spread across phases. Treat as engineering work, not "leftover time at the end."

**Agent implication:** `ui-qa` is now load-bearing for SHIP verdict — not just a11y/Carbon compliance, but actual visual polish review. `product-reviewer` similarly weighted toward "does this look impressive?" not just "does the UX make sense?"
