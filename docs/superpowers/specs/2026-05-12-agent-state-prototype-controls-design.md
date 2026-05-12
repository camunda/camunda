# Agent state prototype controls — design

**Date:** 2026-05-12
**Branch:** `3462-real-time-agent-visibility-prototype`
**Scope:** Operate frontend prototype only (`operate/client/`)

## Goal

Let designers/PMs switch the Operate agent demo between four agent-state snapshots so each state's UI (canvas, instance history, details tab) can be reviewed without code edits. The four states:

1. **Agent not yet active** — the AI Agent BPMN element exists but has no element-instance.
2. **Agent active** — the existing demo: one agent run mid-tool-call.
3. **Agent completed** — one agent run that has finished.
4. **Agent with multiple element instances** — two AI Agent runs: the first completed, then `User_Feedback` returned `userSatisfied = false`, the agent restarted; the second is currently active.

## Non-goals

- Real backend integration. All state lives in `modules/mock-server/`.
- Per-state UI animations / transitions. Switching between states is a navigation (URL change).
- Tests beyond the existing `historyToAgentElementData` transform tests. This is prototype work.

## Architecture

### Control surface — dev nav menu

The existing app header already renders one `Demo: <name>` nav item per `SCENARIOS[]` entry (DEV-only, via `import.meta.env.DEV` gate in `operate/client/src/App/Layout/AppHeader/index.tsx`). `SCENARIOS` grows from 1 entry to 4 — one per state. No new UI component, no settings icon, no popover. Each state is a separate `processInstanceKey` and therefore a separate URL.

Suggested nav labels:

- Demo: Agent not yet active
- Demo: Agent active
- Demo: Agent completed
- Demo: Multiple element instances

### Scenario registry shape

`ScenarioDefinition` today has singular `agentInstance: AgentInstance` and `agentInstanceHistory: HistoryElement[]`. For state #4 there are two of each, paired by which element-instance they belong to. Replace those fields with one grouped array:

```ts
agentInstances: Array<{
  instance: AgentInstance;            // the API payload
  elementInstanceKey: string;         // the AD_HOC_SUB_PROCESS instance this run belongs to
  history: HistoryElement[];          // history for this run
}>;
```

States 1–3 have a 0- or 1-length array. State 4 has length 2.

The existing `agentElementInstanceKey: string` field on ScenarioDefinition becomes redundant — derivable from `agentInstances[*].elementInstanceKey`. Remove it.

### `agentData` context — multi-instance support

Today:

```ts
const agentInstance = agentInstancesResult?.items[0];          // takes first
const {data: historyResult} = useAgentInstanceHistory(...);    // one fetch
const data: Record<string, AgentElementData> = {
  [scenario.agentElementInstanceKey]: elementData,             // one key
};
```

Changes:

1. Keep all `AgentInstance`s returned by `useSearchAgentInstances`, not just `[0]`.
2. Replace the single `useAgentInstanceHistory` call with `useQueries` from TanStack Query — one query per `AgentInstance`, fanned out dynamically.
3. Pair each loaded history with the element-instance key it belongs to. The pairing is reported by the mock handler — see "Mock-server handlers" below.
4. Build `agentData: Record<elementInstanceKey, AgentElementData>` with one entry per loaded run (one key in States 1–3, two in State 4).

Context field renames for clarity (singular → multi):

| Today | After |
|---|---|
| `agentSubprocessKey: string \| null` | `primaryAgentElementInstanceKey: string \| null` |
| (n/a) | `agentElementInstanceKeys: string[]` |

`primaryAgentElementInstanceKey` is the fallback used when the user clicks `AI_Agent` on the diagram and `resolvedElementInstance` can't disambiguate (the BPMN element id matches multiple instances). It's the active run if one exists, else the most recent.

### DetailsTab — pick the right agent run

```ts
const sourceKey =
  resolvedElementInstance?.elementInstanceKey
  ?? primaryAgentElementInstanceKey;
const agentData = sourceKey ? getAgentDataForElement(sourceKey) : null;
```

