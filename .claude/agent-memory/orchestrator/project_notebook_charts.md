---
name: Operate Notebook charts decision
description: Charts are central to the demo; using Carbon Charts with isolation for future shadcn migration
type: project
---

Notebook charts use **Carbon Charts (`@carbon/charts-react`)**. Stephan judged charts the most important demo feature ("will mindblow people"). New dep — requires "ask first" per AGENTS.md before merge.

Camunda may migrate from Carbon to shadcn in the near future. Code must be structured so a future chart-lib swap is contained.

**Why:** Carbon Charts gives bar/line/donut/etc. for free, matches Operate's design language, takes simple config objects (LLM-friendly). Stephan considered D3 but accepted Carbon Charts as the right hackday-scope choice.

**How to apply:**
- Initial chart subtypes: `bar`, `line`, `donut`. No others without explicit ask.
- Carbon Charts imports live **only** inside `operate/client/src/App/Notebooks/widgets/ChartWidget.tsx`. No other file imports `@carbon/charts-react`.
- Widget config uses **library-agnostic terms** (`chartType: 'bar' | 'line' | 'donut'`, generic data shape — not Carbon Charts-specific options). Config must survive a chart-lib swap. The translation from generic config → Carbon Charts options happens inside ChartWidget only.
- This isolation pattern is the migration insurance: shadcn (or whatever replaces Carbon Charts) = rewrite one file, change one dep, no impact on LLM prompts/configs/persisted notebooks.
- Pre-merge: someone (Stephan or orchestrator-on-his-behalf) needs to confirm the dep addition is OK with frontend dep owners.
