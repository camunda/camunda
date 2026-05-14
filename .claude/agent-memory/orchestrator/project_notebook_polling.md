---
name: Operate Notebook data freshness & polling
description: How widgets auto-refresh and how manual refresh is exposed
type: project
---

Widget auto-refresh is **LLM-controlled via the config** (`refreshMs` field), implemented through **TanStack Query**'s `refetchInterval` (the library formerly known as React Query — package: `@tanstack/react-query`). Every widget also gets a manual refresh button.

**Why:** Operate already uses TanStack Query, which provides both auto-polling and manual refetch for free. Letting the LLM choose the interval per widget matches the prompt's intent (live-monitoring views vs. historical snapshots) without adding any infrastructure.

**How to apply:**
- Widget config: optional `refreshMs?: number`. Omitted = no auto-refresh.
- `WidgetRenderer` wires this into `useQuery({ refetchInterval: refreshMs ?? false })`.
- Every widget header has a Carbon refresh icon button → `refetch()`.
- **Defensive bounds:** clamp `refreshMs` to `[2000, 300000]` ms in the renderer. Prevents LLM typos (`100`, `5000000`) from wrecking demos.
- **System prompt guidance:** LLM should pick ~10000ms for live-monitoring intent, ~30000ms for less-volatile, omit for static/historical.
- React Query's default `refetchIntervalInBackground: false` is fine — auto-pause when tab hidden.
- Action-panel mutations: on `useMutation` success, call `queryClient.invalidateQueries()` coarsely (invalidate everything in the notebook). Cheap, correct enough for hackday.
