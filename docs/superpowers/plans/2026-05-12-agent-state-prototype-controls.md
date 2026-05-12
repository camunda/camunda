# Agent State Prototype Controls — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three new scenarios beside the existing "agent active" demo (not-yet-active, completed, multiple element instances) so designers/PMs can switch between four agent-state snapshots from the existing dev nav menu, with the canvas / instance history / details panel rendering the right things per state.

**Architecture:** Each state is a separate `SCENARIOS[]` entry with a unique `processInstanceKey` (= unique URL = one DEV nav item). `ScenarioDefinition` grows a grouped `agentInstances: Array<{instance, elementInstanceKey, history}>` field replacing today's singular agent-instance + history + element-instance-key fields, so state #4 can carry two agent runs. `agentData` context fans out history fetches via `useQueries` keyed by element-instance, exposing one `AgentElementData` per run. UI: state-aware status icon (`Time` → `CheckmarkOutline`), conditional tool-calls section in StatusAccordion, and the existing `activeAgentStatuses` gate already drops the canvas tag/shine for completed and not-yet-active.

**Tech Stack:** React 18, TypeScript, Vite, MSW v2, TanStack Query (`useQueries`), `requestWithThrow`, Carbon Design System, Styled Components.

---

## Prototype context — what this is and isn't

This is **design-prototype work** under `operate/client/`. Per `CLAUDE.md` "Design Prototypes (UI-Only Work)": all data is mocked under `modules/mock-server/`, no tests are required for these changes, and existing tests must continue to pass. The `historyToAgentElementData` transform stays untouched (its three Vitest sanity checks should keep passing).

Branch state: there are uncommitted changes already in `operate/client/` from prior brainstorming iterations (status-tag styling, AI_Task_Agent nested element, DetailsTab/StructuredList unification, multiple `activeAgentStatuses` support). This plan builds on top of that working-tree state.

---

## Required reading (one pass before starting)

