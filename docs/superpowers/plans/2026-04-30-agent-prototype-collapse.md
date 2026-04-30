# Agent Prototype Collapse — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collapse the real-time agent visibility prototype to a single fully-mocked "agent chat with tools" scenario whose data flows through MSW handlers shaped to the planned engine `agent-instances` API, deleting the synthesized-from-real-BPMN placeholder path and the second loan-evaluation scenario.

**Architecture:** The prototype's `AgentDataProvider` currently has two paths: a scripted-scenario path that reads pre-built enrichment from `scenarioRegistry`, and a placeholder path that *synthesizes* fake enrichment data from a real deployed BPMN (`buildPlaceholderAgentData`). After this refactor there is one path: the provider sources data via three new TanStack Query hooks (`useAgentInstance`, `useSearchAgentInstances`, `useAgentInstanceHistory`) that hit the `/v2/agent-instances/*` endpoints; MSW handlers intercept those calls only for the one mock `processInstanceKey` and serve hand-crafted fixtures conforming to the API spec; for any other URL the queries `passthrough()` and the provider returns "not an agent instance". A pure transform reconstructs the legacy iteration-shaped `AgentElementData` from the flat `HistoryElement[]` so the components don't change. A dev-only `DemoLauncher` button mounted in the App shell exposes the demo URL with one click.

**Tech Stack:** React 18, TypeScript, Vite, MSW v2 (`http`/`HttpResponse`/`passthrough`), TanStack Query, `requestWithThrow` from `modules/request`, Carbon Design System, Styled Components, Vitest + Testing Library.

---

## Prototype context — what this is and isn't

This is a **design prototype** for internal stakeholder demos, not production code. There is no real backend; the "new API surface" we're adding is purely frontend plumbing — types, query hooks, and a fetch layer that *speaks the shape* of the planned engine `/v2/agent-instances/*` endpoints. MSW intercepts every call and returns hand-crafted fixtures. Nothing leaves the browser. Benefit: when the engine team ships the real endpoints, swapping MSW for real responses is mechanical — no provider or component changes.

Testing posture: **no new tests except 3 sanity checks for the iteration-grouping transform** (Task 6). The transform is the one piece of non-trivial pure logic, and 3 quick checks catch the obvious ways it can silently produce wrong data. Everything else is verified by the visual smoke test in Task 15. Existing tests must continue to pass.

---

## Required reading (one pass before starting)

