# AI Agent Context Panel (Operate UI) — Implementation Plan

## Goal
When a user clicks an **active Ad-hoc Sub-process** in the Process Instance view and the clicked scope contains an `agentContext` variable, show a **dedicated right sidebar panel** rendering the agent execution as a **vertical timeline** (Carbon styling).

This plan reflects the updated requirement:
- **Do not rely on connector `jobType`** (or process definition details) to decide visibility.
- **Only check presence of the `agentContext` variable on the clicked Ad-hoc Sub-process instance** (scope/element instance) to decide whether to show the panel.

## UX / Feature Requirements
- Right sidebar opens when user selects an eligible Ad-hoc Sub-process.
- Timeline shows:
  - Agent state (`agentContext.state`).
  - Collapsible tool definition list (`agentContext.toolDefinitions`).
  - Collapsible system prompt (first `conversation.messages[]` with `role: "system"`).
  - Each LLM call (assistant message without toolCalls).
  - Tool call start (assistant `toolCalls`), and tool call finished (matching `tool_call_result`).
    - Tool call input + tool output must be **expandable**.
    - Tool call `id` must match tool result `id`.
  - If agent still running: nicely animated status (e.g. “Thinking…” / “Waiting for tool call results…”).
- Panel updates when the `agentContext` variable changes.
- Implementation should be isolated from unrelated code.
- Styling uses Carbon, responsive and professional.

## Data Shape (from sample `agentContext.json`)
Expected structure (best-effort tolerant parsing):
- `state: string` (e.g. READY)
- `metadata: { processDefinitionKey, processInstanceKey }`
- `metrics: { modelCalls, tokenUsage }`
- `toolDefinitions: Array<{ name, description, inputSchema }>`
- `conversation: { type, conversationId, messages[] }`
  - `messages[]` contains objects with one of:
    - `role: system|user|assistant` with `content[]`
    - `role: assistant` with `toolCalls[]` (`{id,name,arguments}`)
    - `role: tool_call_result` with `results[]` (`{id,name,content}`)
  - `metadata.timestamp` is present on many entries

## Architecture (isolation first)
Create three isolated layers:

1) **Query / data access** (`modules/queries/agentContext/*`)
- Single hook: `useAgentContextVariable({processInstanceKey, scopeKey})`
- Responsibilities:
  - fetch `agentContext` variable for the selected scope
  - parse JSON (safe)
  - refetch on updates (polling or event-based invalidation)

2) **Parsing + view-model** (`modules/agentContext/*`)
- Pure TS, no React
- Responsibilities:
  - validate/parse the raw JSON string into typed model
  - normalize to a timeline view-model
  - correlate tool call starts ↔ tool results by id

3) **UI** (`App/ProcessInstance/RightPanel/AgentContextPanel/*`)
- React components only; no API logic except calling the hook
- Carbon layout (CollapsablePanel + Accordion + InlineLoading/Tag/CodeSnippet)

## Step-by-step Implementation

### 1) Identify the selection context for clicked element instance (✅ analyzed)
Operate already tracks the user’s selection in the **URL search params**, and resolves it to a concrete element instance.

**Selection source of truth**
- Hook: `operate/client/src/modules/hooks/useProcessInstanceElementSelection.ts`
- URL params used:
  - `elementInstanceKey` (preferred when clicking a concrete instance)
  - `elementId` (fallback when no single instance key is known)
  - `isMultiInstanceBody`, `isPlaceholder`, `anchorElementId`
- The hook exposes:
  - `selectedElementInstanceKey` (string | null)
  - `selectedElementId` (string | null)
  - `resolvedElementInstance` (the API element instance object, or null)

**Where selection is set when clicking the Instance History tree**
- Tree UI: `operate/client/src/App/ProcessInstance/ElementInstanceLog/ElementInstancesTree/index.tsx`
- On click, it calls `selectElementInstance({ elementId, elementInstanceKey: scopeKey })`.
- In this tree, `scopeKey` is the **element instance key**:
  - `scopeKey = elementInstance.elementInstanceKey`

**How to obtain the data we need**
Use `useProcessInstanceElementSelection()` and read:
- `resolvedElementInstance.elementInstanceKey` → use as the **scopeKey** for variable lookup
- `resolvedElementInstance.type` → determine Ad-hoc scope types
- `resolvedElementInstance.state` → determine active vs completed/terminated