- `docs/superpowers/specs/2026-05-12-agent-state-prototype-controls-design.md` — the spec this plan implements.
- `.github/instructions/frontend.instructions.md` — TanStack Query / Styled Components / component layout conventions (don't introduce new patterns).
- `operate/client/src/modules/mock-server/scenarioRegistry.ts` — the registry shape being refactored.
- `operate/client/src/modules/mock-server/handlers.ts` — MSW handlers being updated.
- `operate/client/src/modules/contexts/agentData.tsx` — the context being extended for multi-instance loading.
- `operate/client/src/modules/queries/agentInstances/types.ts` — types reused (no changes here).
- `operate/client/src/App/ProcessInstance/BottomPanelTabs/AiAgentTab/AgentDetailPanel.tsx` — Completed-state rendering goes in `StatusAccordion`.

---

## File structure — what gets created, modified, deleted

### Modify

| Path | Responsibility |
|---|---|
| `operate/client/src/modules/mock-server/agentDemoData/constants.ts` | Existing keys get `_ACTIVE` suffix; add new constants for each new scenario. |
| `operate/client/src/modules/mock-server/agentDemoData/agentInstanceData.ts` | Rename existing exports with `_ACTIVE` suffix; add new exports per state. |
| `operate/client/src/modules/mock-server/agentDemoData/agentProcessInstance.ts` | Rename existing exports with `_ACTIVE` suffix; remove `User_Feedback` ACTIVE from the active scenario; add new exports per state. |
| `operate/client/src/modules/mock-server/agentDemoData/index.ts` | Re-export new datasets. |
| `operate/client/src/modules/mock-server/scenarioRegistry.ts` | `ScenarioDefinition` shape change; four `SCENARIOS` entries. |
| `operate/client/src/modules/mock-server/handlers.ts` | Two endpoint handlers updated for the new `agentInstances` shape. |
| `operate/client/src/modules/contexts/agentData.tsx` | `useQueries`-based fan-out; rename `agentSubprocessKey → primaryAgentElementInstanceKey`; expose `agentElementInstanceKeys: string[]`; gate nested-task entry on parent status. |
| `operate/client/src/App/ProcessInstance/BottomPanelTabs/DetailsTab/index.tsx` | Use the renamed context field; pick the right agent run via `resolvedElementInstance?.elementInstanceKey ?? primaryAgentElementInstanceKey`. |
| `operate/client/src/App/ProcessInstance/BottomPanelTabs/AiAgentTab/AgentDetailPanel.tsx` | `'COMPLETED' → 'Completed'` status label; conditional icon (`Time` / `CheckmarkOutline`); hide "Tool calls" chip section when status is COMPLETED. |

### Not modified

- `operate/client/src/modules/mock-server/agentDemoData/agentBpmnXml.ts` — reused across all four scenarios.
- `operate/client/src/modules/queries/agentInstances/historyToAgentElementData.ts` — transform is unchanged; tests stay green.
- `operate/client/src/App/ProcessInstance/TopPanel/index.tsx` — consumes `activeAgentStatuses` which keeps its shape.
- `operate/client/src/App/Layout/AppHeader/index.tsx` — already maps over `SCENARIOS`; new entries surface automatically.

---

## Naming conventions for new mock data

State suffixes applied to every per-state export and constant: `_NOT_ACTIVE`, `_ACTIVE`, `_COMPLETED`, `_MULTIPLE`.

Process-instance key ranges (distinct first digit per state for grep-ability):

- State 1 (not active): `5451799813685***`
- State 2 (active, **existing**): `4451799813685***` (unchanged)
- State 3 (completed): `6451799813685***`
- State 4 (multiple): `7451799813685***`

Within each range, follow the existing offsets: `…000` = process-instance, `…010` = AI_Agent outer subprocess, `…015–019` = inner instance wrappers, `…020–045` = child tool task element-instances, `…200` = agent-instance.

For State 4 (two agent runs), the second run reuses the suffix `_2`: `MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2`, `MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_2`, etc., with keys at `7451799813685100..199` for the second run.

---

## Task 1: Refactor `ScenarioDefinition` to a multi-run `agentInstances` field

**Files:**
- Modify: `operate/client/src/modules/mock-server/scenarioRegistry.ts`
- Modify: `operate/client/src/modules/mock-server/handlers.ts`

This is a structural refactor with **no behavior change** — the existing single scenario keeps producing identical mock responses, just sourced through a new field shape.

- [ ] **Step 1: Update `ScenarioDefinition` type**

In `scenarioRegistry.ts`, replace the three singular fields with one grouped array. Old (current):

```ts
agentElementId: string;
agentElementInstanceKey: string;
agentElementIds: Set<string>;
// ...
agentInstance: AgentInstance;
agentInstanceHistory: HistoryElement[];
```

New:

```ts
agentElementId: string;
agentElementIds: Set<string>;
// ...
agentInstances: Array<{
  instance: AgentInstance;
  // BPMN element-instance key this agent run belongs to.
  elementInstanceKey: string;
  history: HistoryElement[];
}>;
```

(Remove `agentElementInstanceKey`, `agentInstance`, `agentInstanceHistory`.)

- [ ] **Step 2: Migrate the existing `SCENARIOS[0]` entry to the new shape**

Still in `scenarioRegistry.ts`, replace the three singular field assignments in the existing entry with:

```ts
agentInstances: [
  {
    instance: MOCK_AGENT_INSTANCE,
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY,
    history: MOCK_AGENT_HISTORY_ELEMENTS,
  },
],
```

Leave `agentElementId: 'AI_Agent'` and `agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS` as-is for now (they're renamed in Task 2).

- [ ] **Step 3: Update `handlers.ts` to consume `agentInstances`**

The two affected handlers are the agent-instance search and the per-instance history search. Locate the existing handlers (search for `agentInstance` and `agent-instances` strings) and rewrite:

```ts
// POST /v2/agent-instances/search
http.post('/v2/agent-instances/search', async ({request}) => {
  const body = await request.json() as {
    filter?: {processInstanceKey?: string; elementInstanceKey?: string};
  };
  const scenario = SCENARIOS.find(
    (s) => s.instanceKey === body.filter?.processInstanceKey,
  );
  if (!scenario) {
    return HttpResponse.json({items: [], total: 0});
  }
  const entries = scenario.agentInstances.filter((entry) => {
    if (
      body.filter?.elementInstanceKey &&
      entry.elementInstanceKey !== body.filter.elementInstanceKey
    ) {
      return false;
    }
    return true;
  });
  return HttpResponse.json({
    items: entries.map((e) => e.instance),
    total: entries.length,
  });
}),

// POST /v2/agent-instances/:agentInstanceKey/history/search
http.post(
  '/v2/agent-instances/:agentInstanceKey/history/search',
  ({params}) => {
    const {agentInstanceKey} = params as {agentInstanceKey: string};
    for (const scenario of SCENARIOS) {
      const entry = scenario.agentInstances.find(
        (e) => e.instance.agentInstanceKey === agentInstanceKey,
      );
      if (entry) {
        return HttpResponse.json({items: entry.history, total: entry.history.length});
      }
    }
    return HttpResponse.json({items: [], total: 0});
  },
),
```

When updating the existing handlers, preserve:
- The MSW v2 `http.post(...)` wrapper style already in `handlers.ts`.
- Existing `committed: true` filter on history items (if the request body sets it) — apply it after the per-scenario lookup: `entry.history.filter((h) => h.committed === true)`.
- The standard `HttpResponse.json(...)` return shape.

Do not introduce `passthrough()` or change unrelated handlers in the file.

- [ ] **Step 4: Verify build + behavior**

```bash
cd /Users/zsofia/Documents/GitHub/camunda/operate/client
npx prettier --check src/modules/mock-server/scenarioRegistry.ts src/modules/mock-server/handlers.ts
npx vitest run src/modules/queries/agentInstances/historyToAgentElementData.test.ts
```

Expected: prettier passes, 3/3 transform tests pass.

Manually: `npm run start`, open the existing `Demo: Agent chat with tools` link, confirm the canvas + status tag + agent panel still render the same as before.

- [ ] **Step 5: Commit**

```bash
git add operate/client/src/modules/mock-server/scenarioRegistry.ts operate/client/src/modules/mock-server/handlers.ts
git commit -m "$(cat <<'EOF'
refactor: group ScenarioDefinition's agent fields into a multi-run array

Replaces singular agentInstance + agentInstanceHistory + agentElementInstanceKey
with a single agentInstances: Array<{instance, elementInstanceKey, history}> so
a scenario can carry more than one agent run (needed for the upcoming "multiple
element instances" demo state). No behavior change for the existing scenario.
EOF
)"
```

---

## Task 2: Rename existing mock-data exports with `_ACTIVE` suffix

**Files:**
- Modify: `operate/client/src/modules/mock-server/agentDemoData/constants.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/agentInstanceData.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/agentProcessInstance.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/index.ts`
- Modify: `operate/client/src/modules/mock-server/scenarioRegistry.ts`

This is a pure rename, no behavior change. After this, every per-state mock export has a consistent suffix.

- [ ] **Step 1: Rename constants in `constants.ts`**

Apply these renames to the existing exports (using a global find-and-replace within the file):

```
MOCK_AGENT_INSTANCE_KEY          → MOCK_AGENT_INSTANCE_KEY_ACTIVE
MOCK_AGENT_DEFINITION_KEY        → MOCK_AGENT_DEFINITION_KEY_ACTIVE
MOCK_AGENT_DEFINITION_ID         → MOCK_AGENT_DEFINITION_ID_ACTIVE
MOCK_AGENT_SUBPROCESS_KEY        → MOCK_AGENT_SUBPROCESS_KEY_ACTIVE
MOCK_AGENT_INNER_INSTANCE_1_KEY  → MOCK_AGENT_INNER_INSTANCE_1_KEY_ACTIVE
MOCK_AGENT_INNER_INSTANCE_2_KEY  → MOCK_AGENT_INNER_INSTANCE_2_KEY_ACTIVE
MOCK_AGENT_INNER_INSTANCE_3_KEY  → MOCK_AGENT_INNER_INSTANCE_3_KEY_ACTIVE
MOCK_AGENT_INNER_INSTANCE_4_KEY  → MOCK_AGENT_INNER_INSTANCE_4_KEY_ACTIVE
MOCK_AGENT_INNER_INSTANCE_5_KEY  → MOCK_AGENT_INNER_INSTANCE_5_KEY_ACTIVE
MOCK_AGENT_TASK_AGENT_INSTANCE_KEY → MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_ACTIVE
MOCK_AGENT_SUBPROCESS_ELEMENT_IDS → MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_ACTIVE
MOCK_AGENT_AGENT_INSTANCE_KEY    → MOCK_AGENT_AGENT_INSTANCE_KEY_ACTIVE
```

- [ ] **Step 2: Rename exports in `agentInstanceData.ts`**

```
MOCK_AGENT_INSTANCE          → MOCK_AGENT_INSTANCE_ACTIVE
MOCK_AGENT_HISTORY_ELEMENTS  → MOCK_AGENT_HISTORY_ELEMENTS_ACTIVE
```

Also update all internal references to the renamed constants from Step 1.

- [ ] **Step 3: Rename exports in `agentProcessInstance.ts`**

```
MOCK_AGENT_PROCESS_INSTANCE   → MOCK_AGENT_PROCESS_INSTANCE_ACTIVE
MOCK_AGENT_PROCESS_DEFINITION → MOCK_AGENT_PROCESS_DEFINITION_ACTIVE
MOCK_AGENT_ELEMENT_INSTANCES  → MOCK_AGENT_ELEMENT_INSTANCES_ACTIVE
MOCK_AGENT_ELEMENT_STATISTICS → MOCK_AGENT_ELEMENT_STATISTICS_ACTIVE
MOCK_AGENT_SEQUENCE_FLOWS     → MOCK_AGENT_SEQUENCE_FLOWS_ACTIVE
MOCK_AGENT_VARIABLES          → MOCK_AGENT_VARIABLES_ACTIVE
MOCK_AGENT_JOBS               → MOCK_AGENT_JOBS_ACTIVE
```

Update internal constant references.

- [ ] **Step 4: Update `index.ts` re-exports and `scenarioRegistry.ts` consumers**

In `agentDemoData/index.ts`, re-export the renamed identifiers. In `scenarioRegistry.ts`, update the existing entry to reference the renamed identifiers (`instanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE`, etc.).

- [ ] **Step 5: Verify**

```bash
cd /Users/zsofia/Documents/GitHub/camunda/operate/client
npx prettier --check src/modules/mock-server/agentDemoData/ src/modules/mock-server/scenarioRegistry.ts
npx --no-install tsc --noEmit -p tsconfig.json 2>&1 | grep "error TS" | head -10
```

Expected: prettier passes, no TS errors. Reload the existing demo, confirm UI unchanged.

- [ ] **Step 6: Commit**

```bash
git add operate/client/src/modules/mock-server/agentDemoData/ operate/client/src/modules/mock-server/scenarioRegistry.ts
git commit -m "$(cat <<'EOF'
refactor: suffix existing agent-demo mock exports with _ACTIVE

Pure rename to make room for new per-state mock data (NOT_ACTIVE, COMPLETED,
MULTIPLE). No behavior change.
EOF
)"
```

---

## Task 3: Fix the `User_Feedback` ACTIVE bug in the existing scenario

**Files:**
- Modify: `operate/client/src/modules/mock-server/agentDemoData/agentProcessInstance.ts`

The current `_ACTIVE` scenario has `User_Feedback` as an ACTIVE element-instance and an `active: 1` statistics row, but at this point in the flow the agent is still running and `User_Feedback` hasn't been reached yet.

- [ ] **Step 1: Remove the `User_Feedback` element-instance**

In `MOCK_AGENT_ELEMENT_INSTANCES_ACTIVE`, find and delete the element-instance entry with `elementId: 'User_Feedback'`:

```ts
// DELETE this block entirely
{
  elementInstanceKey: '4451799813685040',
  // ...
  elementId: 'User_Feedback',
  // ...
  state: 'ACTIVE',
  // ...
},
```

- [ ] **Step 2: Remove the `User_Feedback` row from `MOCK_AGENT_ELEMENT_STATISTICS_ACTIVE`**

```ts
// DELETE this entry from .items
{
  elementId: 'User_Feedback',
  active: 1,
  canceled: 0,
  incidents: 0,
  completed: 0,
},
```

- [ ] **Step 3: Verify**

```bash
cd /Users/zsofia/Documents/GitHub/camunda/operate/client
npx prettier --check src/modules/mock-server/agentDemoData/agentProcessInstance.ts
```

Manually: reload the active demo, confirm `User_Feedback` on the canvas is no longer highlighted as active (no token, no badge).

- [ ] **Step 4: Commit**

```bash
git add operate/client/src/modules/mock-server/agentDemoData/agentProcessInstance.ts
git commit -m "fix: User_Feedback should not be active while AI Agent is running"
```

---

## Task 4: `agentData` context — multi-instance fetching + status-aware nested-task gate

**Files:**
- Modify: `operate/client/src/modules/contexts/agentData.tsx`

After this, the context supports loading and exposing multiple `AgentElementData` per scenario, keyed by element-instance.

- [ ] **Step 1: Rename context fields**

Locate the `AgentDataContextValue` interface and apply these renames:

```diff
-  agentSubprocessKey: string | null;
+  agentElementInstanceKeys: string[];
+  primaryAgentElementInstanceKey: string | null;
```

Update `EMPTY_VALUE` to match: `agentElementInstanceKeys: []`, `primaryAgentElementInstanceKey: null`.

- [ ] **Step 2: Switch single history fetch to parallel `useQueries`**

Replace the single `useAgentInstanceHistory(agentInstance?.agentInstanceKey, ...)` call with a `useQueries` block that fans out one query per returned `AgentInstance`. Source:

```ts
import {useQueries} from '@tanstack/react-query';
import {searchAgentInstanceHistory} from 'modules/api/v2/agentInstances/searchAgentInstanceHistory';

// inside AgentDataProvider, after agentInstancesResult is fetched:
const allAgentInstances = agentInstancesResult?.items ?? [];

const historyResults = useQueries({
  queries: allAgentInstances.map((instance) => ({
    queryKey: ['agentInstanceHistory', instance.agentInstanceKey, {committed: true}],
    queryFn: async () => {
      const {response, error} = await searchAgentInstanceHistory(
        instance.agentInstanceKey,
        {filter: {committed: true}},
      );
      if (error) throw error;
      return response;
    },
  })),
});
```

(If `useAgentInstanceHistory` is no longer used anywhere after this change, leave the hook file in place — it's harmless and the schemas-package migration may still need it.)

- [ ] **Step 3: Build a per-element-instance `agentData` map**

Replace the singular `data: Record<string, AgentElementData>` assignment with one entry per loaded history. The pairing of `agentInstanceKey → elementInstanceKey` lives in `scenario.agentInstances`. Read it from there:

```ts
const data: Record<string, AgentElementData> = {};
for (let i = 0; i < allAgentInstances.length; i++) {
  const instance = allAgentInstances[i]!;
  const historyItems = historyResults[i]?.data?.items;
  if (!historyItems) continue;
  const scenarioEntry = scenario.agentInstances.find(
    (e) => e.instance.agentInstanceKey === instance.agentInstanceKey,
  );
  if (!scenarioEntry) continue;
  data[scenarioEntry.elementInstanceKey] = historyToAgentElementData(instance, historyItems);
}
```

- [ ] **Step 4: Compute `primaryAgentElementInstanceKey` and `agentElementInstanceKeys`**

```ts
const agentElementInstanceKeys = scenario.agentInstances.map((e) => e.elementInstanceKey);
// Prefer an active run, else the last entry.
const activeEntry = scenario.agentInstances.find(
  (e) => e.instance.status !== 'COMPLETED' && e.instance.status !== 'FAILED',
);
const primaryAgentElementInstanceKey =
  activeEntry?.elementInstanceKey ??
  scenario.agentInstances[scenario.agentInstances.length - 1]?.elementInstanceKey ??
  null;
```

Return these on the context value (replacing the old `agentSubprocessKey`).

- [ ] **Step 5: Gate `buildActiveAgentStatuses` on parent agent status**

Locate `buildActiveAgentStatuses` and change it to drop the nested-task entry when the parent agent's status is COMPLETED/FAILED. Replace the existing body with:

```ts
function buildActiveAgentStatuses(
  elementData: AgentElementData,
  agentElementId: string,
): ActiveAgentStatus[] {
  const isLive =
    elementData.status !== 'COMPLETED' && elementData.status !== 'FAILED';
  if (!isLive) return [];
  return [
    {
      elementId: agentElementId,
      label: 'Calling tools...',
      showShine: true,
    },
    {
      elementId: NESTED_TASK_AGENT_ELEMENT_ID,
      label: NESTED_TASK_AGENT_LABEL,
      showShine: false,
    },
  ];
}
```

- [ ] **Step 6: Pick the right `elementData` for `activeAgentStatuses` in the multi-instance case**

The context currently builds `activeAgentStatuses` from a single `elementData`. With multiple runs, we want it to reflect the **active** run (so state #4 shows "Calling tools…" tied to the active AI_Agent, not the completed one). Update the assembly:

```ts
const activeAgentData =
  primaryAgentElementInstanceKey !== null
    ? data[primaryAgentElementInstanceKey]
    : null;

// inside the returned object:
activeAgentStatuses: activeAgentData
  ? buildActiveAgentStatuses(activeAgentData, scenario.agentElementId)
  : [],
```

- [ ] **Step 7: Verify**

```bash
cd /Users/zsofia/Documents/GitHub/camunda/operate/client
npx prettier --check src/modules/contexts/agentData.tsx
npx --no-install tsc --noEmit -p tsconfig.json 2>&1 | grep "error TS" | head -10
npx vitest run src/modules/queries/agentInstances/historyToAgentElementData.test.ts
```

Expected: prettier passes, no TS errors, 3/3 transform tests pass. Reload the active demo, confirm everything still works (status tag, shine border, agent details panel).

- [ ] **Step 8: Commit**

```bash
git add operate/client/src/modules/contexts/agentData.tsx
git commit -m "$(cat <<'EOF'
feat: support multiple agent runs in agentData context

Replaces the single-history fetch with a useQueries fan-out, builds the
per-element-instance agentData map from the scenario's pairing, and exposes
agentElementInstanceKeys + primaryAgentElementInstanceKey for callers. Gates
the canvas status tags on the active run's status so completed/failed scenarios
render without overlays.
EOF
)"
```

---

## Task 5: DetailsTab — follow rename + pick the right run on selection

**Files:**
- Modify: `operate/client/src/App/ProcessInstance/BottomPanelTabs/DetailsTab/index.tsx`

- [ ] **Step 1: Replace `agentSubprocessKey` destructuring with the new fields**

Find:

```ts
const {
  isAgentElement,
  agentSubprocessKey,
  getAgentDataForElement,
  // ...
} = useAgentData();
```

Replace with:

```ts
const {
  isAgentElement,
  primaryAgentElementInstanceKey,
  agentElementInstanceKeys,
  getAgentDataForElement,
  // ...
} = useAgentData();
```

- [ ] **Step 2: Update `showAgentContent` to use the new fields**

Find:

```ts
const showAgentContent =
  isAgentElement(selectedElementId) && agentSubprocessKey !== null;
```

Replace with:

```ts
const showAgentContent =
  isAgentElement(selectedElementId) && primaryAgentElementInstanceKey !== null;
```

- [ ] **Step 3: Update the `useElementInstance` fallback to use `primaryAgentElementInstanceKey`**

Find:

```ts
const {data: agentSubprocessInstance} = useElementInstance(
  agentSubprocessKey ?? '',
  {enabled: showAgentContent && !!agentSubprocessKey},
);
```

Replace with:

```ts
const {data: agentSubprocessInstance} = useElementInstance(
  primaryAgentElementInstanceKey ?? '',
  {enabled: showAgentContent && !!primaryAgentElementInstanceKey},
);
```

- [ ] **Step 4: Update the `agentData` useMemo to pick by selection**

Find:

```ts
const agentData = useMemo(() => {
  if (!showAgentContent || !agentSubprocessKey) {
    return null;
  }
  return getAgentDataForElement(agentSubprocessKey);
}, [showAgentContent, agentSubprocessKey, getAgentDataForElement]);
```

Replace with:

```ts
const agentData = useMemo(() => {
  if (!showAgentContent) {
    return null;
  }
  // When the user picks a specific row in instance-history, resolvedElementInstance
  // pins us to one agent run. When the click is on the BPMN diagram and matches
  // multiple instances, fall back to the active one (primary).
  const lookupKey =
    resolvedElementInstance?.elementInstanceKey ?? primaryAgentElementInstanceKey;
  if (!lookupKey) return null;
  return getAgentDataForElement(lookupKey);
}, [
  showAgentContent,
  resolvedElementInstance,
  primaryAgentElementInstanceKey,
  getAgentDataForElement,
]);
```

- [ ] **Step 5: Verify**

```bash
cd /Users/zsofia/Documents/GitHub/camunda/operate/client
npx prettier --check src/App/ProcessInstance/BottomPanelTabs/DetailsTab/index.tsx
npx --no-install tsc --noEmit -p tsconfig.json 2>&1 | grep "error TS" | head -10
```

Reload the active demo, select the AI_Agent — confirm details panel still shows the current "Calling tools…" content.

- [ ] **Step 6: Commit**

```bash
git add operate/client/src/App/ProcessInstance/BottomPanelTabs/DetailsTab/index.tsx
git commit -m "$(cat <<'EOF'
refactor: route DetailsTab agent lookup through resolvedElementInstance

Switches to the renamed primaryAgentElementInstanceKey and prefers the user's
resolved selection when looking up agent data — so when the same BPMN element
has multiple instances, picking a specific row in instance-history shows that
run's details.
EOF
)"
```

---

## Task 6: AgentDetailPanel — Completed-state rendering

**Files:**
- Modify: `operate/client/src/App/ProcessInstance/BottomPanelTabs/AiAgentTab/AgentDetailPanel.tsx`

- [ ] **Step 1: Add `CheckmarkOutline` to the icon imports**

Find the `@carbon/icons-react` import block and add `CheckmarkOutline`:

```ts
import {
  Maximize,
  Time,
  CheckmarkOutline,
  MeterAlt,
  // ...existing imports
} from '@carbon/icons-react';
```

- [ ] **Step 2: Add `'COMPLETED'` to `statusLabels` in `StatusAccordion`**

Find the `statusLabels` map inside `StatusAccordion`:

```ts
const statusLabels: Record<string, string> = {
  INITIALIZING: 'Initializing',
  TOOL_DISCOVERY: 'Discovering tools...',
  THINKING: 'Thinking...',
  WAITING_FOR_TOOL: 'Calling tools',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
};
```

(`COMPLETED` and `FAILED` may already be present — confirm and leave as-is if so.)

- [ ] **Step 3: Switch the title icon based on status**

Just above the `accordionTitle` JSX in `StatusAccordion`, add:

```ts
const StatusIcon = agentData.status === 'COMPLETED' ? CheckmarkOutline : Time;
```

Then in the `accordionTitle` JSX, replace `<Time size={16} />` with `<StatusIcon size={16} />`.

- [ ] **Step 4: Hide the "Tool calls" chip section when status is COMPLETED**

Find the JSX block beginning with `{activeTools.length > 0 && (`. Change the guard to:

```tsx
{agentData.status !== 'COMPLETED' && activeTools.length > 0 && (
  // existing chip-row JSX
)}
```

- [ ] **Step 5: Verify**

```bash
cd /Users/zsofia/Documents/GitHub/camunda/operate/client
npx prettier --check src/App/ProcessInstance/BottomPanelTabs/AiAgentTab/AgentDetailPanel.tsx
npx --no-install tsc --noEmit -p tsconfig.json 2>&1 | grep "error TS" | head -10
```

Reload the active demo — current "Calling tools" state should be unchanged (icon stays `Time`, chips still render).

- [ ] **Step 6: Commit**

```bash
git add operate/client/src/App/ProcessInstance/BottomPanelTabs/AiAgentTab/AgentDetailPanel.tsx
git commit -m "$(cat <<'EOF'
feat: render Completed agent status with checkmark icon, hide tool chips

Adds a CheckmarkOutline icon swap to StatusAccordion when the agent status is
COMPLETED, and gates the active-tool-calls chip section so it disappears in the
completed view (the message body — last assistant text — still renders).
EOF
)"
```

---

## Task 7: State 1 — "Agent not yet active" scenario

**Files:**
- Modify: `operate/client/src/modules/mock-server/agentDemoData/constants.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/agentProcessInstance.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/index.ts`
- Modify: `operate/client/src/modules/mock-server/scenarioRegistry.ts`

Goal: a snapshot where `StartEvent_1` and `Gateway_0z6ctwk` are complete and the agent hasn't activated yet. `AI_Agent` exists in the BPMN diagram but has no element-instance (→ not selectable).

- [ ] **Step 1: Add new constants in `constants.ts`**

Append at the bottom of the file:

```ts
// State 1 — Agent not yet active. No AI_Agent instance; only the entry events.
export const MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE = '5451799813685000';
export const MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE = '5451799813685099';
export const MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE = 'ai-agent-chat-with-tools';
```

(Definition id is shared — same BPMN.)

- [ ] **Step 2: Add new exports in `agentProcessInstance.ts`**

Append at the bottom of the file:

```ts
import {
  // ...existing imports
  MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
  MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
  MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
} from './constants';

export const MOCK_AGENT_PROCESS_INSTANCE_NOT_ACTIVE: ProcessInstance = {
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
  processDefinitionName: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
  processDefinitionVersion: 1,
  processDefinitionVersionTag: null,
  tenantId: '<default>',
  state: 'ACTIVE',
  startDate: '2026-03-26T14:30:00.000Z',
  endDate: null,
  hasIncident: false,
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  rootProcessInstanceKey: null,
  tags: [],
};

export const MOCK_AGENT_PROCESS_DEFINITION_NOT_ACTIVE: ProcessDefinition = {
  name: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
  resourceName: 'ai-agent-chat-with-tools.bpmn',
  version: 1,
  versionTag: null,
  tenantId: '<default>',
  hasStartForm: false,
};

export const MOCK_AGENT_ELEMENT_INSTANCES_NOT_ACTIVE: MockElementInstance[] = [
  {
    elementInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
    elementId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
    elementName: 'AI Agent Chat With Tools',
    type: 'PROCESS',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.000Z',
    endDate: null,
    incidentKey: null,
  },
  {
    elementInstanceKey: '5451799813685001',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
    elementId: 'StartEvent_1',
    elementName: 'Task to perform received',
    type: 'START_EVENT',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.100Z',
    endDate: '2026-03-26T14:30:00.200Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '5451799813685005',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
    elementId: 'Gateway_0z6ctwk',
    elementName: null,
    type: 'EXCLUSIVE_GATEWAY',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.250Z',
    endDate: '2026-03-26T14:30:00.280Z',
    incidentKey: null,
  },
];

export const MOCK_AGENT_ELEMENT_STATISTICS_NOT_ACTIVE = {
  items: [
    {elementId: 'StartEvent_1', active: 0, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'Gateway_0z6ctwk', active: 0, canceled: 0, incidents: 0, completed: 1},
  ],
};

export const MOCK_AGENT_SEQUENCE_FLOWS_NOT_ACTIVE = {
  items: [
    {processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE, elementId: 'Flow_0pbzrme'},
  ],
};

export const MOCK_AGENT_VARIABLES_NOT_ACTIVE: Variable[] = [
  {
    variableKey: `${MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE}-inputText`,
    name: 'inputText',
    value:
      '"Find the email address of user Leanne Graham and send her an invitation to the company offsite."',
    isTruncated: false,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    scopeKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
  },
];

export const MOCK_AGENT_JOBS_NOT_ACTIVE: Job[] = [];
```

- [ ] **Step 3: Re-export in `agentDemoData/index.ts`**

Add to the existing re-export block:

```ts
export {
  MOCK_AGENT_PROCESS_INSTANCE_NOT_ACTIVE,
  MOCK_AGENT_PROCESS_DEFINITION_NOT_ACTIVE,
  MOCK_AGENT_ELEMENT_INSTANCES_NOT_ACTIVE,
  MOCK_AGENT_ELEMENT_STATISTICS_NOT_ACTIVE,
  MOCK_AGENT_SEQUENCE_FLOWS_NOT_ACTIVE,
  MOCK_AGENT_VARIABLES_NOT_ACTIVE,
  MOCK_AGENT_JOBS_NOT_ACTIVE,
} from './agentProcessInstance';

export {
  MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
  MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
  MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
} from './constants';
```

- [ ] **Step 4: Add the SCENARIOS entry in `scenarioRegistry.ts`**

Add a new entry after the existing active scenario:

```ts
{
  instanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
  definitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
  definitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
  name: 'Agent not yet active',
  description: 'AI Agent element rendered but no agent run has started.',
  pattern: 'subprocess',
  agentElementId: 'AI_Agent',
  agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_ACTIVE,
  bpmnXml: AGENT_BPMN_XML,
  processInstance: MOCK_AGENT_PROCESS_INSTANCE_NOT_ACTIVE,
  processDefinition: MOCK_AGENT_PROCESS_DEFINITION_NOT_ACTIVE,
  elementInstances: MOCK_AGENT_ELEMENT_INSTANCES_NOT_ACTIVE,
  elementStatistics: MOCK_AGENT_ELEMENT_STATISTICS_NOT_ACTIVE,
  sequenceFlows: MOCK_AGENT_SEQUENCE_FLOWS_NOT_ACTIVE,
  variables: MOCK_AGENT_VARIABLES_NOT_ACTIVE,
  jobs: MOCK_AGENT_JOBS_NOT_ACTIVE,
  agentInstances: [],
},
```

Add the matching imports at the top of `scenarioRegistry.ts`.

- [ ] **Step 5: Verify**

```bash
cd /Users/zsofia/Documents/GitHub/camunda/operate/client
npx prettier --check src/modules/mock-server/
npx --no-install tsc --noEmit -p tsconfig.json 2>&1 | grep "error TS" | head -10
```

Reload the app, open the new `Demo: Agent not yet active` link, confirm:
- Canvas renders the full BPMN diagram.
- `StartEvent_1` and `Gateway_0z6ctwk` show completed badges.
- `AI_Agent` is rendered but not selectable (clicking it shows no details panel content, or the standard "no element selected" empty state).
- No status tag, no shine border, no nested-task tag.

- [ ] **Step 6: Commit**

```bash
git add operate/client/src/modules/mock-server/
git commit -m "feat: add 'Agent not yet active' demo scenario"
```

---

## Task 8: State 3 — "Agent completed" scenario

**Files:**
- Modify: `operate/client/src/modules/mock-server/agentDemoData/constants.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/agentInstanceData.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/agentProcessInstance.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/index.ts`
- Modify: `operate/client/src/modules/mock-server/scenarioRegistry.ts`

Goal: a snapshot where the agent has fully run to completion. All element-instances inside `AI_Agent` are `COMPLETED`, and the agent's history includes a final assistant message that summarizes what the agent did.

- [ ] **Step 1: Add new constants in `constants.ts`**

Append at the bottom of the file:

```ts
// State 3 — Agent completed.
export const MOCK_AGENT_INSTANCE_KEY_COMPLETED = '6451799813685000';
export const MOCK_AGENT_DEFINITION_KEY_COMPLETED = '6451799813685099';
export const MOCK_AGENT_DEFINITION_ID_COMPLETED = 'ai-agent-chat-with-tools';
export const MOCK_AGENT_SUBPROCESS_KEY_COMPLETED = '6451799813685010';
export const MOCK_AGENT_INNER_INSTANCE_1_KEY_COMPLETED = '6451799813685015';
export const MOCK_AGENT_INNER_INSTANCE_2_KEY_COMPLETED = '6451799813685016';
export const MOCK_AGENT_INNER_INSTANCE_3_KEY_COMPLETED = '6451799813685017';
export const MOCK_AGENT_INNER_INSTANCE_4_KEY_COMPLETED = '6451799813685018';
export const MOCK_AGENT_INNER_INSTANCE_5_KEY_COMPLETED = '6451799813685019';
export const MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_COMPLETED = '6451799813685045';
export const MOCK_AGENT_AGENT_INSTANCE_KEY_COMPLETED = '6451799813685200';

// AI_Agent element-id set is identical across all states — alias the existing one.
export {MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_ACTIVE as MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_COMPLETED};
```

- [ ] **Step 2: Add `MOCK_AGENT_INSTANCE_COMPLETED` + `MOCK_AGENT_HISTORY_ELEMENTS_COMPLETED` in `agentInstanceData.ts`**

Append at the bottom of the file:

```ts
import {
  // ...existing imports
  MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  MOCK_AGENT_DEFINITION_ID_COMPLETED,
  MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
  MOCK_AGENT_AGENT_INSTANCE_KEY_COMPLETED,
} from './constants';

export const MOCK_AGENT_INSTANCE_COMPLETED: AgentInstance = {
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_COMPLETED,
  status: 'COMPLETED',
  definition: {
    model: 'us.anthropic.claude-sonnet-4-20250514-v1:0',
    provider: 'AWS Bedrock',
    systemPrompt: `You are a helpful assistant that performs tasks on behalf of the user.

## Available Tools

You have access to the following tools:
- **ListUsers** — query the user directory
- **LoadUserByID** — fetch a single user profile by \`id\`
- **GetDateAndTime** — get the current UTC timestamp
- **DraftEmailTemplate** — produce a formatted email draft from a subject and body
- **AskHumanToSendEmail** — delegate email sending to a human operator

## Guidelines

1. Always **verify** information before taking action.
2. Use \`=inputText\` for the user's request.
3. When composing emails, include all required fields.
4. Be concise in your reasoning.`,
    tools: [
      {name: 'ListUsers', source: 'AD_HOC'},
      {name: 'LoadUserByID', source: 'AD_HOC'},
      {name: 'GetDateAndTime', source: 'AD_HOC'},
      {name: 'DraftEmailTemplate', source: 'AD_HOC'},
      {name: 'AskHumanToSendEmail', source: 'AD_HOC'},
    ],
  },
  metrics: {
    inputTokens: 2814,
    outputTokens: 358,
    totalTokens: 3172,
    toolCalls: 5,
  },
  creationTime: '2026-03-26T14:30:00.300Z',
  elementId: 'AI_Agent',
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  tenantId: '<default>',
};

// Build the COMPLETED history by reusing MOCK_AGENT_HISTORY_ELEMENTS_ACTIVE and
// appending the missing tool_results + a final assistant exit message.
const completedBaseElement = (overrides: {
  historyElementKey: string;
  role: HistoryElement['role'];
  timestamp: string;
  content: string;
  metrics?: HistoryElement['metrics'];
}): HistoryElement => ({
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_COMPLETED,
  elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
  jobKey: '6451799813685011',
  committed: true,
  metrics: {},
  ...overrides,
  content: [{contentType: 'text', content: overrides.content}],
});

export const MOCK_AGENT_HISTORY_ELEMENTS_COMPLETED: HistoryElement[] = [
  // The first three iterations mirror the active scenario verbatim. Reuse the
  // text but rebind the agentInstanceKey + elementInstanceKey to the COMPLETED keys.
  ...MOCK_AGENT_HISTORY_ELEMENTS_ACTIVE.map((el) => ({
    ...el,
    agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_COMPLETED,
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
  })),
  // Tool results for DraftEmailTemplate + AskHumanToSendEmail (which were in-flight
  // in the active scenario).
  completedBaseElement({
    historyElementKey: '6451799441055720',
    role: 'tool_result',
    timestamp: '2026-03-26T14:30:05.100Z',
    content: JSON.stringify({
      subject: 'Company Offsite Invitation — March 28',
      preview:
        'Hi Leanne,\\n\\nThe team is hosting the offsite on March 28, 2026 — would love to have you there.',
      tone_used: 'friendly',
    }),
  }),
  completedBaseElement({
    historyElementKey: '6451799441055721',
    role: 'tool_result',
    timestamp: '2026-03-26T14:30:05.400Z',
    content: JSON.stringify({sent: true, sent_at: '2026-03-26T14:30:05.380Z'}),
  }),
  // Final assistant exit message — this is what StatusAccordion shows in the Completed view.
  completedBaseElement({
    historyElementKey: '6451799441055722',
    role: 'assistant',
    timestamp: '2026-03-26T14:30:05.500Z',
    content:
      "Done — the invitation to Leanne Graham at Sincere@april.biz has been drafted with March 28 prominently in the body, the human operator confirmed the send, and the email left the queue at 14:30:05 UTC. The full audit trail (5 tool calls across 3 iterations) is recorded under this agent run, and no follow-up is required from the user.",
    metrics: {inputTokens: 1255, outputTokens: 116, totalTokens: 1371},
  }),
];
```

- [ ] **Step 3: Add element-instance + statistics + sequence-flow + variables + jobs exports in `agentProcessInstance.ts`**

Append at the bottom of the file:

```ts
import {
  // ...existing imports
  MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  MOCK_AGENT_DEFINITION_ID_COMPLETED,
  MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_1_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_2_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_3_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_4_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_5_KEY_COMPLETED,
  MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_COMPLETED,
} from './constants';

export const MOCK_AGENT_PROCESS_INSTANCE_COMPLETED: ProcessInstance = {
  // Same shape as MOCK_AGENT_PROCESS_INSTANCE_ACTIVE but with COMPLETED keys.
  // The process itself stays ACTIVE — only the AI_Agent step has finished.
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  processDefinitionName: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  processDefinitionVersion: 1,
  processDefinitionVersionTag: null,
  tenantId: '<default>',
  state: 'ACTIVE',
  startDate: '2026-03-26T14:30:00.000Z',
  endDate: null,
  hasIncident: false,
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  rootProcessInstanceKey: null,
  tags: [],
};

export const MOCK_AGENT_PROCESS_DEFINITION_COMPLETED: ProcessDefinition = {
  name: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  resourceName: 'ai-agent-chat-with-tools.bpmn',
  version: 1,
  versionTag: null,
  tenantId: '<default>',
  hasStartForm: false,
};

// Build the completed element-instances list by deep-copying the ACTIVE list
// and flipping in-flight states to COMPLETED with an endDate. This avoids
// re-typing ~12 entries; rebind keys to the COMPLETED set.
const COMPLETED_END_DATE = '2026-03-26T14:30:05.500Z';

export const MOCK_AGENT_ELEMENT_INSTANCES_COMPLETED: MockElementInstance[] = [
  // Top-level: process + StartEvent + Gateway (identical pattern; rebound keys).
  {
    elementInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementName: 'AI Agent Chat With Tools',
    type: 'PROCESS',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.000Z',
    endDate: null,
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685001',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'StartEvent_1',
    elementName: 'Task to perform received',
    type: 'START_EVENT',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.100Z',
    endDate: '2026-03-26T14:30:00.200Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685005',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'Gateway_0z6ctwk',
    elementName: null,
    type: 'EXCLUSIVE_GATEWAY',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.250Z',
    endDate: '2026-03-26T14:30:00.280Z',
    incidentKey: null,
  },
  // AI_Agent outer subprocess — COMPLETED.
  {
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent',
    type: 'AD_HOC_SUB_PROCESS',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.300Z',
    endDate: COMPLETED_END_DATE,
    incidentKey: null,
  },
  // Inner instance wrappers + child tool tasks (5 pairs, all COMPLETED).
  // Pattern: for each tool activation in ACTIVE state, copy the same structure
  // but bind to COMPLETED keys, set state: 'COMPLETED', set endDate, and for the
  // two that were in-flight in ACTIVE (AskHumanToSendEmail, AI_Task_Agent),
  // also set endDate. See MOCK_AGENT_ELEMENT_INSTANCES_ACTIVE for the structure.
  // Build them inline below — full code:

  // Tool 1: ListUsers wrapper + child
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_1_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:01.200Z',
    endDate: '2026-03-26T14:30:02.800Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685020',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'ListUsers',
    elementName: 'List users',
    type: 'SERVICE_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_1_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:01.200Z',
    endDate: '2026-03-26T14:30:02.800Z',
    incidentKey: null,
  },
  // Tool 2: LoadUserByID
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_2_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.000Z',
    endDate: '2026-03-26T14:30:04.100Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685025',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'LoadUserByID',
    elementName: 'Load user by ID',
    type: 'SERVICE_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_2_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.000Z',
    endDate: '2026-03-26T14:30:04.100Z',
    incidentKey: null,
  },
  // Tool 3: GetDateAndTime
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_3_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.050Z',
    endDate: '2026-03-26T14:30:04.150Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685030',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'GetDateAndTime',
    elementName: 'Get Date and Time',
    type: 'SCRIPT_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_3_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.050Z',
    endDate: '2026-03-26T14:30:04.150Z',
    incidentKey: null,
  },
  // Tool 4: AskHumanToSendEmail (was ACTIVE in state 2 — now COMPLETED)
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_4_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.300Z',
    endDate: '2026-03-26T14:30:05.400Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685035',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AskHumanToSendEmail',
    elementName: 'Ask human to send email',
    type: 'USER_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_4_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.300Z',
    endDate: '2026-03-26T14:30:05.400Z',
    incidentKey: null,
  },
  // Tool 5: AI_Task_Agent (was ACTIVE in state 2 — now COMPLETED)
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_5_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.320Z',
    endDate: '2026-03-26T14:30:05.100Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Task_Agent',
    elementName: 'AI task agent',
    type: 'TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_5_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.320Z',
    endDate: '2026-03-26T14:30:05.100Z',
    incidentKey: null,
  },
];