- `.github/instructions/frontend.instructions.md` — TanStack Query / `requestWithThrow` / `queryKeys` / Styled Components / component layout conventions.
- `operate/client/src/modules/mock-server/README.md` — MSW pattern.
- `operate/client/src/modules/mock-server/handlers.ts` — current MSW handlers (this is the file you'll add to in Task 10).
- `operate/client/src/modules/mock-server/scenarioRegistry.ts` — scenario shape (changes in Task 8).
- `operate/client/src/modules/mock-server/agentDemoData/{constants,agentBpmnXml,agentProcessInstance,agentEnrichmentData,index}.ts` — mock fixtures we're keeping; `agentEnrichmentData.ts` is the file replaced in Task 7.
- `operate/client/src/modules/contexts/agentData.tsx` — the provider with the two-path branching. Rewritten in Task 11.
- `operate/client/src/modules/contexts/detectAgent.ts` — `detectAgentElement` + `buildPlaceholderAgentData`. Deleted in Task 12.
- API spec: `/Users/zsofia/Documents/claude-assistant/Epics/Agentic Observability/Specs/API spec.md`.
- Terminology/intent: `/Users/zsofia/Documents/claude-assistant/Epics/Agentic Observability/Context for Claude.md`.

## Known API spec gaps to flag (do not invent fields)

- `@camunda/camunda-api-zod-schemas/8.10` does **not** yet export endpoint definitions or types for `/v2/agent-instances/*`. We hand-write URLs, methods, and types in this prototype and tag every site with a `// TODO(API): replace once schemas package adds agent-instances endpoints` comment so the migration is mechanical when the schemas land.
- The spec's `tools[]` items have only `{name, source}`. The legacy components render `description` and `parameters`. Treat these as gaps: the transform fills them with empty strings / `undefined`, and we leave a comment marking the gap. Do not fabricate descriptions.
- The spec's `AgentInstance` has no `limits` field; the legacy `AgentUsage.modelCalls/toolsCalled` shape exposes `{current, limit}`. Derive `current` from metrics and leave `limit` as a hardcoded prototype constant (e.g. `10`) with a TODO comment — same pattern the existing fixture uses.
- The legacy `AgentStatus` enum includes `INITIALIZING | TOOL_DISCOVERY | THINKING | WAITING_FOR_TOOL | COMPLETED | FAILED`. The API spec only documents `IDLE | THINKING | CALLING_TOOL | ...` (incomplete). Map both directions in the transform; for unknown statuses, fall back to `THINKING`. Document the open question in a comment in `types.ts`.

---

## File structure — what gets created, modified, deleted

### Create

| Path | Responsibility |
|---|---|
| `operate/client/src/modules/queries/agentInstances/types.ts` | Hand-written TS types for `AgentInstance`, `AgentInstanceStatus`, `AgentInstanceTool`, `AgentInstanceMetrics`, `HistoryElement`, `HistoryElementRole`, `HistoryElementContent`, request/response bodies. |
| `operate/client/src/modules/api/v2/agentInstances/fetchAgentInstance.ts` | `requestWithThrow<AgentInstance>` for `GET /v2/agent-instances/{key}`. |
| `operate/client/src/modules/api/v2/agentInstances/searchAgentInstances.ts` | `requestWithThrow` for `POST /v2/agent-instances/search`. |
| `operate/client/src/modules/api/v2/agentInstances/searchAgentInstanceHistory.ts` | `requestWithThrow` for `POST /v2/agent-instances/{key}/history/search`. |
| `operate/client/src/modules/queries/agentInstances/useAgentInstance.ts` | Hook for single instance. |
| `operate/client/src/modules/queries/agentInstances/useSearchAgentInstances.ts` | Hook for search-by-processInstanceKey. |
| `operate/client/src/modules/queries/agentInstances/useAgentInstanceHistory.ts` | Hook for history search. |
| `operate/client/src/modules/queries/agentInstances/historyToAgentElementData.ts` | Pure transform from `(AgentInstance, HistoryElement[])` → legacy `AgentElementData`. Sibling of the hooks; testable in isolation. |
| `operate/client/src/modules/queries/agentInstances/historyToAgentElementData.test.ts` | Vitest unit tests for the transform. |
| `operate/client/src/modules/contexts/agentData.types.ts` | Stable home for the legacy consumer-facing types (`AgentElementData`, `AgentIteration`, `AgentToolCall`, `AgentStatus`, `AgentFinishReason`, `AgentUsage`, `ConversationMessage`) — moved out of `agentEnrichmentData.ts` so they survive the mock-data rewrite. |
| `operate/client/src/modules/mock-server/agentDemoData/agentInstanceData.ts` | New fixture file: `MOCK_AGENT_INSTANCE: AgentInstance` and `MOCK_AGENT_HISTORY_ELEMENTS: HistoryElement[]` (flat). Replaces `agentEnrichmentData.ts`. |
| `operate/client/src/App/DemoLauncher/index.tsx` | Dev-only floating launcher that reads `SCENARIOS` and renders one button per scenario. |
| `operate/client/src/App/DemoLauncher/styled.tsx` | Styled Components for the launcher (Carbon tokens, low visual weight). |

### Modify

| Path | What changes |
|---|---|
| `operate/client/src/modules/queries/queryKeys.ts` | Add `agentInstances` factory: `get(key)`, `search(payload)`, `historySearch(key, payload)`. |
| `operate/client/src/modules/mock-server/agentDemoData/index.ts` | Remove `agentEnrichmentData.ts` re-exports (file is deleted); export `MOCK_AGENT_INSTANCE`, `MOCK_AGENT_HISTORY_ELEMENTS` from the new file. Type re-exports move to point at `modules/contexts/agentData.types`. |
| `operate/client/src/modules/mock-server/scenarioRegistry.ts` | Drop loan-scenario block; replace `enrichmentData: Record<string, AgentElementData>` with `agentInstance: AgentInstance` and `agentInstanceHistory: HistoryElement[]`. |
| `operate/client/src/modules/mock-server/handlers.ts` | Add three new handlers for the agent-instance endpoints. |
| `operate/client/src/modules/contexts/agentData.tsx` | Rewrite as a thin adapter over the three hooks + transform. Single path. No `detectAgent` import. |
| `operate/client/src/App/ProcessInstance/BottomPanelTabs/AiAgentTab/AgentDetailPanel.tsx` | Update import path of legacy types from `'modules/mock-server/agentDemoData'` → `'modules/contexts/agentData.types'`. |
| `operate/client/src/App/index.tsx` | Mount `<DemoLauncher />` inside `Wrapper`, gated by `import.meta.env.DEV`. |

### Delete

| Path | Reason |
|---|---|
| `operate/client/src/modules/mock-server/loanEvaluationDemoData/` (folder) | Second scenario being removed per the brief. |
| `operate/client/src/modules/mock-server/agentDemoData/agentEnrichmentData.ts` | Replaced by `agentInstanceData.ts` (data) and `agentData.types.ts` (types). |
| `operate/client/src/modules/contexts/detectAgent.ts` | Both `detectAgentElement` and `buildPlaceholderAgentData` are unused after the provider refactor (verified — only `agentData.tsx` imports them). |

---

## Task 1: Add agent-instance API types

**Files:**
- Create: `operate/client/src/modules/queries/agentInstances/types.ts`

- [ ] **Step 1: Create the types file**

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// TODO(API): The /v2/agent-instances/* endpoints are not yet in
// @camunda/camunda-api-zod-schemas/8.10. These hand-written types mirror the
// API spec at docs/superpowers/plans/2026-04-30-agent-prototype-collapse.md
// (and the source file in the design epic). Once the schemas package exposes
// endpoint definitions, replace these with imports from the package and
// delete this file.

export type AgentInstanceStatus =
  | 'IDLE'
  | 'THINKING'
  | 'CALLING_TOOL'
  | 'WAITING_FOR_TOOL_RESULT'
  | 'COMPLETED'
  | 'FAILED';

export type AgentInstanceToolSource = 'AD_HOC' | 'MCP';

export type AgentInstanceTool = {
  name: string;
  source: AgentInstanceToolSource;
};

export type AgentInstanceDefinition = {
  model: string;
  provider: string;
  systemPrompt: string;
  tools: AgentInstanceTool[];
};

export type AgentInstanceMetrics = {
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  toolCalls: number;
};

export type AgentInstance = {
  agentInstanceKey: string;
  status: AgentInstanceStatus;
  definition: AgentInstanceDefinition;
  metrics: AgentInstanceMetrics;
  creationTime: string;
  elementId: string;
  processInstanceKey: string;
  processDefinitionKey: string;
  tenantId: string;
};

export type HistoryElementRole =
  | 'user'
  | 'assistant'
  | 'tool_call'
  | 'tool_result';

export type HistoryElementContent = {
  // The spec lists `text` only and notes others may exist. Keep the union
  // open for forward compatibility but only `text` is implemented.
  contentType: 'text';
  content: string;
};

export type HistoryElementMetrics = Partial<{
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
}>;

export type HistoryElement = {
  historyElementKey: string;
  agentInstanceKey: string;
  elementInstanceKey: string;
  jobKey: string;
  role: HistoryElementRole;
  content: HistoryElementContent[];
  timestamp: string;
  metrics: HistoryElementMetrics;
  committed: boolean;
};

// ----- Request/response bodies for the three read endpoints -----

export type SearchAgentInstancesRequestBody = {
  pagination?: {from?: number; limit?: number};
  filter?: {
    processInstanceKey?: string;
    elementId?: string;
    elementInstanceKey?: string;
  };
};

export type SearchAgentInstancesResponseBody = {
  items: AgentInstance[];
  total: number;
};

export type SearchAgentInstanceHistoryRequestBody = {
  pagination?: {from?: number; limit?: number};
  filter?: {
    elementInstanceKey?: string;
    committed?: boolean;
  };
};

export type SearchAgentInstanceHistoryResponseBody = {
  items: HistoryElement[];
  total: number;
};
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd operate/client && npx tsc --noEmit`
Expected: no new errors (the file is currently unused, so this just checks syntax).

- [ ] **Step 3: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/modules/queries/agentInstances/types.ts
git commit -m "feat: add hand-written types for agent-instances API"
```

---

## Task 2: Move legacy AgentElementData types to a stable location

The current `AgentElementData`/`AgentIteration`/`AgentToolCall`/`AgentStatus`/`AgentFinishReason`/`AgentUsage`/`ConversationMessage` types live in `agentDemoData/agentEnrichmentData.ts`. They are imported by `agentData.tsx`, `detectAgent.ts`, and `AgentDetailPanel.tsx`. We're about to delete `agentEnrichmentData.ts` (Task 7), so move the types into a stable home and update consumers.

**Files:**
- Create: `operate/client/src/modules/contexts/agentData.types.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/index.ts` (re-export from new location for backwards compat during the rest of this refactor)
- Modify: `operate/client/src/modules/contexts/agentData.tsx` (import from new path)
- Modify: `operate/client/src/modules/contexts/detectAgent.ts` (import from new path — file gets deleted in Task 12 but we need it to compile in the meantime)
- Modify: `operate/client/src/App/ProcessInstance/BottomPanelTabs/AiAgentTab/AgentDetailPanel.tsx` (import from new path)

- [ ] **Step 1: Create the types file by copying the type declarations out of `agentEnrichmentData.ts`**

Open `operate/client/src/modules/mock-server/agentDemoData/agentEnrichmentData.ts` and copy lines 1–96 (the license header plus all `export type` / `export interface` blocks — everything *above* `export const MOCK_AGENT_ENRICHMENT_DATA`) into `operate/client/src/modules/contexts/agentData.types.ts`. Do not change the type bodies. The result file should contain: `AgentStatus`, `AgentToolCall`, `AgentFinishReason`, `AgentIteration`, `AgentUsage`, `ConversationMessage`, `AgentElementData` — all unchanged.

- [ ] **Step 2: In `agentEnrichmentData.ts`, replace the local declarations with re-exports**

In `operate/client/src/modules/mock-server/agentDemoData/agentEnrichmentData.ts`, remove the original type declarations (lines 9–96 in the current file, the block above `MOCK_AGENT_ENRICHMENT_DATA`) and replace them with:

```ts
export type {
  AgentStatus,
  AgentToolCall,
  AgentFinishReason,
  AgentIteration,
  AgentUsage,
  ConversationMessage,
  AgentElementData,
} from 'modules/contexts/agentData.types';

import type {AgentElementData} from 'modules/contexts/agentData.types';
```

(The `import type` is needed for `MOCK_AGENT_ENRICHMENT_DATA: Record<string, AgentElementData>` below.) Leave the rest of the file (the `MOCK_AGENT_ENRICHMENT_DATA` constant) intact — it'll be deleted entirely in Task 7.

- [ ] **Step 3: Update consumer imports**

In `operate/client/src/modules/contexts/agentData.tsx`, change lines 10–14 from:

```ts
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
} from 'modules/mock-server/agentDemoData';
```

to:

```ts
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
} from './agentData.types';
```

In `operate/client/src/modules/contexts/detectAgent.ts`, change lines 10–15 from:

```ts
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
  ConversationMessage,
} from 'modules/mock-server/agentDemoData';
```

to:

```ts
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
  ConversationMessage,
} from './agentData.types';
```

In `operate/client/src/App/ProcessInstance/BottomPanelTabs/AiAgentTab/AgentDetailPanel.tsx`, change lines 23–28 from:

```ts
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
  ConversationMessage,
} from 'modules/mock-server/agentDemoData';
```

to:

```ts
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
  ConversationMessage,
} from 'modules/contexts/agentData.types';
```

- [ ] **Step 4: Verify everything still compiles**

Run: `cd operate/client && npx tsc --noEmit`
Expected: zero errors. (The runtime behavior is unchanged — only import paths moved.)

- [ ] **Step 5: Run tests as a baseline**

Run: `cd operate/client && npm run test -- --run`
Expected: same pass/fail set as before this task. (No tests should regress; we haven't changed any behavior.)

- [ ] **Step 6: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/modules/contexts/agentData.types.ts \
  operate/client/src/modules/mock-server/agentDemoData/agentEnrichmentData.ts \
  operate/client/src/modules/contexts/agentData.tsx \
  operate/client/src/modules/contexts/detectAgent.ts \
  operate/client/src/App/ProcessInstance/BottomPanelTabs/AiAgentTab/AgentDetailPanel.tsx
git commit -m "refactor: move agent element data types to modules/contexts"
```

---

## Task 3: Add API client functions

**Files:**
- Create: `operate/client/src/modules/api/v2/agentInstances/fetchAgentInstance.ts`
- Create: `operate/client/src/modules/api/v2/agentInstances/searchAgentInstances.ts`
- Create: `operate/client/src/modules/api/v2/agentInstances/searchAgentInstanceHistory.ts`

These mirror `modules/api/v2/processInstances/fetchProcessInstance.ts` and `modules/api/v2/elementInstances/searchElementInstances.ts` but hand-write URLs/methods because the schemas package has no entry yet.

- [ ] **Step 1: Create `fetchAgentInstance.ts`**

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestWithThrow} from 'modules/request';
import type {AgentInstance} from 'modules/queries/agentInstances/types';

const fetchAgentInstance = async (agentInstanceKey: string) => {
  // TODO(API): replace once @camunda/camunda-api-zod-schemas exposes the
  // agent-instances endpoint definitions.
  return requestWithThrow<AgentInstance>({
    url: `/v2/agent-instances/${agentInstanceKey}`,
    method: 'GET',
  });
};

export {fetchAgentInstance};
```

- [ ] **Step 2: Create `searchAgentInstances.ts`**

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestWithThrow} from 'modules/request';
import type {
  SearchAgentInstancesRequestBody,
  SearchAgentInstancesResponseBody,
} from 'modules/queries/agentInstances/types';

const searchAgentInstances = async (
  payload: SearchAgentInstancesRequestBody,
  signal?: AbortSignal,
) => {
  // TODO(API): replace once @camunda/camunda-api-zod-schemas exposes the
  // agent-instances endpoint definitions.
  return requestWithThrow<SearchAgentInstancesResponseBody>({
    url: '/v2/agent-instances/search',
    method: 'POST',
    body: payload,
    signal,
  });
};

export {searchAgentInstances};
```

- [ ] **Step 3: Create `searchAgentInstanceHistory.ts`**

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestWithThrow} from 'modules/request';
import type {
  SearchAgentInstanceHistoryRequestBody,
  SearchAgentInstanceHistoryResponseBody,
} from 'modules/queries/agentInstances/types';

const searchAgentInstanceHistory = async (
  agentInstanceKey: string,
  payload: SearchAgentInstanceHistoryRequestBody,
  signal?: AbortSignal,
) => {
  // TODO(API): replace once @camunda/camunda-api-zod-schemas exposes the
  // agent-instances endpoint definitions.
  return requestWithThrow<SearchAgentInstanceHistoryResponseBody>({
    url: `/v2/agent-instances/${agentInstanceKey}/history/search`,
    method: 'POST',
    body: payload,
    signal,
  });
};

export {searchAgentInstanceHistory};
```

- [ ] **Step 4: Type-check**

Run: `cd operate/client && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/modules/api/v2/agentInstances/
git commit -m "feat: add API client functions for agent-instance read endpoints"
```

---

## Task 4: Add query-key factory entries

**Files:**
- Modify: `operate/client/src/modules/queries/queryKeys.ts`

- [ ] **Step 1: Add the agentInstances factory**

In `operate/client/src/modules/queries/queryKeys.ts`, add to the imports at the top:

```ts
import type {
  SearchAgentInstanceHistoryRequestBody,
  SearchAgentInstancesRequestBody,
} from './agentInstances/types';
```

And add a new factory entry inside the `queryKeys` object — place it alphabetically near `auditLogs` (so it slots in early in the object). Add this block:

```ts
  agentInstances: {
    get: (agentInstanceKey: string) => ['agentInstance', agentInstanceKey],
    search: (payload: SearchAgentInstancesRequestBody) => [
      'agentInstancesSearch',
      payload,
    ],
    historySearch: (
      agentInstanceKey: string,
      payload: SearchAgentInstanceHistoryRequestBody,
    ) => ['agentInstanceHistorySearch', agentInstanceKey, payload],
  },
```

- [ ] **Step 2: Type-check**

Run: `cd operate/client && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/modules/queries/queryKeys.ts
git commit -m "feat: add query keys for agent-instance hooks"
```

---

## Task 5: Add TanStack Query hooks

**Files:**
- Create: `operate/client/src/modules/queries/agentInstances/useAgentInstance.ts`
- Create: `operate/client/src/modules/queries/agentInstances/useSearchAgentInstances.ts`
- Create: `operate/client/src/modules/queries/agentInstances/useAgentInstanceHistory.ts`

These mirror `useProcessInstance.ts` (single GET with `skipToken` when no key) and `useProcessInstancesSearch.ts` (search with body + `enabled`).

- [ ] **Step 1: Create `useAgentInstance.ts`**

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery, type UseQueryResult} from '@tanstack/react-query';
import type {RequestError} from 'modules/request';
import {fetchAgentInstance} from 'modules/api/v2/agentInstances/fetchAgentInstance';
import {queryKeys} from '../queryKeys';
import type {AgentInstance} from './types';