**Ad-hoc Sub-process element types in the Instance History tree**
The Instance History supports folding for these element instance types (includes Ad-hoc):
- `AD_HOC_SUB_PROCESS`
- `AD_HOC_SUB_PROCESS_INNER_INSTANCE`

(Source: `FOLDABLE_ELEMENT_TYPES` in `ElementInstancesTree/index.tsx`.)

**Output for the next steps**
From existing hooks we can reliably derive:
- `processInstanceKey`: from `useProcessInstancePageParams()` (used inside the selection hook)
- `scopeKey`: `resolvedElementInstance.elementInstanceKey` (string)
- `elementType`: `resolvedElementInstance.type` (e.g. `AD_HOC_SUB_PROCESS`)
- `elementState`: `resolvedElementInstance.state`

Notes:
- We should base “active/running” on `resolvedElementInstance.state` (exact enum values to be confirmed from the schema usage in UI).
- Root process selection is handled specially in the tree (clears selection); the agent panel should only consider non-root selections.

### 2) Gate panel visibility using only `agentContext` variable presence (✅ implemented)
Eligibility rules:
1. Selection is an **Ad-hoc Sub-process** element instance.
2. Selection state is **active/running** (not completed/terminated).
3. Variable named `agentContext` exists for that selection scope.

#### How to get the correct `scopeKey` in Operate UI
Operate already has the concept of a “variable scope key” and uses it for the Variables panel:
- Hook: `operate/client/src/modules/hooks/useVariableScopeKey` (in `modules/hooks/variables.ts`)
  - It derives the scope from the current element selection.
- Helper: `useElementSelectionInstanceKey()` (in `modules/hooks/useElementSelectionInstanceKey.ts`)
  - If an element instance is selected, it returns the resolved `elementInstanceKey`.
  - If nothing is selected, it returns the process instance key.

For the agent panel gating we should **use the selected element instance key**, not the process instance key.
- Recommended source: `useProcessInstanceElementSelection().resolvedElementInstance.elementInstanceKey`
  - This guarantees we’re checking the clicked scope.

#### How variables are queried (existing mechanism)
Operate’s variables query uses the v2 endpoint wrapper:
- Query hook: `operate/client/src/modules/queries/variables/useVariables.ts`
- API client: `operate/client/src/modules/api/v2/variables/searchVariables.ts`

It calls `searchVariables` with a filter:
- `processInstanceKey: {$eq: <processInstanceId>}`
- `scopeKey: {$eq: <scopeKey>}`

#### Efficient `agentContext` presence check (without fetching all variables)
To decide whether to show the panel, we only need to know if **a variable named `agentContext` exists** for the selected scope.

Implement a small dedicated query (recommended) that reuses the same v2 `searchVariables` endpoint but adds a name filter:
- Filter:
  - `processInstanceKey: {$eq: processInstanceKey}`
  - `scopeKey: {$eq: selectedScopeKey}`
  - `name: {$eq: 'agentContext'}`
- Page:
  - `limit: 1`, `from: 0`

This returns quickly and avoids loading potentially many variables.

Derived result:
- `hasAgentContext = response.items.length > 0`

#### Where the gating logic will live
Create a new small hook in the agent panel feature (so it stays isolated):
- `operate/client/src/modules/queries/agentContext/useHasAgentContext.ts` ✅ (implemented)

Contract:
- Input: `{ processInstanceKey: string, scopeKey: string | null, enabled: boolean }`
- Output (via react-query `select`): `{ hasAgentContext: boolean }`

This hook will be used *only* to decide if the Agent panel should render.

**Output:** boolean `shouldShowAgentContextPanel` based on:
- `resolvedElementInstance?.type` in `{AD_HOC_SUB_PROCESS, AD_HOC_SUB_PROCESS_INNER_INSTANCE}`
- `resolvedElementInstance?.state` is active
- `hasAgentContext === true`

### 3) Implement `useAgentContextVariable` hook (✅ implemented)
Create: `operate/client/src/modules/queries/agentContext/useAgentContextVariable.ts` ✅ (implemented)

Contract:
- **Input:** `{ processInstanceKey: string, scopeKey: string | null, enabled?: boolean, refetchInterval?: number | false }`
- **Output (current):**
  - `rawValue: string | null` (normalized string)
  - `parsed: unknown | null` (safe JSON.parse result)
  - `parseError: Error | null`
  - `variableKey: string | null`

Update behavior:
- Supports polling via `refetchInterval` (to be enabled when the selected ad-hoc scope is active and the panel is visible).