export const MOCK_AGENT_ELEMENT_STATISTICS_COMPLETED = {
  items: [
    {elementId: 'StartEvent_1', active: 0, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'Gateway_0z6ctwk', active: 0, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'AI_Agent', active: 0, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'ListUsers', active: 0, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'LoadUserByID', active: 0, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'GetDateAndTime', active: 0, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'AskHumanToSendEmail', active: 0, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'AI_Task_Agent', active: 0, canceled: 0, incidents: 0, completed: 1},
  ],
};

export const MOCK_AGENT_SEQUENCE_FLOWS_COMPLETED = {
  items: [
    {processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED, elementId: 'Flow_0pbzrme'},
    {processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED, elementId: 'Flow_16otfp1'},
    {processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED, elementId: 'Flow_0m7etfk'},
  ],
};

export const MOCK_AGENT_VARIABLES_COMPLETED: Variable[] = [
  {
    variableKey: `${MOCK_AGENT_INSTANCE_KEY_COMPLETED}-inputText`,
    name: 'inputText',
    value:
      '"Find the email address of user Leanne Graham and send her an invitation to the company offsite."',
    isTruncated: false,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    scopeKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  },
];

export const MOCK_AGENT_JOBS_COMPLETED: Job[] = [];
```

- [ ] **Step 4: Re-export in `agentDemoData/index.ts`**

Add new exports beside the State 1 block (mirror the same shape).

- [ ] **Step 5: Add SCENARIOS entry**

In `scenarioRegistry.ts`, add an entry after the State 1 entry:

```ts
{
  instanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  definitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  definitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
  name: 'Agent completed',
  description: 'AI Agent run finished; details panel shows Completed status + exit message.',
  pattern: 'subprocess',
  agentElementId: 'AI_Agent',
  agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_COMPLETED,
  bpmnXml: AGENT_BPMN_XML,
  processInstance: MOCK_AGENT_PROCESS_INSTANCE_COMPLETED,
  processDefinition: MOCK_AGENT_PROCESS_DEFINITION_COMPLETED,
  elementInstances: MOCK_AGENT_ELEMENT_INSTANCES_COMPLETED,
  elementStatistics: MOCK_AGENT_ELEMENT_STATISTICS_COMPLETED,
  sequenceFlows: MOCK_AGENT_SEQUENCE_FLOWS_COMPLETED,
  variables: MOCK_AGENT_VARIABLES_COMPLETED,
  jobs: MOCK_AGENT_JOBS_COMPLETED,
  agentInstances: [
    {
      instance: MOCK_AGENT_INSTANCE_COMPLETED,
      elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
      history: MOCK_AGENT_HISTORY_ELEMENTS_COMPLETED,
    },
  ],
},
```

- [ ] **Step 6: Verify**

```bash
cd /Users/zsofia/Documents/GitHub/camunda/operate/client
npx prettier --check src/modules/mock-server/
npx --no-install tsc --noEmit -p tsconfig.json 2>&1 | grep "error TS" | head -10
```

Reload, open `Demo: Agent completed`, confirm:
- No status tag, no shine border on the canvas.
- All tool children show completed badges.
- Clicking AI_Agent → details panel → Status accordion shows `CheckmarkOutline` icon + "Completed" label + the final assistant message ("Done — the invitation to Leanne Graham…") with no "Tool calls" chip section.

- [ ] **Step 7: Commit**

```bash
git add operate/client/src/modules/mock-server/
git commit -m "feat: add 'Agent completed' demo scenario"
```

---

## Task 9: State 4 — "Agent with multiple element instances" scenario

**Files:**
- Modify: `operate/client/src/modules/mock-server/agentDemoData/constants.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/agentInstanceData.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/agentProcessInstance.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/index.ts`
- Modify: `operate/client/src/modules/mock-server/scenarioRegistry.ts`

Goal: two AI_Agent runs separated by a `User_Feedback` step that returned `userSatisfied = false`.

- [ ] **Step 1: Add new constants in `constants.ts` (two sets of keys, suffix `_1` and `_2`)**

```ts
// State 4 — Multiple element instances. Two AI_Agent runs separated by User_Feedback.
export const MOCK_AGENT_INSTANCE_KEY_MULTIPLE = '7451799813685000';
export const MOCK_AGENT_DEFINITION_KEY_MULTIPLE = '7451799813685099';
export const MOCK_AGENT_DEFINITION_ID_MULTIPLE = 'ai-agent-chat-with-tools';