const useAgentInstance = (
  agentInstanceKey: string | undefined,
): UseQueryResult<AgentInstance, RequestError> => {
  return useQuery({
    queryKey: queryKeys.agentInstances.get(agentInstanceKey ?? ''),
    queryFn: agentInstanceKey
      ? async () => {
          const {response, error} = await fetchAgentInstance(agentInstanceKey);
          if (response !== null) {
            return response;
          }
          throw error;
        }
      : skipToken,
  });
};

export {useAgentInstance};
```

- [ ] **Step 2: Create `useSearchAgentInstances.ts`**

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchAgentInstances} from 'modules/api/v2/agentInstances/searchAgentInstances';
import {queryKeys} from '../queryKeys';
import type {
  SearchAgentInstancesRequestBody,
  SearchAgentInstancesResponseBody,
} from './types';

type Options<T> = {
  enabled?: boolean;
  select?: (result: SearchAgentInstancesResponseBody) => T;
};

const useSearchAgentInstances = <T = SearchAgentInstancesResponseBody>(
  payload: SearchAgentInstancesRequestBody,
  options?: Options<T>,
) => {
  return useQuery({
    queryKey: queryKeys.agentInstances.search(payload),
    queryFn: async () => {
      const {response, error} = await searchAgentInstances(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled: options?.enabled,
    select: options?.select,
  });
};

export {useSearchAgentInstances};
```

- [ ] **Step 3: Create `useAgentInstanceHistory.ts`**

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery} from '@tanstack/react-query';
import {searchAgentInstanceHistory} from 'modules/api/v2/agentInstances/searchAgentInstanceHistory';
import {queryKeys} from '../queryKeys';
import type {
  SearchAgentInstanceHistoryRequestBody,
  SearchAgentInstanceHistoryResponseBody,
} from './types';

const useAgentInstanceHistory = (
  agentInstanceKey: string | undefined,
  payload: SearchAgentInstanceHistoryRequestBody = {},
) => {
  return useQuery<SearchAgentInstanceHistoryResponseBody>({
    queryKey: queryKeys.agentInstances.historySearch(
      agentInstanceKey ?? '',
      payload,
    ),
    queryFn: agentInstanceKey
      ? async () => {
          const {response, error} = await searchAgentInstanceHistory(
            agentInstanceKey,
            payload,
          );
          if (response !== null) {
            return response;
          }
          throw error;
        }
      : skipToken,
  });
};