Notes:
- This hook intentionally returns `parsed` as `unknown` for now; step 4 will introduce typed parsing and timeline normalization.

**Output:** stable hook used only by the panel.

### 4) Add parsing + normalization utilities (✅ implemented)
Created (pure TS, isolated from React):
- `operate/client/src/modules/agentContext/types.ts` ✅
  - Defines tolerant AgentContext types and `AgentTimelineModel`.
- `operate/client/src/modules/agentContext/parseAgentContext.ts` ✅
  - Tolerant parsing from `unknown` (never throws; ensures arrays exist).
- `operate/client/src/modules/agentContext/correlateToolCalls.ts` ✅
  - Correlates `toolCalls[]` with `tool_call_result.results[]` strictly by id.
- `operate/client/src/modules/agentContext/buildTimelineModel.ts` ✅
  - Builds `AgentTimelineModel` with header items (state/tools/system) + timeline events.
  - Supports `isRunning` to append a final status item (Thinking / Waiting for tool results).

Tests:
- `operate/client/src/modules/agentContext/buildTimelineModel.test.ts` ✅
  - Validates tool call/result matching and running status behavior.

Notes:
- The model currently:
  - emits `TOOL_CALL` events containing correlated results inline
  - also emits `TOOL_CALL_RESULT` events when results are present (both patterns supported by the future UI)
  - keeps ordering based on message order (uses timestamps when available for display only)

**Output:** `AgentTimelineModel` ready for rendering.

### 5) Build the Carbon UI panel (UPDATED: Bottom panel tab, not sidebar)

✅ **Implemented**

Delivered as a new tab in the existing bottom-right panel (`VariablePanel`):
- Wiring / gating / auto-selection:
  - `operate/client/src/App/ProcessInstance/BottomPanel/VariablePanel/index.tsx`
    - Adds `tabIds.agentContext = 'agent-context'`
    - Conditionally shows the **AI Agent** tab when:
      - selection type is `AD_HOC_SUB_PROCESS` / `AD_HOC_SUB_PROCESS_INNER_INSTANCE`
      - selected element instance is running (`isRunning(resolvedElementInstance)`)
      - `useHasAgentContext(...)` returns `hasAgentContext === true`
    - Auto-selects the AI Agent tab on eligible selection changes

- Tab content (isolated components):
  - `operate/client/src/App/ProcessInstance/BottomPanel/VariablePanel/AgentContextTab/`
    - `index.tsx` (fetch + parse + build timeline, polling only when tab visible + scope running)
    - `timeline/*` (Carbon accordion-based timeline rendering)

Tests:
- `operate/client/src/App/ProcessInstance/BottomPanel/VariablePanel/tests/agentContextTab.test.tsx`
  - Verifies the tab is shown and selected by default for eligible ad-hoc selections.

### 6) Animate “agent is still running” state (unchanged concept, new location)
Same heuristics, now applied within the tab:
1. If the selected element instance is active → agent is running.
2. If agent context `state` indicates still running → running.
3. If last tool call has no matching results → show “Waiting for tool call results…”, else “Thinking…”.

Implementation details:
- Pass `isRunning` into `buildTimelineModel({agentContext, isRunning})`.
- Only animate when the **AI Agent tab is selected**.

### 7) Update-on-change behavior (unchanged concept, new location)
- The tab content should update when the `agentContext` variable changes.
- Enable polling only while:
  - the tab is visible AND
  - the selected element instance is active

To preserve UX:
- keep stable IDs from `buildTimelineModel`.
- avoid collapsing the whole timeline on every refresh.

### 8) Testing (update for tab approach)
Add component tests for `VariablePanel` behavior:
- AI Agent tab is shown only when `agentContext` exists on selected ad-hoc scope.
- AI Agent tab becomes the default selected tab when clicking that scope.
- Polling enabled only when AI Agent tab is selected (optional).

## Edge Cases / Non-goals
- Extremely large conversations may need virtualization later.
- Some `assistant` messages may contain both `content` and `toolCalls` in future; support both.
- If `agentContext` is not valid JSON, show an error but allow viewing raw value.

## Open questions (to confirm before final wiring)
1. How does Operate expose variables scoped to a specific element instance (scopeKey) in the UI layer?
2. How do we detect “Ad-hoc Sub-process” in the selected element instance model (type value/name)?
3. Is there an existing event-driven invalidation mechanism for variable updates, or should we poll?