// Run 1 (completed)
export const MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1 = '7451799813685010';
export const MOCK_AGENT_INNER_INSTANCE_1_KEY_MULTIPLE_1 = '7451799813685015';
export const MOCK_AGENT_INNER_INSTANCE_2_KEY_MULTIPLE_1 = '7451799813685016';
export const MOCK_AGENT_INNER_INSTANCE_3_KEY_MULTIPLE_1 = '7451799813685017';
export const MOCK_AGENT_INNER_INSTANCE_4_KEY_MULTIPLE_1 = '7451799813685018';
export const MOCK_AGENT_INNER_INSTANCE_5_KEY_MULTIPLE_1 = '7451799813685019';
export const MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_MULTIPLE_1 = '7451799813685045';
export const MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_1 = '7451799813685200';

// User_Feedback (between runs)
export const MOCK_USER_FEEDBACK_KEY_MULTIPLE = '7451799813685080';

// Run 2 (active)
export const MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2 = '7451799813685110';
export const MOCK_AGENT_INNER_INSTANCE_1_KEY_MULTIPLE_2 = '7451799813685115';
export const MOCK_AGENT_INNER_INSTANCE_2_KEY_MULTIPLE_2 = '7451799813685116';
export const MOCK_AGENT_INNER_INSTANCE_3_KEY_MULTIPLE_2 = '7451799813685117';
export const MOCK_AGENT_INNER_INSTANCE_4_KEY_MULTIPLE_2 = '7451799813685118';
export const MOCK_AGENT_INNER_INSTANCE_5_KEY_MULTIPLE_2 = '7451799813685119';
export const MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_MULTIPLE_2 = '7451799813685145';
export const MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_2 = '7451799813685300';