export {useAgentInstanceHistory};
```

- [ ] **Step 4: Type-check**

Run: `cd operate/client && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/modules/queries/agentInstances/
git commit -m "feat: add TanStack Query hooks for agent-instance read endpoints"
```

---

## Task 6: Build the pure transform with 3 sanity tests

The transform takes one `AgentInstance` and a chronological `HistoryElement[]` and returns the legacy `AgentElementData` the components consume. Iteration grouping rule: an iteration is a sequence `[optional user] → assistant (with N tool_calls) → N tool_results`. The next assistant message starts a new iteration. Tool results are matched to their tool_call by *order of arrival* within the iteration (since the spec doesn't include tool_call IDs). If a tool_call has no matching tool_result, it stays in-flight (`status: 'ACTIVE'`, `output: undefined`).

This is a design prototype — only 3 sanity tests guard the transform. Visual verification (Task 15) is the real gate.

**Files:**
- Create: `operate/client/src/modules/queries/agentInstances/historyToAgentElementData.test.ts`
- Create: `operate/client/src/modules/queries/agentInstances/historyToAgentElementData.ts`

- [ ] **Step 1: Write the failing test file**

Create `operate/client/src/modules/queries/agentInstances/historyToAgentElementData.test.ts`:

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {historyToAgentElementData} from './historyToAgentElementData';
import type {AgentInstance, HistoryElement} from './types';

const baseInstance: AgentInstance = {
  agentInstanceKey: 'agent-1',
  status: 'CALLING_TOOL',
  definition: {
    model: 'claude-sonnet-4',
    provider: 'anthropic',
    systemPrompt: 'You are helpful.',
    tools: [
      {name: 'search_orders', source: 'AD_HOC'},
      {name: 'send_email', source: 'AD_HOC'},
    ],
  },
  metrics: {
    inputTokens: 1000,
    outputTokens: 200,
    totalTokens: 1200,
    toolCalls: 2,
  },
  creationTime: '2026-04-07T10:00:00.000Z',
  elementId: 'AI_Agent',
  processInstanceKey: 'pi-1',
  processDefinitionKey: 'pd-1',
  tenantId: '<default>',
};

const makeElement = (
  overrides: Partial<HistoryElement> & {
    role: HistoryElement['role'];
    content: string;
  },
  index: number,
): HistoryElement => ({
  historyElementKey: `he-${index}`,
  agentInstanceKey: 'agent-1',
  elementInstanceKey: 'ei-1',
  jobKey: 'job-1',
  timestamp: `2026-04-07T10:0${index}:00.000Z`,
  metrics: {},
  committed: true,
  ...overrides,
  content: [{contentType: 'text', content: overrides.content}],
});

describe('historyToAgentElementData', () => {
  it('should group one assistant + tool_call + tool_result into a single completed iteration', () => {
    const history: HistoryElement[] = [
      makeElement({role: 'user', content: 'Find order ORD-1'}, 1),
      makeElement({role: 'assistant', content: 'Looking it up.'}, 2),
      makeElement(
        {
          role: 'tool_call',
          content: JSON.stringify({
            name: 'search_orders',
            input: {orderId: 'ORD-1'},
          }),
        },
        3,
      ),
      makeElement(
        {role: 'tool_result', content: JSON.stringify({status: 'shipped'})},
        4,
      ),
    ];

    const result = historyToAgentElementData(baseInstance, history);

    expect(result.iterations).toHaveLength(1);
    expect(result.iterations[0]!.userMessage).toBe('Find order ORD-1');
    expect(result.iterations[0]!.agentMessage).toBe('Looking it up.');
    expect(result.iterations[0]!.toolCalls[0]!.toolName).toBe('search_orders');
    expect(result.iterations[0]!.toolCalls[0]!.status).toBe('COMPLETED');
    expect(result.iterations[0]!.toolCalls[0]!.output).toEqual({
      status: 'shipped',
    });
  });

  it('should leave a tool_call without a matching tool_result as ACTIVE with no output', () => {
    const history: HistoryElement[] = [
      makeElement({role: 'user', content: 'Send the email.'}, 1),
      makeElement({role: 'assistant', content: 'Sending now.'}, 2),
      makeElement(
        {
          role: 'tool_call',
          content: JSON.stringify({name: 'send_email', input: {to: 'a@b.c'}}),
        },
        3,
      ),
    ];

    const result = historyToAgentElementData(baseInstance, history);

    expect(result.iterations[0]!.toolCalls[0]!.status).toBe('ACTIVE');
    expect(result.iterations[0]!.toolCalls[0]!.output).toBeUndefined();
  });

  it('should match parallel tool_results to their tool_calls by arrival order', () => {
    const history: HistoryElement[] = [
      makeElement({role: 'user', content: 'Lookup and date.'}, 1),
      makeElement({role: 'assistant', content: 'Two parallel calls.'}, 2),
      makeElement(
        {
          role: 'tool_call',
          content: JSON.stringify({name: 'load_user', input: {id: 1}}),
        },
        3,
      ),
      makeElement(
        {role: 'tool_call', content: JSON.stringify({name: 'now', input: {}})},
        4,
      ),
      makeElement(
        {role: 'tool_result', content: JSON.stringify({email: 'a@b.c'})},
        5,
      ),
      makeElement(
        {role: 'tool_result', content: JSON.stringify({iso: '2026-04-07'})},
        6,
      ),
    ];

    const result = historyToAgentElementData(baseInstance, history);

    expect(result.iterations[0]!.toolCalls).toHaveLength(2);
    expect(result.iterations[0]!.toolCalls[0]!.toolName).toBe('load_user');
    expect(result.iterations[0]!.toolCalls[0]!.output).toEqual({email: 'a@b.c'});
    expect(result.iterations[0]!.toolCalls[1]!.toolName).toBe('now');
    expect(result.iterations[0]!.toolCalls[1]!.output).toEqual({
      iso: '2026-04-07',
    });
  });
});
```

- [ ] **Step 2: Run the failing tests**

Run: `cd operate/client && npx vitest run src/modules/queries/agentInstances/historyToAgentElementData.test.ts`
Expected: all 3 tests fail with `Cannot find module './historyToAgentElementData'`.

- [ ] **Step 3: Create the implementation**

Create `operate/client/src/modules/queries/agentInstances/historyToAgentElementData.ts`:

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AgentElementData,
  AgentIteration,
  AgentStatus,
  AgentToolCall,
  ConversationMessage,
} from 'modules/contexts/agentData.types';
import type {
  AgentInstance,
  AgentInstanceStatus,
  HistoryElement,
} from './types';

// TODO(API): the API spec does not yet include `limits`. Hardcoding to keep the
// existing UI counters working — replace once the spec lands the field.
const PROTOTYPE_MODEL_CALL_LIMIT = 10;
const PROTOTYPE_TOOL_CALL_LIMIT = 10;

const STATUS_MAP: Record<AgentInstanceStatus, AgentStatus> = {
  IDLE: 'INITIALIZING',
  THINKING: 'THINKING',
  CALLING_TOOL: 'WAITING_FOR_TOOL',
  WAITING_FOR_TOOL_RESULT: 'WAITING_FOR_TOOL',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
};

const textOf = (element: HistoryElement): string =>
  element.content
    .filter((c) => c.contentType === 'text')
    .map((c) => c.content)
    .join('\n');

const safeParse = (raw: string): unknown => {
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
};

type ToolCallPayload = {name: string; input?: Record<string, unknown>};

const parseToolCall = (raw: string): ToolCallPayload => {
  const parsed = safeParse(raw);
  if (
    parsed &&
    typeof parsed === 'object' &&
    'name' in parsed &&
    typeof (parsed as ToolCallPayload).name === 'string'
  ) {
    return parsed as ToolCallPayload;
  }
  return {name: 'unknown', input: {}};
};

function historyToAgentElementData(
  instance: AgentInstance,
  history: HistoryElement[],
): AgentElementData {
  const iterations: AgentIteration[] = [];
  let current: AgentIteration | null = null;
  let pendingUserMessage: string | undefined;
  let pendingResultIndex = 0; // points at the next tool_call awaiting a result

  const closeCurrent = () => {
    if (current) {
      iterations.push(current);
      current = null;
      pendingResultIndex = 0;
    }
  };

  for (const element of history) {
    const text = textOf(element);

    if (element.role === 'user') {
      closeCurrent();
      pendingUserMessage = text;
      continue;
    }

    if (element.role === 'assistant') {
      closeCurrent();
      current = {
        iterationNumber: iterations.length + 1,
        startTimestamp: element.timestamp,
        userMessage: pendingUserMessage,
        agentMessage: text,
        reasoning: text,
        toolCalls: [],
        tokenUsage: {
          input: element.metrics.inputTokens ?? 0,
          output: element.metrics.outputTokens ?? 0,
        },
        finishReason: 'TOOL_EXECUTION',
        messageId: element.historyElementKey,
      };
      pendingUserMessage = undefined;
      continue;
    }

    if (element.role === 'tool_call') {
      if (!current) {
        // Defensive: a tool_call without a preceding assistant. Open an empty
        // iteration so the call is still surfaced.
        current = {
          iterationNumber: iterations.length + 1,
          startTimestamp: element.timestamp,
          userMessage: pendingUserMessage,
          reasoning: '',
          toolCalls: [],
          tokenUsage: {input: 0, output: 0},
          finishReason: 'TOOL_EXECUTION',
        };
        pendingUserMessage = undefined;
      }
      const payload = parseToolCall(text);
      const toolCall: AgentToolCall = {
        toolName: payload.name,
        toolElementId: payload.name,
        toolDescription: '',
        rationale: '',
        input: payload.input ?? {},
        status: 'ACTIVE',
      };
      current.toolCalls.push(toolCall);
      continue;
    }

    if (element.role === 'tool_result') {
      if (!current) {
        continue;
      }
      const target = current.toolCalls[pendingResultIndex];
      if (target) {
        target.output = safeParse(text);
        target.status = 'COMPLETED';
        pendingResultIndex += 1;
      }
      current.endTimestamp = element.timestamp;
      continue;
    }
  }

  closeCurrent();

  const conversation: ConversationMessage[] = [
    {role: 'system', content: [instance.definition.systemPrompt]},
  ];
  for (const element of history) {
    const text = textOf(element);
    if (element.role === 'user') {
      conversation.push({
        role: 'user',
        content: [text],
        timestamp: element.timestamp,
      });
    } else if (element.role === 'assistant') {
      conversation.push({
        role: 'assistant',
        content: [text],
        timestamp: element.timestamp,
      });
    } else if (element.role === 'tool_call') {
      const payload = parseToolCall(text);
      conversation.push({
        role: 'assistant',
        content: [],
        timestamp: element.timestamp,
        toolCalls: [
          {
            id: element.historyElementKey,
            name: payload.name,
            arguments: payload.input ?? {},
          },
        ],
      });
    } else if (element.role === 'tool_result') {
      conversation.push({
        role: 'tool_call_result',
        content: [],
        timestamp: element.timestamp,
        toolResults: [
          {id: element.historyElementKey, name: '', content: text},
        ],
      });
    }
  }

  const firstUser = history.find((h) => h.role === 'user');
  const userPrompt = firstUser ? textOf(firstUser) : '';

  // statusDetail: name(s) of any in-flight tool call in the most recent iteration.
  const lastIteration = iterations[iterations.length - 1];
  const activeToolNames =
    lastIteration?.toolCalls.filter((tc) => tc.status === 'ACTIVE').map(
      (tc) => tc.toolName,
    ) ?? [];

  return {
    status: STATUS_MAP[instance.status] ?? 'THINKING',
    statusDetail:
      activeToolNames.length > 0 ? activeToolNames.join(', ') : undefined,
    modelProvider: instance.definition.provider,
    modelId: instance.definition.model,
    systemPrompt: instance.definition.systemPrompt,
    userPrompt,
    iterations,
    usage: {
      modelCalls: {
        current: iterations.length,
        limit: PROTOTYPE_MODEL_CALL_LIMIT,
      },
      tokensUsed: {
        inputTokens: instance.metrics.inputTokens,
        outputTokens: instance.metrics.outputTokens,
        reasoningTokens: 0,
        totalTokens: instance.metrics.totalTokens,
      },
      toolsCalled: {
        current: instance.metrics.toolCalls,
        limit: PROTOTYPE_TOOL_CALL_LIMIT,
      },
    },
    // TODO(API): tool source/description/parameters not in the spec yet — fill
    // with empty strings so the UI shows the name only.
    toolDefinitions: instance.definition.tools.map((t) => ({
      name: t.name,
      description: '',
    })),
    conversation,
  };
}

