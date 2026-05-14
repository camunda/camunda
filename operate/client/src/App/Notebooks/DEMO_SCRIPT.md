<!--
Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
one or more contributor license agreements. See the NOTICE file distributed
with this work for additional information regarding copyright ownership.
Licensed under the Camunda License 1.0. You may not use this file
except in compliance with the Camunda License 1.0.
-->

# Operate Notebooks — Hackday demo (3 minutes)

> A Jupyter-style canvas inside Operate where natural-language prompts generate live, V2-API-backed dashboards.

---

## Slide 1 — *Problem* (20 seconds)

> **Operate's dashboard is static. We decided years ago what every customer sees — the same tables, the same tabs, the same charts, for everyone.**

But customers don't come to Operate for one use case. The SRE on Monday morning wants a health overview. The same person at 3pm — debugging a production incident — wants a heatmap of where tokens are stuck. The platform owner wants worker capacity. The compliance lead wants throughput trends. **Same product, same data, wildly different questions.**

Their only escape hatch today: click through three pages and read tables — or file a feature request and wait a quarter.

---

## Slide 2 — *Solution* (15 seconds)

> **I got inspired by Jupyter notebooks** — a blank canvas where you type what you want and live cells appear. So I built that for Operate.

Operate Notebooks is a Jupyter-style canvas inside Operate. Type what you want, an LLM generates a dashboard backed by live V2-API queries, and it renders in seconds. The app becomes adaptive — it shapes itself to whatever situation the user is in.

> **Create as many dashboards as you want, as many as you need** — one per process, one per situation, one per Monday morning. Each notebook lives at its own URL, persists across reloads, and is yours to shape.

Two paths to a dashboard:
1. Curated suggestion pills → instant deterministic templates
2. Free-text → real LLM (AWS Bedrock, Sonnet 4.5) generates widgets on the fly

---

## Slide 3 — *Demo* (~2 minutes, live in browser)

### Setup before stage

- Browser open to `http://localhost:3000/notebooks/test1`
- Camunda cluster running at `host:8080` with seeded data (89+ instances, 30+ incidents)
- Vite dev server running with the AWS Bedrock credentials in `.env.local`
- Hard-refresh once just before going on

### Beat 1 — One pill, full dashboard (35 sec)

**Click "Showcase all widgets"** in the right sidebar.

> "I'll start with our showcase — one click, every widget type the notebook can render, all backed by live V2 queries."

Widgets cascade in (90ms staggered) — about 14 of them. While they animate, narrate:

> "Live metrics — active instances, incidents. Trend sparklines from real time-bucketed queries. Carbon Charts — donut, pie, stacked bar, stacked area. A per-process status grid. The BPMN heatmap — the saturation comes from incident count per task. An interleaved activity stream. A conversion funnel through `order-process`. And a live incidents table at the bottom."

Wait for them to settle. Pause for effect.

### Beat 2 — The provenance moment (15 sec)

Hover any widget. Click the `</>` icon.

> "Every widget shows its description and the actual V2 endpoint. Nothing here is fabricated — this is `/v2/incidents/statistics/process-instances-by-error`, with the body and field mapping the LLM produced."

Close the modal.

### Beat 3 — The real LLM (60 sec)

> "Pills are curated for safety. Now the magic moment — type something completely arbitrary."

Click "Clear all widgets" (top-right). Then in the prompt textarea:

```
How is order-process doing today, and where are tokens stuck?
```

Click **Generate**. Spinner appears. About 10–15 seconds wait.

> "That's hitting AWS Bedrock — Sonnet 4.5 — directly from the browser via SigV4-signed calls. While it thinks, what it's seeing: the system prompt I wrote it knows the V2 API surface, the widget config schema, layout rules, the height tiers. It returns a JSON tool call with widget configs."

Widgets appear. Narrate what happened:

> "It chose: a metric of active instances, the BPMN heatmap of order-process — see the dark red node, that's `Task_Validate` with 25 stuck — a chart of incidents by error type, and a table of the stuck instances. It built this dashboard in 12 seconds, never seen this prompt before."

### Beat 4 — Process awareness (20 sec)

In the prompt, type:

```
Compare order-process to payment-process side by side
```

Click Generate. 15 seconds.

> "And it understands process names from the prompt — left side order-process, right side payment-process, BPMN diagrams of both. The widget configs come back referring to specific `processDefinitionKey`s. Even works when the user types a name we never explicitly enumerated."

---

## Total time: ~3 minutes

| Section | Seconds |
|---|---|
| Slide 1: Problem | 20 |
| Slide 2: Solution | 15 |
| Slide 3 / Beat 1: showcase pill | 35 |
| Slide 3 / Beat 2: provenance modal | 15 |
| Slide 3 / Beat 3: live LLM prompt | 60 |
| Slide 3 / Beat 4: comparison prompt | 20 |
| **Total** | **~165s** |

Pad with audience reaction time. If you're getting close to the wire, drop Beat 4.

End the demo by leaving the live LLM-generated dashboard on screen — that's the closing image. No wrap slide needed; the working app is the punchline.

---

## Pre-flight checklist

Before the demo (run 30 minutes before):

- [ ] `bash operate/client/scripts/seed-demo-data.sh` (refreshes incidents on Task_Validate)
- [ ] `cd operate/client && npm start -- --host 0.0.0.0` is running
- [ ] Open `http://localhost:3000/notebooks/showcase` (or any id) and refresh once to pre-warm caches
- [ ] Verify `.env.local` has `VITE_AWS_BEDROCK_ARN`, `VITE_AWS_ACCESS_KEY_ID`, `VITE_AWS_SECRET_ACCESS_KEY`
- [ ] Test the live LLM with a single safe prompt to make sure the Bedrock IPs are still in the firewall allowlist
- [ ] Have a fallback prompt ready in case a response is dud — `Show me a Monday morning view` (pill, instant, never fails)

## Bail-out paths

If Bedrock fails mid-demo, click any pill instead. The pill prompts route to static presets that are deterministic and instant — same widget types, same data. The audience won't know the difference unless you tell them.

If the BPMN doesn't render, the diagram has likely been deployed but `processDefinitionId` lookup failed. Open `/v2/process-definitions/search` directly in a separate tab to confirm `order-process` is deployed.

## What NOT to demo

- Don't open the React DevTools — fragile.
- Don't toggle to a non-existent process; the BPMN error state appears and breaks the flow.
- Don't paste 1000 chars into the prompt. Keep prompts under 80 chars; they read better on stage.