// AI_Agent element-id set is identical across all states.
export {MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_ACTIVE as MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_MULTIPLE};
```

- [ ] **Step 2: Add AgentInstance + history exports in `agentInstanceData.ts`**

Two agent runs: Run 1 is a completed clone of the COMPLETED scenario's data, rebound to MULTIPLE_1 keys. Run 2 is an active clone of the ACTIVE scenario's data, rebound to MULTIPLE_2 keys.

```ts
import {
  // ...existing imports
  MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1,
  MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
  MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_1,
  MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_2,
} from './constants';

// Run 1 — completed agent run
export const MOCK_AGENT_INSTANCE_MULTIPLE_1: AgentInstance = {
  ...MOCK_AGENT_INSTANCE_COMPLETED,
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_1,
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
};

export const MOCK_AGENT_HISTORY_ELEMENTS_MULTIPLE_1: HistoryElement[] =
  MOCK_AGENT_HISTORY_ELEMENTS_COMPLETED.map((el) => ({
    ...el,
    agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_1,
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1,
  }));

// Run 2 — active agent run (same in-flight shape as ACTIVE scenario)
export const MOCK_AGENT_INSTANCE_MULTIPLE_2: AgentInstance = {
  ...MOCK_AGENT_INSTANCE_ACTIVE,
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_2,
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  // Bump timestamps by ~10 seconds to sit after the user-feedback step.
  creationTime: '2026-03-26T14:30:15.000Z',
};