export {historyToAgentElementData};
```

- [ ] **Step 4: Run the tests and verify they pass**

Run: `cd operate/client && npx vitest run src/modules/queries/agentInstances/historyToAgentElementData.test.ts`
Expected: all 3 tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/modules/queries/agentInstances/historyToAgentElementData.ts \
  operate/client/src/modules/queries/agentInstances/historyToAgentElementData.test.ts
git commit -m "feat: add pure transform from agent-instance history to AgentElementData"
```

---

## Task 7: Reshape the agent demo data into API-shaped fixtures

Replace the 17-kB `agentEnrichmentData.ts` with a smaller, API-shaped `agentInstanceData.ts` that exports one `AgentInstance` plus a flat chronological `HistoryElement[]`. The conversation models exactly the existing 3-iteration scenario (find Leanne → load profile + date in parallel → ask human to send email).

**Files:**
- Create: `operate/client/src/modules/mock-server/agentDemoData/agentInstanceData.ts`
- Modify: `operate/client/src/modules/mock-server/agentDemoData/index.ts`
- Delete: `operate/client/src/modules/mock-server/agentDemoData/agentEnrichmentData.ts`

- [ ] **Step 1: Add a key constant for the agent instance**

In `operate/client/src/modules/mock-server/agentDemoData/constants.ts`, append:

```ts
export const MOCK_AGENT_AGENT_INSTANCE_KEY = '4451799813685200';
```

- [ ] **Step 2: Create the new fixture file**

Create `operate/client/src/modules/mock-server/agentDemoData/agentInstanceData.ts`:

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AgentInstance,
  HistoryElement,
} from 'modules/queries/agentInstances/types';
import {
  MOCK_AGENT_AGENT_INSTANCE_KEY,
  MOCK_AGENT_DEFINITION_KEY,
  MOCK_AGENT_INSTANCE_KEY,
  MOCK_AGENT_SUBPROCESS_KEY,
} from './constants';

const JOB_KEY = '4451799813685011';