- When the user selects a specific row in instance-history → `resolvedElementInstance` is set → look up that run.
- When the user clicks `AI_Agent` on the diagram → ambiguous in state #4 → fall back to the active run.

`elementInstanceKey` (the variable also used to gate `useJobs` etc.) keeps its current derivation: `resolvedElementInstance?.elementInstanceKey ?? (showAgentContent ? agentSubprocessInstance?.elementInstanceKey ?? null : null)`. `agentSubprocessInstance` is fetched from `primaryAgentElementInstanceKey`.

### StatusAccordion — Completed-state rendering

In `App/ProcessInstance/BottomPanelTabs/AiAgentTab/AgentDetailPanel.tsx`:

- Add `'COMPLETED' → 'Completed'` to the `statusLabels` map.
- Switch the accordion title icon based on status:
  - In-progress states (`INITIALIZING`, `TOOL_DISCOVERY`, `THINKING`, `WAITING_FOR_TOOL`) → keep `Time`.
  - `COMPLETED` → `CheckmarkOutline` from `@carbon/icons-react`.
  - `FAILED` is out of scope for this spec; the same switch can be extended later (e.g., `WarningAltFilled`).
- Hide the "Tool calls" chip section when `agentData.status === 'COMPLETED'`. Add the gate to the existing `activeTools.length > 0` block.
- `currentMessage` falls back to `lastIteration.reasoning` when no active tool call exists, so the "exit message" (the last assistant text) renders without further change.

### Canvas overlays — status tag and shine

`buildActiveAgentStatuses` in `modules/contexts/agentData.tsx` already gates the orchestrating-agent entry on `status !== 'COMPLETED' && status !== 'FAILED'`. That handles:

- State 1: no `AgentInstance` → no `activeAgentStatuses` entries → no tag, no shine.
- State 3: status `COMPLETED` → gate excludes the orchestrating agent.

For state 4, the orchestrating-agent entry uses status from the **active** run (`CALLING_TOOL`), not the completed one — so the tag stays "Calling tools…" on the active `AI_Agent` element. The completed run never produces a status entry.

For the nested `AI_Task_Agent` entry: keep the existing demo wiring (always pushed in `buildActiveAgentStatuses`) gated on the same overall status check — i.e., drop it when there's no in-flight agent run. Concretely: skip pushing the nested entry when the parent agent's `status` is `COMPLETED` or `FAILED`, or when no agent is loaded.

### Mock-server handlers

Two small updates in `modules/mock-server/handlers.ts`:

- `POST /v2/agent-instances/search`: filter `scenario.agentInstances` first by request body's `processInstanceKey` (matches against `instance.processInstanceKey`), then by `elementInstanceKey` if present (matches against the entry's `elementInstanceKey` pairing field). Return `.map(a => a.instance)`. The `elementInstanceKey` filter lets the context look up "which agent ran at this BPMN instance" when needed.
- `POST /v2/agent-instances/{key}/history/search`: find the `agentInstances` entry whose `instance.agentInstanceKey` matches the URL parameter; return its `history`.

### Element selectability

`useSelectableElements` already derives selectability from the presence of element-instances. State 1 omits `AI_Agent` from `elementInstances` and `MOCK_AGENT_ELEMENT_STATISTICS` → `AI_Agent` is not selectable. No `isSelectable` flag plumbing required.

### Execution-count badge (state 4)

Per the chosen approach: mock data exposes `completed: 1` for `AI_Agent` in state 4. The badge becomes visible when the user toggles Operate's existing "Execution count" control in the diagram toolbar (`executionCountToggleStore.isExecutionCountVisible`). No auto-enable; no per-element bypass.

## Per-state mock data shape

### State 1 — `agent-not-active`

- `processInstance.state`: `'ACTIVE'`.
- `elementInstances`:
  - `StartEvent_1` — `COMPLETED`.
  - `Gateway_0z6ctwk` — `COMPLETED`.
- `elementStatistics.items`:
  - `StartEvent_1` — `{ completed: 1 }`.
  - `Gateway_0z6ctwk` — `{ completed: 1 }`.