export const MOCK_AGENT_HISTORY_ELEMENTS_MULTIPLE_2: HistoryElement[] =
  MOCK_AGENT_HISTORY_ELEMENTS_ACTIVE.map((el) => ({
    ...el,
    agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_2,
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
    // Shift all timestamps by +15 seconds so they sit after the first run + user feedback.
    timestamp: new Date(new Date(el.timestamp).getTime() + 15000).toISOString(),
  }));
```

- [ ] **Step 3: Add element-instances + statistics in `agentProcessInstance.ts`**

The element-instances list contains: top-level entities (process / StartEvent / first Gateway pass) + Run 1's full COMPLETED subtree + User_Feedback COMPLETED + a second Gateway element-instance + Run 2's ACTIVE subtree.

Build it programmatically by cloning the COMPLETED list (with MULTIPLE_1 rebinding) and the ACTIVE list (with MULTIPLE_2 rebinding), then concatenating with the User_Feedback + second Gateway entries:

```ts
import {
  // ...existing imports
  // MULTIPLE-state keys (this task):
  MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  MOCK_AGENT_DEFINITION_ID_MULTIPLE,
  MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1,
  MOCK_AGENT_INNER_INSTANCE_1_KEY_MULTIPLE_1,
  MOCK_AGENT_INNER_INSTANCE_2_KEY_MULTIPLE_1,
  MOCK_AGENT_INNER_INSTANCE_3_KEY_MULTIPLE_1,
  MOCK_AGENT_INNER_INSTANCE_4_KEY_MULTIPLE_1,
  MOCK_AGENT_INNER_INSTANCE_5_KEY_MULTIPLE_1,
  MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_MULTIPLE_1,
  MOCK_USER_FEEDBACK_KEY_MULTIPLE,
  MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
  MOCK_AGENT_INNER_INSTANCE_1_KEY_MULTIPLE_2,
  MOCK_AGENT_INNER_INSTANCE_2_KEY_MULTIPLE_2,
  MOCK_AGENT_INNER_INSTANCE_3_KEY_MULTIPLE_2,
  MOCK_AGENT_INNER_INSTANCE_4_KEY_MULTIPLE_2,
  MOCK_AGENT_INNER_INSTANCE_5_KEY_MULTIPLE_2,
  MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_MULTIPLE_2,
  // ACTIVE-state keys (already imported in this file from Task 2 — verify present):
  MOCK_AGENT_INSTANCE_KEY_ACTIVE,
  MOCK_AGENT_DEFINITION_ID_ACTIVE,
  MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
  MOCK_AGENT_INNER_INSTANCE_1_KEY_ACTIVE,
  MOCK_AGENT_INNER_INSTANCE_2_KEY_ACTIVE,
  MOCK_AGENT_INNER_INSTANCE_3_KEY_ACTIVE,
  MOCK_AGENT_INNER_INSTANCE_4_KEY_ACTIVE,
  MOCK_AGENT_INNER_INSTANCE_5_KEY_ACTIVE,
  MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_ACTIVE,
  // COMPLETED-state keys (added in Task 8 — these must be imported here so the
  // RUN_1_REBINDS table below resolves):
  MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  MOCK_AGENT_DEFINITION_ID_COMPLETED,
  MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_1_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_2_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_3_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_4_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_5_KEY_COMPLETED,
  MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_COMPLETED,
} from './constants';