export const MOCK_AGENT_INSTANCE: AgentInstance = {
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY,
  status: 'CALLING_TOOL',
  definition: {
    model: 'us.anthropic.claude-sonnet-4-20250514-v1:0',
    provider: 'AWS Bedrock',
    systemPrompt: `You are a helpful assistant that performs tasks on behalf of the user.

## Available Tools

You have access to the following tools:
- **ListUsers** — query the user directory
- **LoadUserByID** — fetch a single user profile by \`id\`
- **GetDateAndTime** — get the current UTC timestamp
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
      {name: 'AskHumanToSendEmail', source: 'AD_HOC'},
    ],
  },
  metrics: {
    inputTokens: 1959,
    outputTokens: 242,
    totalTokens: 2201,
    toolCalls: 4,
  },
  creationTime: '2026-03-26T14:30:00.300Z',
  elementId: 'AI_Agent',
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY,
  tenantId: '<default>',
};

const baseElement = (
  overrides: {
    historyElementKey: string;
    role: HistoryElement['role'];
    timestamp: string;
    content: string;
    metrics?: HistoryElement['metrics'];
  },
): HistoryElement => ({
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY,
  elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY,
  jobKey: JOB_KEY,
  committed: true,
  metrics: {},
  ...overrides,
  content: [{contentType: 'text', content: overrides.content}],
});

export const MOCK_AGENT_HISTORY_ELEMENTS: HistoryElement[] = [
  // ----- Iteration 1: user asks, agent calls ListUsers -----
  baseElement({
    historyElementKey: '6755399441055700',
    role: 'user',
    timestamp: '2026-03-26T14:30:00.400Z',
    content:
      'Find the email address of user Leanne Graham and send her an invitation to the company offsite.',
  }),
  baseElement({
    historyElementKey: '6755399441055701',
    role: 'assistant',
    timestamp: '2026-03-26T14:30:01.200Z',
    content:
      "I'll start by looking up the user directory to find Leanne Graham.",
    metrics: {inputTokens: 482, outputTokens: 87, totalTokens: 569},
  }),
  baseElement({
    historyElementKey: '6755399441055702',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:01.300Z',
    content: JSON.stringify({name: 'ListUsers', input: {}}),
  }),
  baseElement({
    historyElementKey: '6755399441055703',
    role: 'tool_result',
    timestamp: '2026-03-26T14:30:02.900Z',
    content: JSON.stringify([
      {id: 1, name: 'Leanne Graham', email: 'Sincere@april.biz'},
      {id: 2, name: 'Ervin Howell', email: 'Shanna@melissa.tv'},
    ]),
  }),

  // ----- Iteration 2: parallel LoadUserByID + GetDateAndTime -----
  baseElement({
    historyElementKey: '6755399441055704',
    role: 'assistant',
    timestamp: '2026-03-26T14:30:03.000Z',
    content:
      "Found Leanne Graham (id: 1). Loading her full profile and getting the current date in parallel.",
    metrics: {inputTokens: 621, outputTokens: 64, totalTokens: 685},
  }),
  baseElement({
    historyElementKey: '6755399441055705',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:03.050Z',
    content: JSON.stringify({name: 'LoadUserByID', input: {id: 1}}),
  }),
  baseElement({
    historyElementKey: '6755399441055706',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:03.100Z',
    content: JSON.stringify({name: 'GetDateAndTime', input: {}}),
  }),
  baseElement({
    historyElementKey: '6755399441055707',
    role: 'tool_result',
    timestamp: '2026-03-26T14:30:04.100Z',
    content: JSON.stringify({
      id: 1,
      name: 'Leanne Graham',
      email: 'Sincere@april.biz',
      phone: '1-770-736-8031 x56442',
      company: {name: 'Romaguera-Crona'},
    }),
  }),
  baseElement({
    historyElementKey: '6755399441055708',
    role: 'tool_result',
    timestamp: '2026-03-26T14:30:04.150Z',
    content: JSON.stringify({timestamp: '2026-03-26T14:30:04.245+00:00'}),
  }),

  // ----- Iteration 3: AskHumanToSendEmail in flight -----
  baseElement({
    historyElementKey: '6755399441055709',
    role: 'assistant',
    timestamp: '2026-03-26T14:30:04.290Z',
    content:
      "I have everything needed: Leanne's email is Sincere@april.biz and the current date is 2026-03-26. Requesting a human operator to send the invitation email.",
    metrics: {inputTokens: 856, outputTokens: 91, totalTokens: 947},
  }),
  baseElement({
    historyElementKey: '6755399441055710',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:04.300Z',
    content: JSON.stringify({
      name: 'AskHumanToSendEmail',
      input: {
        recipient_name: 'Leanne Graham',
        recipient_email: 'Sincere@april.biz',
        email_subject: 'Company Offsite Invitation',
        email_body:
          'Dear Leanne,\n\nYou are invited to the company offsite on March 28, 2026.\n\nBest regards',
      },
    }),
  }),
];
```

- [ ] **Step 3: Update the agentDemoData barrel exports**

Open `operate/client/src/modules/mock-server/agentDemoData/index.ts`. Replace the entire file with:

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export {
  MOCK_AGENT_INSTANCE_KEY,
  MOCK_AGENT_DEFINITION_KEY,
  MOCK_AGENT_DEFINITION_ID,
  MOCK_AGENT_SUBPROCESS_KEY,
  MOCK_AGENT_LLM_CALL_1_KEY,
  MOCK_AGENT_LLM_CALL_2_KEY,
  MOCK_AGENT_LLM_CALL_3_KEY,
  MOCK_AGENT_SUBPROCESS_ELEMENT_IDS,
  MOCK_AGENT_AGENT_INSTANCE_KEY,
} from './constants';

export {AGENT_BPMN_XML} from './agentBpmnXml';
export {
  MOCK_AGENT_PROCESS_INSTANCE,
  MOCK_AGENT_PROCESS_DEFINITION,
  MOCK_AGENT_ELEMENT_INSTANCES,
  MOCK_AGENT_ELEMENT_STATISTICS,
  MOCK_AGENT_SEQUENCE_FLOWS,
  MOCK_AGENT_VARIABLES,
  MOCK_AGENT_JOBS,
} from './agentProcessInstance';
export {
  MOCK_AGENT_INSTANCE,
  MOCK_AGENT_HISTORY_ELEMENTS,
} from './agentInstanceData';
```

(All legacy `AgentElementData` type re-exports are gone; consumers now import those from `modules/contexts/agentData.types` directly per Task 2. The `isAgentDemoInstance`/`isAgentDemoDefinition` helpers were unused — confirm with `git grep` and drop if unused. Run: `cd /Users/zsofia/Documents/GitHub/camunda && git grep -n "isAgentDemoInstance\|isAgentDemoDefinition"`. If only this file matches, leave them out — they're gone after the file overwrite.)

- [ ] **Step 4: Delete the old enrichment data file**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git rm operate/client/src/modules/mock-server/agentDemoData/agentEnrichmentData.ts
```

- [ ] **Step 5: Type-check**

Run: `cd operate/client && npx tsc --noEmit`
Expected: errors **only** in `scenarioRegistry.ts` (it still imports `MOCK_AGENT_ENRICHMENT_DATA` and `AgentElementData` from the deleted file). That's expected — Task 8 fixes it. Also expected: no errors anywhere else.

- [ ] **Step 6: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/modules/mock-server/agentDemoData/
git commit -m "feat: replace agent enrichment fixture with API-shaped agent-instance + history fixtures"
```

---

## Task 8: Update scenarioRegistry — drop loan scenario, swap data fields

**Files:**
- Modify: `operate/client/src/modules/mock-server/scenarioRegistry.ts`

- [ ] **Step 1: Replace the file contents**

Open `operate/client/src/modules/mock-server/scenarioRegistry.ts` and replace the entire file with:

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ProcessInstance,
  ProcessDefinition,
  Variable,
  ElementInstance,
  Job,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {
  AgentInstance,
  HistoryElement,
} from 'modules/queries/agentInstances/types';

import {
  MOCK_AGENT_INSTANCE_KEY,
  MOCK_AGENT_DEFINITION_KEY,
  MOCK_AGENT_DEFINITION_ID,
  MOCK_AGENT_SUBPROCESS_KEY,
  MOCK_AGENT_SUBPROCESS_ELEMENT_IDS,
  MOCK_AGENT_AGENT_INSTANCE_KEY,
} from './agentDemoData/constants';
import {AGENT_BPMN_XML} from './agentDemoData/agentBpmnXml';
import {
  MOCK_AGENT_PROCESS_INSTANCE,
  MOCK_AGENT_PROCESS_DEFINITION,
  MOCK_AGENT_ELEMENT_INSTANCES,
  MOCK_AGENT_ELEMENT_STATISTICS,
  MOCK_AGENT_SEQUENCE_FLOWS,
  MOCK_AGENT_VARIABLES,
  MOCK_AGENT_JOBS,
} from './agentDemoData/agentProcessInstance';
import {
  MOCK_AGENT_INSTANCE,
  MOCK_AGENT_HISTORY_ELEMENTS,
} from './agentDemoData/agentInstanceData';

type MockElementInstance = ElementInstance & {flowScopeKey: string};

export interface ScenarioDefinition {
  instanceKey: string;
  definitionKey: string;
  definitionId: string;
  name: string;
  description: string;
  pattern: 'subprocess' | 'task';
  agentElementId: string;
  agentElementInstanceKey: string;
  agentElementIds: Set<string>;
  agentInstanceKey: string;
  bpmnXml: string;
  processInstance: ProcessInstance;
  processDefinition: ProcessDefinition;
  elementInstances: MockElementInstance[];
  elementStatistics: {
    items: Array<{
      elementId: string;
      active: number;
      canceled: number;
      incidents: number;
      completed: number;
    }>;
  };
  sequenceFlows: {
    items: Array<{processInstanceKey: string; elementId: string}>;
  };
  variables: Variable[];
  jobs: Job[];
  agentInstance: AgentInstance;
  agentInstanceHistory: HistoryElement[];
}

export const SCENARIOS: ScenarioDefinition[] = [
  {
    instanceKey: MOCK_AGENT_INSTANCE_KEY,
    definitionKey: MOCK_AGENT_DEFINITION_KEY,
    definitionId: MOCK_AGENT_DEFINITION_ID,
    name: 'Agent chat with tools',
    description: 'Ad-hoc subprocess with agent + tools bundled together',
    pattern: 'subprocess',
    agentElementId: 'AI_Agent',
    agentElementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY,
    agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS,
    agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY,
    bpmnXml: AGENT_BPMN_XML,
    processInstance: MOCK_AGENT_PROCESS_INSTANCE,
    processDefinition: MOCK_AGENT_PROCESS_DEFINITION,
    elementInstances: MOCK_AGENT_ELEMENT_INSTANCES,
    elementStatistics: MOCK_AGENT_ELEMENT_STATISTICS,
    sequenceFlows: MOCK_AGENT_SEQUENCE_FLOWS,
    variables: MOCK_AGENT_VARIABLES,
    jobs: MOCK_AGENT_JOBS,
    agentInstance: MOCK_AGENT_INSTANCE,
    agentInstanceHistory: MOCK_AGENT_HISTORY_ELEMENTS,
  },
];

export function getScenarioByInstanceKey(
  key: string,
): ScenarioDefinition | undefined {
  return SCENARIOS.find((s) => s.instanceKey === key);
}

export function getScenarioByDefinitionKey(
  key: string,
): ScenarioDefinition | undefined {
  return SCENARIOS.find((s) => s.definitionKey === key);
}

export function getScenarioByAgentInstanceKey(
  key: string,
): ScenarioDefinition | undefined {
  return SCENARIOS.find((s) => s.agentInstanceKey === key);
}
```

- [ ] **Step 2: Type-check**

Run: `cd operate/client && npx tsc --noEmit`
Expected: errors only in `agentData.tsx` (still uses old `enrichmentData`) — fixed in Task 11. Loan scenario imports are gone.

- [ ] **Step 3: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/modules/mock-server/scenarioRegistry.ts
git commit -m "feat: reshape scenarioRegistry to expose agentInstance + agentInstanceHistory and drop second scenario"
```

---

## Task 9: Delete the loan-evaluation demo data folder

**Files:**
- Delete: `operate/client/src/modules/mock-server/loanEvaluationDemoData/` (entire folder)

- [ ] **Step 1: Verify nothing else still imports from the folder**

Run: `cd /Users/zsofia/Documents/GitHub/camunda && git grep -n "loanEvaluationDemoData\|MOCK_LOAN_" -- 'operate/client/src/'`
Expected: zero matches (the only remaining references were in `scenarioRegistry.ts`, removed in Task 8).

- [ ] **Step 2: Delete the folder**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git rm -r operate/client/src/modules/mock-server/loanEvaluationDemoData/
```

- [ ] **Step 3: Type-check**

Run: `cd operate/client && npx tsc --noEmit`
Expected: same errors as before (only `agentData.tsx`, fixed in Task 11). No new errors from the deletion.

- [ ] **Step 4: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add -A operate/client/src/modules/mock-server/loanEvaluationDemoData/
git commit -m "chore: remove loanEvaluationDemoData scenario"
```

---

## Task 10: Add MSW handlers for the three agent-instance endpoints

**Files:**
- Modify: `operate/client/src/modules/mock-server/handlers.ts`

- [ ] **Step 1: Add handlers**

In `operate/client/src/modules/mock-server/handlers.ts`, update the import line to add `getScenarioByAgentInstanceKey`:

```ts
import {
  SCENARIOS,
  getScenarioByInstanceKey,
  getScenarioByDefinitionKey,
  getScenarioByAgentInstanceKey,
} from './scenarioRegistry';
```

Then append the three new handlers to the `handlers` array (at the end, before the closing `]`):

```ts
  // GET single agent instance
  http.get('*/v2/agent-instances/:agentInstanceKey', ({params}) => {
    const scenario = getScenarioByAgentInstanceKey(
      params.agentInstanceKey as string,
    );
    if (scenario) {
      return HttpResponse.json(scenario.agentInstance);
    }
    return passthrough();
  }),

  // POST agent-instances search (filter by processInstanceKey / elementId)
  http.post('*/v2/agent-instances/search', async ({request}) => {
    const body = (await request.json()) as Record<string, unknown>;
    const filter = body?.filter as Record<string, unknown> | undefined;
    const piKey = filter?.processInstanceKey as string | undefined;
    const elementIdFilter = filter?.elementId as string | undefined;

    const scenario = piKey ? getScenarioByInstanceKey(piKey) : undefined;
    if (!scenario) {
      return passthrough();
    }
    if (
      elementIdFilter !== undefined &&
      elementIdFilter !== scenario.agentElementId
    ) {
      return HttpResponse.json({items: [], total: 0});
    }
    return HttpResponse.json({
      items: [scenario.agentInstance],
      total: 1,
    });
  }),

  // POST agent-instance history search
  http.post(
    '*/v2/agent-instances/:agentInstanceKey/history/search',
    async ({params, request}) => {
      const scenario = getScenarioByAgentInstanceKey(
        params.agentInstanceKey as string,
      );
      if (!scenario) {
        return passthrough();
      }
      const body = (await request.json()) as Record<string, unknown>;
      const filter = body?.filter as Record<string, unknown> | undefined;
      let items = [...scenario.agentInstanceHistory];
      if (typeof filter?.committed === 'boolean') {
        items = items.filter((h) => h.committed === filter.committed);
      }
      return HttpResponse.json({items, total: items.length});
    },
  ),
```

- [ ] **Step 2: Verify handlers are wired**

Run: `cd operate/client && npx tsc --noEmit`
Expected: no errors in `handlers.ts`.

- [ ] **Step 3: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/modules/mock-server/handlers.ts
git commit -m "feat: mock /v2/agent-instances read endpoints in MSW"
```

---

## Task 11: Refactor AgentDataProvider to consume the new query hooks

This is the smoking-gun removal. The provider becomes a thin adapter: it fetches the agent instance via search (filtered by processInstanceKey), then fetches its history, then runs the transform. Components keep their existing `useAgentData()` interface untouched.

**Files:**
- Modify: `operate/client/src/modules/contexts/agentData.tsx`

- [ ] **Step 1: Replace the file with the new implementation**

Open `operate/client/src/modules/contexts/agentData.tsx` and replace the entire file with:

```tsx
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useContext, useMemo} from 'react';
import {useAgentInstanceHistory} from 'modules/queries/agentInstances/useAgentInstanceHistory';
import {useSearchAgentInstances} from 'modules/queries/agentInstances/useSearchAgentInstances';
import {historyToAgentElementData} from 'modules/queries/agentInstances/historyToAgentElementData';
import {getScenarioByInstanceKey} from 'modules/mock-server/scenarioRegistry';
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
} from './agentData.types';

interface AgentDataContextValue {
  isAgentInstance: boolean;
  agentData: Record<string, AgentElementData> | null;
  getAgentDataForElement: (
    elementInstanceKey: string,
  ) => AgentElementData | null;
  isAgentElement: (elementId: string | null | undefined) => boolean;
  agentSubprocessKey: string | null;
  agentElementId: string | null;
  getIterationSummary: (elementId: string) => string | null;
  getIterationForElement: (elementId: string) => AgentIteration | null;
  getToolCallForElement: (
    elementId: string,
  ) => {tool: AgentToolCall; iteration: AgentIteration} | null;
  getAgentStatusLabel: () => string | null;
}

const EMPTY_VALUE: AgentDataContextValue = {
  isAgentInstance: false,
  agentData: null,
  getAgentDataForElement: () => null,
  isAgentElement: () => false,
  agentSubprocessKey: null,
  agentElementId: null,
  getIterationSummary: () => null,
  getIterationForElement: () => null,
  getToolCallForElement: () => null,
  getAgentStatusLabel: () => null,
};

const AgentDataContext = createContext<AgentDataContextValue>(EMPTY_VALUE);

const AgentDataProvider: React.FC<{
  processInstanceKey: string | undefined;
  processDefinitionKey: string | undefined;
  children: React.ReactNode;
}> = ({processInstanceKey, children}) => {
  // Look up the scenario to know which BPMN element ids belong to the agent
  // scope (used by `isAgentElement`). The scenario itself does NOT supply the
  // agent data — that comes from the queries below, via MSW handlers that read
  // from the same fixtures.
  const scenario = processInstanceKey
    ? getScenarioByInstanceKey(processInstanceKey)
    : undefined;

  const {data: agentInstancesResult} = useSearchAgentInstances(
    {filter: {processInstanceKey: processInstanceKey ?? ''}},
    {enabled: !!processInstanceKey},
  );
  const agentInstance = agentInstancesResult?.items[0];

  const {data: historyResult} = useAgentInstanceHistory(
    agentInstance?.agentInstanceKey,
    {filter: {committed: true}},
  );

  const value = useMemo<AgentDataContextValue>(() => {
    if (!agentInstance || !historyResult || !scenario) {
      return EMPTY_VALUE;
    }

    const elementData = historyToAgentElementData(
      agentInstance,
      historyResult.items,
    );
    const data: Record<string, AgentElementData> = {
      [scenario.agentElementInstanceKey]: elementData,
    };

    return {
      isAgentInstance: true,
      agentData: data,
      getAgentDataForElement: (elementInstanceKey) =>
        data[elementInstanceKey] ?? null,
      isAgentElement: (elementId) =>
        !!elementId && scenario.agentElementIds.has(elementId),
      agentSubprocessKey: scenario.agentElementInstanceKey,
      agentElementId: scenario.agentElementId,
      getIterationSummary: (elementId) => {
        const iteration = matchIteration(elementData, elementId);
        return iteration?.reasoning ?? null;
      },
      getIterationForElement: (elementId) =>
        matchIteration(elementData, elementId),
      getToolCallForElement: (elementId) => {
        for (const iteration of elementData.iterations) {
          const tool = iteration.toolCalls.find(
            (t) => t.toolElementId === elementId,
          );
          if (tool) {
            return {tool, iteration};
          }
        }
        return null;
      },
      getAgentStatusLabel: () => {
        if (
          elementData.status === 'COMPLETED' ||
          elementData.status === 'FAILED'
        ) {
          return null;
        }
        return 'Calling tools...';
      },
    };
  }, [agentInstance, historyResult, scenario]);

  return (
    <AgentDataContext.Provider value={value}>
      {children}
    </AgentDataContext.Provider>
  );
};

function matchIteration(
  elementData: AgentElementData,
  elementId: string,
): AgentIteration | null {
  const match = elementId.match(/^LLM_Call_(\d+)$/);
  if (!match) {
    return null;
  }
  const iterNum = parseInt(match[1]!, 10);
  return (
    elementData.iterations.find((it) => it.iterationNumber === iterNum) ?? null
  );
}

function useAgentData(): AgentDataContextValue {
  return useContext(AgentDataContext);
}

export {AgentDataContext, AgentDataProvider, useAgentData};
```

The provider returns `EMPTY_VALUE` whenever any of the three preconditions is missing: no scenario for the URL, no agent instance in the search response, or no history loaded yet. Real deployed processes hit the first precondition (no scenario for them) and never light up the agent panel.

- [ ] **Step 2: Type-check**

Run: `cd operate/client && npx tsc --noEmit`
Expected: zero errors. The provider no longer imports `detectAgent.ts`, but `detectAgent.ts` still exists and still compiles on its own (its only consumers are itself + `agentData.tsx`'s old code) — Task 12 deletes it.

- [ ] **Step 3: Run the full test suite as a sanity check**

Run: `cd operate/client && npm run test -- --run`
Expected: same pass/fail as Task 6's run, plus the transform's tests still pass. No regressions.

- [ ] **Step 4: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/modules/contexts/agentData.tsx
git commit -m "refactor: drive AgentDataProvider from agent-instance queries, remove placeholder branch"
```

---

## Task 12: Delete `detectAgent.ts`

**Files:**
- Delete: `operate/client/src/modules/contexts/detectAgent.ts`

- [ ] **Step 1: Confirm no remaining consumers**

Run: `cd /Users/zsofia/Documents/GitHub/camunda && git grep -n "detectAgentElement\|buildPlaceholderAgentData\|isAgentBusinessObject\|from './detectAgent'\|from \"./detectAgent\"" -- 'operate/client/src/'`
Expected: zero matches. (After Task 11, nothing imports it.)

- [ ] **Step 2: Delete the file**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git rm operate/client/src/modules/contexts/detectAgent.ts
```

- [ ] **Step 3: Type-check + lint**

Run: `cd operate/client && npx tsc --noEmit && npm run lint`
Expected: zero errors.

- [ ] **Step 4: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git commit -m "chore: remove detectAgent.ts (placeholder path no longer used)"
```

---

## Task 13: Add the `DemoLauncher` component

A floating, low-visual-weight launcher that reads `SCENARIOS` from the registry and renders one button per scenario. Clicking navigates to `/processes/<instanceKey>` via React Router (so the SPA stays in-app). Carbon `IconButton` + a small floating panel.

**Files:**
- Create: `operate/client/src/App/DemoLauncher/index.tsx`
- Create: `operate/client/src/App/DemoLauncher/styled.tsx`

- [ ] **Step 1: Create `styled.tsx`**

```tsx
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Button} from '@carbon/react';

const Container = styled.div`
  position: fixed;
  bottom: var(--cds-spacing-05);
  right: var(--cds-spacing-05);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-02);
  align-items: flex-end;
  pointer-events: none;
`;

const LauncherButton = styled(Button)`
  pointer-events: auto;
  opacity: 0.85;
  &:hover {
    opacity: 1;
  }
`;

export {Container, LauncherButton};
```

- [ ] **Step 2: Create `index.tsx`**

```tsx
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate} from 'react-router-dom';
import {SCENARIOS} from 'modules/mock-server/scenarioRegistry';
import {Paths} from 'modules/Routes';
import {Container, LauncherButton} from './styled';

