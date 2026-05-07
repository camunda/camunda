---
name: Operate Notebook demo script & multi-widget generation
description: Locked-in demo script (variant A'); LLM must support generating N widgets in one shot
type: project
---

**Locked-in demo script (Script A'):**

1. "Set me up with a Monday morning view for our process health" → 4 widgets cascade in with staggered animation (metric, bar chart, BPMN+heatmap, table)
2. "Add a trend chart for incidents over the last 24h" → 5th widget (line chart)
3. "That stuck task — which instances are affected?" → 6th widget (table, contextual to BPMN heatmap)
4. Reload browser → everything persists

Stretch (if time): variant C ("Something looks off this morning") to demo vague-intent interpretation.

**Critical capability — multi-widget generation:**

The LLM contract is `prompt → array of WidgetConfig (length 1+)`. A single-widget response is just length 1. This is simpler than two code paths.

**Why:** Stephan's call. A dashboard materializing from one prompt is more cinematic than four prompts each adding one widget. It's THE wow moment.

**How to apply:**
- Tool schema: `generate_widgets({ widgets: [...] })`. Always an array.
- System prompt teaches when to generate many vs. one: vague/dashboard/view intent → 3-5 cohesive widgets; specific intent → exactly 1. Cap at 6.
- System prompt demands **diversity** — each widget must show a different aspect, not duplicate.
- Order matters: most impactful first (heatmap > metric > table).
- Validation: any config in batch fails → retry whole batch once. Whole-batch retry, not per-config.
- Latency: 4-config response is ~6-9s. Acceptable because cascade animation masks it.

**Cascade animation (do not cut):**
- Stagger widget mounts 150ms apart
- Each: opacity 0→1 + translateY(8px → 0) over 200ms, eased
- Total cascade for 4 widgets: ~800ms
- Without this, multi-widget reveal feels flat

**Pre-demo dress-rehearsal protocol:** before going on stage, run the locked-in prompts 5x in a row and confirm reliability. If variance is high, tighten system prompt or rephrase the prompt itself. Few-shot examples in the system prompt help here.