export const MOCK_AGENT_PROCESS_INSTANCE_MULTIPLE: ProcessInstance = {
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  processDefinitionName: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  processDefinitionVersion: 1,
  processDefinitionVersionTag: null,
  tenantId: '<default>',
  state: 'ACTIVE',
  startDate: '2026-03-26T14:30:00.000Z',
  endDate: null,
  hasIncident: false,
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  rootProcessInstanceKey: null,
  tags: [],
};

export const MOCK_AGENT_PROCESS_DEFINITION_MULTIPLE: ProcessDefinition = {
  name: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  resourceName: 'ai-agent-chat-with-tools.bpmn',
  version: 1,
  versionTag: null,
  tenantId: '<default>',
  hasStartForm: false,
};

// Rebinding helpers — point cloned element-instances at the MULTIPLE keyspace.
type RebindMap = Record<string, string>;
const rebindElementInstances = (
  source: MockElementInstance[],
  processKey: string,
  definitionKey: string,
  keyRebinds: RebindMap,
): MockElementInstance[] =>
  source.map((el) => ({
    ...el,
    processInstanceKey: processKey,
    processDefinitionKey: definitionKey,
    elementInstanceKey: keyRebinds[el.elementInstanceKey] ?? el.elementInstanceKey,
    flowScopeKey: keyRebinds[el.flowScopeKey] ?? el.flowScopeKey,
  }));

const RUN_1_REBINDS: RebindMap = {
  [MOCK_AGENT_INSTANCE_KEY_COMPLETED]: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  [MOCK_AGENT_SUBPROCESS_KEY_COMPLETED]: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1,
  [MOCK_AGENT_INNER_INSTANCE_1_KEY_COMPLETED]: MOCK_AGENT_INNER_INSTANCE_1_KEY_MULTIPLE_1,
  [MOCK_AGENT_INNER_INSTANCE_2_KEY_COMPLETED]: MOCK_AGENT_INNER_INSTANCE_2_KEY_MULTIPLE_1,
  [MOCK_AGENT_INNER_INSTANCE_3_KEY_COMPLETED]: MOCK_AGENT_INNER_INSTANCE_3_KEY_MULTIPLE_1,
  [MOCK_AGENT_INNER_INSTANCE_4_KEY_COMPLETED]: MOCK_AGENT_INNER_INSTANCE_4_KEY_MULTIPLE_1,
  [MOCK_AGENT_INNER_INSTANCE_5_KEY_COMPLETED]: MOCK_AGENT_INNER_INSTANCE_5_KEY_MULTIPLE_1,
  [MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_COMPLETED]: MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_MULTIPLE_1,
  '6451799813685001': '7451799813685001', // StartEvent
  '6451799813685005': '7451799813685005', // Gateway pass 1
  '6451799813685020': '7451799813685020', // ListUsers (rebind isn't required, but keep keys disjoint per state)
  '6451799813685025': '7451799813685025', // LoadUserByID
  '6451799813685030': '7451799813685030', // GetDateAndTime
  '6451799813685035': '7451799813685035', // AskHumanToSendEmail
};

const RUN_2_REBINDS: RebindMap = {
  [MOCK_AGENT_INSTANCE_KEY_ACTIVE]: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  [MOCK_AGENT_SUBPROCESS_KEY_ACTIVE]: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
  [MOCK_AGENT_INNER_INSTANCE_1_KEY_ACTIVE]: MOCK_AGENT_INNER_INSTANCE_1_KEY_MULTIPLE_2,
  [MOCK_AGENT_INNER_INSTANCE_2_KEY_ACTIVE]: MOCK_AGENT_INNER_INSTANCE_2_KEY_MULTIPLE_2,
  [MOCK_AGENT_INNER_INSTANCE_3_KEY_ACTIVE]: MOCK_AGENT_INNER_INSTANCE_3_KEY_MULTIPLE_2,
  [MOCK_AGENT_INNER_INSTANCE_4_KEY_ACTIVE]: MOCK_AGENT_INNER_INSTANCE_4_KEY_MULTIPLE_2,
  [MOCK_AGENT_INNER_INSTANCE_5_KEY_ACTIVE]: MOCK_AGENT_INNER_INSTANCE_5_KEY_MULTIPLE_2,
  [MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_ACTIVE]: MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_MULTIPLE_2,
  // StartEvent + Gateway pass 1 are NOT in Run 2 (run 2 starts after the user-feedback loopback)
  // So those entries from ACTIVE are filtered out below before rebinding.
};