const DemoLauncher: React.FC = () => {
  const navigate = useNavigate();

  if (SCENARIOS.length === 0) {
    return null;
  }

  return (
    <Container data-testid="demo-launcher">
      {SCENARIOS.map((scenario) => (
        <LauncherButton
          key={scenario.instanceKey}
          kind="tertiary"
          size="sm"
          data-testid={`open-demo-${scenario.instanceKey}-button`}
          onClick={() => navigate(Paths.processInstance(scenario.instanceKey))}
        >
          {`Open demo: ${scenario.name}`}
        </LauncherButton>
      ))}
    </Container>
  );
};

export {DemoLauncher};
```

- [ ] **Step 3: Type-check + lint**

Run: `cd operate/client && npx tsc --noEmit && npm run lint`
Expected: zero errors.

- [ ] **Step 4: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/App/DemoLauncher/
git commit -m "feat: add dev-only DemoLauncher component"
```

---

## Task 14: Mount the launcher in the App, dev-only and tree-shaken in prod

**Files:**
- Modify: `operate/client/src/App/index.tsx`

- [ ] **Step 1: Add the launcher to the Wrapper**

In `operate/client/src/App/index.tsx`, add to the imports near the other `App/...` imports:

```ts
import {DemoLauncher} from './DemoLauncher';
```