- `agentInstances`: `[]`.
- `sequenceFlows`: `Flow_0pbzrme` taken.
- `variables`: only `inputText` (the user's prompt).
- `jobs`: `[]`.

### State 2 — `agent-active` (current scenario, with bug fix)

Same as today's `MOCK_AGENT_*` fixtures with one fix: **remove the `User_Feedback` `ACTIVE` element-instance** and **remove its `active: 1` row from `MOCK_AGENT_ELEMENT_STATISTICS`**. The user feedback step has not been reached.

### State 3 — `agent-completed`

- `processInstance.state`: `'ACTIVE'` (still — agent is one step; the process keeps going to `User_Feedback`).
- `elementInstances`:
  - `StartEvent_1` — `COMPLETED`.
  - `Gateway_0z6ctwk` — `COMPLETED`.
  - `AI_Agent` (outer) — `COMPLETED`. Has `endDate`.
  - All four inner-instance wrappers — `COMPLETED`.
  - All child tool tasks (`ListUsers`, `LoadUserByID`, `GetDateAndTime`, `AskHumanToSendEmail`, `AI_Task_Agent`) — `COMPLETED`.
  - **No `User_Feedback` element-instance** (snapshot taken at the moment the agent finishes; we sidestep the question of whether `User_Feedback` has started).
- `elementStatistics.items`:
  - `StartEvent_1`, `Gateway_0z6ctwk`, `AI_Agent`, each tool — all `{ completed: 1 }`.
- `agentInstances`: one entry with `instance.status: 'COMPLETED'` and the full history (all assistant messages, all tool_call + tool_result pairs).

### State 4 — `agent-multiple-element-instances`

Element-instance timeline, in order:

1. `StartEvent_1` `COMPLETED`.
2. `Gateway_0z6ctwk` `COMPLETED` (pass 1).
3. `AI_Agent` #1 `COMPLETED` + all its inner instances + all its tool tasks `COMPLETED` (this run's full history is what the current "active" scenario's history looks like at the moment of completion).
4. `User_Feedback` `COMPLETED` with `userSatisfied = false`.
5. `Flow_19gp461` sequence-flow taken (the "no — we follow up" arrow).
6. `Gateway_0z6ctwk` `COMPLETED` (pass 2, second element-instance).
7. `AI_Agent` #2 `ACTIVE` with the same in-flight inner instances we have in state 2 — `AskHumanToSendEmail` `ACTIVE`, `AI_Task_Agent` `ACTIVE`.

- `elementStatistics.items` for `AI_Agent`: `{ active: 1, completed: 1 }`. The completed-count badge becomes visible when the user toggles Operate's Execution-count control.
- `elementStatistics.items` for `Gateway_0z6ctwk`: `{ completed: 2 }` (gateway runs once per agent pass).
- `elementStatistics.items` for each child tool task that ran in AI_Agent #1: `{ completed: 1 }`. Children of AI_Agent #2 mirror state 2's per-tool counts.
- `agentInstances` has two entries:
  - One with `instance.status: 'COMPLETED'`, `elementInstanceKey` = AI_Agent #1's outer key, full final history.
  - One with `instance.status: 'CALLING_TOOL'`, `elementInstanceKey` = AI_Agent #2's outer key, in-flight history (same shape as state 2).
- `variables`: include `userSatisfied: false`.

## File-level summary

New / changed files:

| Path | Change |
|---|---|
| `modules/mock-server/agentDemoData/agentInstanceData.ts` | Add per-state `AgentInstance` + `HistoryElement[]` exports. Keep the file structure flat — one file, multiple named exports (e.g., `MOCK_AGENT_INSTANCE_ACTIVE`, `MOCK_AGENT_HISTORY_ELEMENTS_ACTIVE`, `MOCK_AGENT_INSTANCE_COMPLETED`, etc.). |
| `modules/mock-server/agentDemoData/agentProcessInstance.ts` | Likewise — per-state `MOCK_AGENT_ELEMENT_INSTANCES_*`, `MOCK_AGENT_ELEMENT_STATISTICS_*`, `MOCK_AGENT_SEQUENCE_FLOWS_*`, `MOCK_AGENT_VARIABLES_*`, `MOCK_AGENT_JOBS_*` named exports. |
| `modules/mock-server/agentDemoData/constants.ts` | New `MOCK_AGENT_*` constant groups for the new scenarios' keys. |
| `modules/mock-server/agentDemoData/index.ts` | Export the new datasets. |
| `modules/mock-server/scenarioRegistry.ts` | `ScenarioDefinition`: replace `agentInstance` + `agentInstanceHistory` + `agentElementInstanceKey` with `agentInstances: Array<{instance, elementInstanceKey, history}>`. Add three new SCENARIOS entries. |
| `modules/mock-server/handlers.ts` | Update the two agent-instance endpoints per "Mock-server handlers" above. |
| `modules/contexts/agentData.tsx` | `useQueries`-based multi-instance loading; rename `agentSubprocessKey` → `primaryAgentElementInstanceKey`; add `agentElementInstanceKeys: string[]`. Adjust `buildActiveAgentStatuses` to gate the nested-task entry on overall status. |
| `App/ProcessInstance/BottomPanelTabs/DetailsTab/index.tsx` | Use `resolvedElementInstance?.elementInstanceKey ?? primaryAgentElementInstanceKey` when looking up agent data; update the `useElementInstance` call to use the renamed field. |
| `App/ProcessInstance/BottomPanelTabs/AiAgentTab/AgentDetailPanel.tsx` | Add `'COMPLETED' → 'Completed'` to `statusLabels`; conditional icon (`Time` / `CheckmarkOutline`); hide "Tool calls" chips when status is `COMPLETED`. |
| `App/ProcessInstance/TopPanel/index.tsx` | No code change beyond following the renamed context fields (`activeAgentStatuses` consumer is unchanged in shape). |

No changes to:

- `AppHeader/index.tsx` — the `SCENARIOS.map(...)` already renders all entries.
- The BPMN XML — same `agentBpmnXml.ts` is reused across all four scenarios.
- `historyToAgentElementData.ts` — the transform stays exactly as-is.
- `ShineBorderOverlay.tsx` / `AgentStatusOverlay.tsx` — driven by `activeAgentStatuses`, no per-state logic needed.

## Risks and tradeoffs

- **Mock-data volume**: state 4 roughly doubles the size of `agentInstanceData.ts` and `agentProcessInstance.ts`. Tradeoff accepted (Approach A from the brainstorm) because derivation/transform would be more code than just writing the data, and the explicit-data form is much easier to debug and tweak.
- **`agentData` context refactor**: `useQueries` introduces dynamic-count parallel fetching. Existing call sites consume `activeAgentStatuses` and `getAgentDataForElement(key)`, both of which keep their shape — so the blast radius outside the context file is the rename `agentSubprocessKey → primaryAgentElementInstanceKey` and the DetailsTab lookup change.
- **State 3 corner cases**: snapshot taken at "agent just completed" means no `User_Feedback` instance yet. If a reviewer asks "what happens after the agent completes — wouldn't User_Feedback be active?", the answer is: yes in reality, but for the demo we capture the moment between the two so we can isolate the agent-completed UI without confounding it with a separate user-task UI.
- **Execution-count badge discoverability (state 4)**: per the chosen approach, the user must toggle Execution count manually to see the "1" badge. If demo recipients consistently miss it, we can revisit the auto-enable option later.

## Open questions

None remaining.

## Out of scope (deferred)

- `FAILED` agent state (icon, message). Spec leaves the icon-switch structure in place but doesn't define the failed-state visuals.
- Multiple nested `AI_Task_Agent` instances (per-iteration). Today the prototype shows one nested-task tag; remains unchanged here.
- Real API integration. The `agentInstances` grouping in `ScenarioDefinition` is a mock-side convenience; the real API has separate endpoints and the pairing is implicit via filters.