const run1Cloned = rebindElementInstances(
  MOCK_AGENT_ELEMENT_INSTANCES_COMPLETED.filter(
    (el) => el.elementId !== MOCK_AGENT_DEFINITION_ID_COMPLETED, // drop the PROCESS row; handled at top
  ),
  MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  RUN_1_REBINDS,
);

// Run 2 from ACTIVE: drop the top-level PROCESS + StartEvent + first Gateway
// (those belong only to the first pass; Run 2 starts at the second Gateway pass).
const run2Cloned = rebindElementInstances(
  MOCK_AGENT_ELEMENT_INSTANCES_ACTIVE.filter(
    (el) =>
      el.elementId !== MOCK_AGENT_DEFINITION_ID_ACTIVE &&
      el.elementId !== 'StartEvent_1' &&
      el.elementId !== 'Gateway_0z6ctwk',
  ),
  MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  RUN_2_REBINDS,
);

export const MOCK_AGENT_ELEMENT_INSTANCES_MULTIPLE: MockElementInstance[] = [
  // Top-level process
  {
    elementInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementName: 'AI Agent Chat With Tools',
    type: 'PROCESS',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.000Z',
    endDate: null,
    incidentKey: null,
  },
  // StartEvent (just once)
  {
    elementInstanceKey: '7451799813685001',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'StartEvent_1',
    elementName: 'Task to perform received',
    type: 'START_EVENT',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.100Z',
    endDate: '2026-03-26T14:30:00.200Z',
    incidentKey: null,
  },
  // Gateway pass 1
  {
    elementInstanceKey: '7451799813685005',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'Gateway_0z6ctwk',
    elementName: null,
    type: 'EXCLUSIVE_GATEWAY',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.250Z',
    endDate: '2026-03-26T14:30:00.280Z',
    incidentKey: null,
  },
  // Run 1 — AI_Agent + inner instances + tools (all COMPLETED)
  ...run1Cloned,
  // User_Feedback — COMPLETED, userSatisfied = false
  {
    elementInstanceKey: MOCK_USER_FEEDBACK_KEY_MULTIPLE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'User_Feedback',
    elementName: 'User Feedback',
    type: 'USER_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:05.500Z',
    endDate: '2026-03-26T14:30:14.000Z',
    incidentKey: null,
  },
  // Gateway pass 2 (after user-feedback loopback)
  {
    elementInstanceKey: '7451799813685006',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'Gateway_0z6ctwk',
    elementName: null,
    type: 'EXCLUSIVE_GATEWAY',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:14.100Z',
    endDate: '2026-03-26T14:30:14.120Z',
    incidentKey: null,
  },
  // Run 2 — AI_Agent + inner instances + tools (active state)
  ...run2Cloned,
];

export const MOCK_AGENT_ELEMENT_STATISTICS_MULTIPLE = {
  items: [
    {elementId: 'StartEvent_1', active: 0, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'Gateway_0z6ctwk', active: 0, canceled: 0, incidents: 0, completed: 2},
    {elementId: 'AI_Agent', active: 1, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'ListUsers', active: 0, canceled: 0, incidents: 0, completed: 2},
    {elementId: 'LoadUserByID', active: 0, canceled: 0, incidents: 0, completed: 2},
    {elementId: 'GetDateAndTime', active: 0, canceled: 0, incidents: 0, completed: 2},
    {elementId: 'AskHumanToSendEmail', active: 1, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'AI_Task_Agent', active: 1, canceled: 0, incidents: 0, completed: 1},
    {elementId: 'User_Feedback', active: 0, canceled: 0, incidents: 0, completed: 1},
  ],
};

export const MOCK_AGENT_SEQUENCE_FLOWS_MULTIPLE = {
  items: [
    {processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE, elementId: 'Flow_0pbzrme'},
    {processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE, elementId: 'Flow_16otfp1'},
    {processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE, elementId: 'Flow_0m7etfk'},
    {processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE, elementId: 'Flow_09y08ef'},
    {processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE, elementId: 'Flow_19gp461'},
  ],
};

export const MOCK_AGENT_VARIABLES_MULTIPLE: Variable[] = [
  {
    variableKey: `${MOCK_AGENT_INSTANCE_KEY_MULTIPLE}-inputText`,
    name: 'inputText',
    value:
      '"Find the email address of user Leanne Graham and send her an invitation to the company offsite."',
    isTruncated: false,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    scopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  },
  {
    variableKey: `${MOCK_AGENT_INSTANCE_KEY_MULTIPLE}-userSatisfied`,
    name: 'userSatisfied',
    value: 'false',
    isTruncated: false,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    scopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  },
];

export const MOCK_AGENT_JOBS_MULTIPLE: Job[] = [];
```

- [ ] **Step 4: Re-export in `agentDemoData/index.ts`** (mirror the State-1/State-3 patterns).

- [ ] **Step 5: Add SCENARIOS entry**

In `scenarioRegistry.ts`:

```ts
{
  instanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  definitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  definitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
  name: 'Multiple element instances',
  description: 'Two AI Agent runs separated by a User_Feedback (userSatisfied = false) loop.',
  pattern: 'subprocess',
  agentElementId: 'AI_Agent',
  agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_MULTIPLE,
  bpmnXml: AGENT_BPMN_XML,
  processInstance: MOCK_AGENT_PROCESS_INSTANCE_MULTIPLE,
  processDefinition: MOCK_AGENT_PROCESS_DEFINITION_MULTIPLE,
  elementInstances: MOCK_AGENT_ELEMENT_INSTANCES_MULTIPLE,
  elementStatistics: MOCK_AGENT_ELEMENT_STATISTICS_MULTIPLE,
  sequenceFlows: MOCK_AGENT_SEQUENCE_FLOWS_MULTIPLE,
  variables: MOCK_AGENT_VARIABLES_MULTIPLE,
  jobs: MOCK_AGENT_JOBS_MULTIPLE,
  agentInstances: [
    {
      instance: MOCK_AGENT_INSTANCE_MULTIPLE_1,
      elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1,
      history: MOCK_AGENT_HISTORY_ELEMENTS_MULTIPLE_1,
    },
    {
      instance: MOCK_AGENT_INSTANCE_MULTIPLE_2,
      elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
      history: MOCK_AGENT_HISTORY_ELEMENTS_MULTIPLE_2,
    },
  ],
},
```

- [ ] **Step 6: Verify**

```bash
cd /Users/zsofia/Documents/GitHub/camunda/operate/client
npx prettier --check src/modules/mock-server/
npx --no-install tsc --noEmit -p tsconfig.json 2>&1 | grep "error TS" | head -10
```

Reload, open `Demo: Multiple element instances`, confirm:
- Canvas: AI_Agent has an active token. Toggle "Execution count" in the diagram toolbar → "1" completed badge appears on AI_Agent.
- Status tag "Calling tools…" floats above AI_Agent (the active run); "Thinking…" above AI_Task_Agent.
- Instance history (left tree) shows two AI_Agent entries with User_Feedback between them.
- Selecting the **completed** AI_Agent in the tree → details panel shows `CheckmarkOutline` icon + "Completed" + the exit message (no tool chips).
- Selecting the **active** AI_Agent → details panel shows "Calling tools" + the latest reasoning + tool chips.

- [ ] **Step 7: Commit**

```bash
git add operate/client/src/modules/mock-server/
git commit -m "$(cat <<'EOF'
feat: add 'Multiple element instances' demo scenario

Two AI_Agent runs separated by a User_Feedback step that returned
userSatisfied = false, looping back through Gateway_0z6ctwk. The first run is
COMPLETED with a full history; the second is the in-flight 'Calling tools…'
state from the existing active demo, rebound to fresh keys.
EOF
)"
```

---

## Self-review checklist

After implementing, run this self-check:

- [ ] All four nav items appear in the dev nav menu (`Demo: Agent not yet active`, `Demo: Agent chat with tools` / `Demo: Agent active`, `Demo: Agent completed`, `Demo: Multiple element instances`).
- [ ] State 1: AI_Agent on canvas is not selectable (clicking it produces no agent details panel content).
- [ ] State 2: behaves as before, but User_Feedback is no longer active.
- [ ] State 3: no canvas overlays, details panel shows `CheckmarkOutline` + "Completed" + exit text, no tool chips.
- [ ] State 4: two AI_Agent rows in history, switching between them in the history panel swaps the details panel between completed and active layouts.
- [ ] `npx vitest run src/modules/queries/agentInstances/historyToAgentElementData.test.ts` → 3/3 passing.
- [ ] `npx --no-install tsc --noEmit -p tsconfig.json` → no TS errors.
- [ ] `npx prettier --check src/` → clean.