Then update the `Wrapper` component (currently lines 53–62) to render the launcher conditionally:

```tsx
const Wrapper: React.FC = () => {
  return (
    <>
      <RedirectDeprecatedRoutes />
      <SessionWatcher />
      <TrackPagination />
      <Outlet />
      {import.meta.env.DEV ? <DemoLauncher /> : null}
    </>
  );
};
```

Vite replaces `import.meta.env.DEV` with the boolean literal at build time. In a production build the conditional becomes `false ? <DemoLauncher /> : null`, which Rollup tree-shakes; the static import of `DemoLauncher` is dropped because its only reference is in the dead branch and the module has no side effects (it only declares functions and styled components).

- [ ] **Step 2: Type-check + lint**

Run: `cd operate/client && npx tsc --noEmit && npm run lint`
Expected: zero errors.

- [ ] **Step 3: Run dev server and visually confirm the launcher renders**

Run: `cd operate/client && npm run start`
Expected: dev server starts. In a browser at the dev URL, you see a small low-opacity "Open demo: Agent chat with tools" button in the bottom-right of every page. Clicking it navigates to the agent demo URL. (Stop the dev server with Ctrl-C before continuing.)

- [ ] **Step 4: Run production build and confirm the launcher is excluded**

Run: `cd operate/client && npm run build && grep -lr "Open demo:" build/ dist/ 2>/dev/null || echo "OK: launcher string not present in build output"`
Expected: prints `OK: launcher string not present in build output` (or finds nothing). If the string appears in any built JS file, the launcher is leaking into prod — investigate before continuing.

- [ ] **Step 5: Commit**

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add operate/client/src/App/index.tsx
git commit -m "feat: mount DemoLauncher in app shell, dev-only"
```

---

## Task 15: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Run lint**

Run: `cd operate/client && npm run lint`
Expected: zero errors, zero warnings introduced by this branch.

- [ ] **Step 2: Run the test suite**

Run: `cd operate/client && npm run test -- --run`
Expected: all tests pass, including the new `historyToAgentElementData.test.ts`.

- [ ] **Step 3: Confirm no orphan references**

Run: `cd /Users/zsofia/Documents/GitHub/camunda && git grep -nE "buildPlaceholderAgentData|detectAgentElement|MOCK_LOAN_|loanEvaluationDemoData|MOCK_AGENT_ENRICHMENT_DATA|agentEnrichmentData" -- 'operate/client/src/'`
Expected: zero matches.

- [ ] **Step 4: Hand off to the user for visual verification**

Tell the user the branch is ready and ask them to:
1. Start the dev server (`cd operate/client && npm run start`).
2. Click the "Open demo: Agent chat with tools" button bottom-right.
3. Confirm the agent panel/canvas/Details tab/AI Agent tab renders identically to the pre-refactor demo (they will compare against memory or screenshots).
4. Navigate to a real deployed process URL (any `/processes/<key>` that isn't the mock) and confirm no agent panel appears.
5. Reload the prod build (if accessible) and confirm the floating button does not show.

If anything renders incorrectly or differs from the pre-refactor demo, the most likely culprits are:
- The transform's iteration grouping (Task 6) — re-check by widening the test cases.
- A field the components read that the transform doesn't populate (e.g. `statusDetail`, `summary`, tool `description`/`parameters`) — fill it from a sensible source or add a known TODO.
- The MSW handlers not matching the URL the hooks call — open devtools Network tab and confirm the three new endpoints return 200 with mock JSON.

- [ ] **Step 5: Final commit if any verification fixups were needed**

If steps 1–4 passed cleanly, no commit is needed. If a fix was applied:

```bash
cd /Users/zsofia/Documents/GitHub/camunda
git add <touched files>
git commit -m "fix: <what was fixed>"
```

---

## Self-review notes (kept here for the reader)

- **Spec coverage:** every numbered item in the brief's "Concrete steps" section maps to one or more tasks above. (1)→Task 1; (2)→Tasks 3+4+5; (3)→Tasks 6+7; (4)→Task 10; (5)→Task 11; (6)→Tasks 8+9; (7)→Tasks 11+12; (8)→Tasks 13+14; (9)→Task 15.
- **Type consistency:** the legacy `AgentElementData` and friends live in `modules/contexts/agentData.types.ts` from Task 2 onward; every later task imports from there. The new API types (`AgentInstance`, `HistoryElement`, etc.) live in `modules/queries/agentInstances/types.ts` from Task 1 onward; the API clients, hooks, transform, and MSW handlers all import from there.
- **No placeholders:** every code step contains the file's full new contents or a precise edit. Verification steps include exact commands and expected outputs.
- **Boundary respected:** every modified path is under `operate/client/`. No backend, no other webapps, no new dependencies.
- **Flagged spec gaps:** TODOs are marked at the three places where the API spec is incomplete (no schemas-package endpoints; no tool description/params; no limits) — see "Known API spec gaps to flag" at the top.
