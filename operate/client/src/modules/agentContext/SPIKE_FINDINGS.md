# AI Agent Context Frontend Spike — Findings

## Summary

This spike validates that Operate's frontend **can** locate, retrieve, and parse the canonical
`agentContext` process variable for an AI Agent AHSP element instance using **only existing APIs**
(`POST /v2/variables/query`), without any new backend endpoints or exporter changes.

A dev-only debug tab ("Agent Context (Dev)") and console logger were added behind a feature flag
(`localStorage.setItem('AGENT_CONTEXT_DEBUG', 'true')` in dev mode) to demonstrate the parsing and
visualization.

---

## What the frontend can already infer from `agentContext` alone

| Capability | Status | Notes |
|---|---|---|
| Locate agentContext variable for an AHSP element instance | ✅ Works | Filtered query: `name=agentContext`, `scopeKey=<elementInstanceKey>` |
| Detect inline vs document-reference storage | ✅ Works | Parser inspects for `documentId` / `storeReference` fields |
| Parse inline conversation messages | ✅ Works | Supports multiple array patterns: root array, `.messages`, `.conversationHistory`, `.history`, `.chat`, `.context.messages` |
| Extract system & user prompts | ✅ Works | From message array (role=system, role=user) |
| Reconstruct iteration trail | ✅ Works | Groups assistant→tool message sequences into iterations |
| Extract reasoning / thinking blocks | ✅ Works | Parses `<thinking>...</thinking>` XML blocks from assistant content |
| Identify tool calls and results | ✅ Works | Normalizes both OpenAI-style (`function.name`) and flat (`name`) formats |
| Extract top-level metrics (tokens, iteration counts) | ✅ Works | Searches multiple known paths in the JSON |
| Detect truncation | ✅ Works | Uses `isTruncated` flag from variables API |
| Detect expired document references | ✅ Works | Compares `metadata.expiresAt` against current time |

## Where a dedicated backend endpoint is necessary

### 1. Document reference resolution

When `data.memory.storage.type` is not `in-process`, the `agentContext` variable contains a
**document reference** (pointer into the Camunda Document Store) instead of inline conversation
data. The frontend **cannot** resolve this reference because:

- There is no document retrieval API exposed to the frontend today.
- Even if there were, TTL-based documents may expire before the user inspects the process instance.
- Permissions / access control for the Document Store would need separate handling.

**Recommendation:** The backend-resolving-references endpoint (Solution 1, #50409) must:
- Accept `elementInstanceKey` and resolve the storage type internally.
- Fetch and parse the document content if it's a reference.
- Handle TTL expiry gracefully (return cached/snapshot data or clear error).
- Abstract away storage type differences so the frontend always gets a normalized response.

### 2. Variable value truncation

The `GET /v2/variables/{key}` and `POST /v2/variables/query` endpoints may truncate large values
(the `isTruncated` flag). For rich agent conversations with many iterations, tool calls, and
reasoning blocks, the payload can easily exceed the truncation threshold.

**Recommendation:** The backend endpoint should:
- Retrieve the full variable value without truncation (server-side).
- Or stream/paginate the conversation data.

### 3. Variable overwriting / snapshot stability

The `agentContext` variable is a **mutable runtime snapshot** — it is overwritten on each agent
iteration. If a user inspects the process instance while the agent is running, they see the latest
state, not a stable history. If the variable is subsequently overwritten by a later scope or
process modification, historical data is lost.

**Recommendation:** The backend solution should consider:
- Immutable conversation snapshots (append-only log stored separately from the mutable variable).
- Or capturing conversation history in the exporter for stable post-hoc analysis.

### 4. Custom / external memory stores

The agent connector supports `data.memory.storage.type = "in-process"` as the default, but
custom memory providers are possible. If a user configures an external memory store (e.g., Redis,
vector DB), the `agentContext` variable may contain only a reference or minimal state that cannot
be interpreted without provider-specific logic.

**Recommendation:** The backend endpoint should define a clear contract: if the conversation cannot
be reconstructed, return a structured error indicating the storage type and what (if any) fallback
data is available.

### 5. Performance for large payloads

Inline agent contexts for long-running agents with many tools can reach hundreds of KB. Parsing
this on the client adds latency and memory pressure, especially in the browser. The spike detects
payloads >500 KB and logs a warning.

**Recommendation:** The backend should:
- Paginate iteration data (e.g., return latest N iterations by default).
- Support field selection (e.g., iterations only, metrics only).

---

## Files created / modified

### New files
| File | Purpose |
|---|---|
| `src/modules/agentContext/types.ts` | TypeScript types for the reconstructed conversation model |
| `src/modules/agentContext/parseAgentContext.ts` | Parser: raw JSON → `AgentConversationModel` |
| `src/modules/agentContext/parseAgentContext.test.ts` | Unit tests for the parser (10 tests) |
| `src/modules/agentContext/useAgentContext.ts` | React Query hook: fetches + parses agentContext |
| `src/App/ProcessInstance/BottomPanelTabs/AgentContextTab/index.tsx` | Dev-only tab with console dump + minimal UI |

### Modified files
| File | Change |
|---|---|
| `src/modules/feature-flags.ts` | Added `IS_AGENT_CONTEXT_DEBUG_ENABLED` flag |
| `src/modules/Routes.tsx` | Added `processInstanceAgentContext` path |
| `src/modules/hooks/useCurrentPage.tsx` | Added `process-details-agent-context` page type |
| `src/App/index.tsx` | Added lazy route for AgentContextTab |
| `src/App/ProcessInstance/BottomPanelTabs/index.tsx` | Added conditional tab entry |

---

## How to activate the spike

```js
// In browser console (dev mode only):
localStorage.setItem('AGENT_CONTEXT_DEBUG', 'true');
location.reload();
```

Navigate to any process instance → select an AHSP element instance → click the "Agent Context
(Dev)" tab. The parsed model is also logged to the browser console with the `[Agent Context Debug]`
group.

---

## Edge cases discovered

1. **Unknown message format**: If the connector uses a non-standard message array key, the parser
   falls back to empty and logs a warning. The backend endpoint should normalize the format.

2. **Nested tool call formats**: Both OpenAI-style (`{function: {name, arguments}}`) and flat
   (`{name, arguments}`) tool call shapes are handled. Additional providers may use other shapes.

3. **Thinking/reasoning extraction**: Only supports `<thinking>...</thinking>` XML-style blocks.
   Some providers use `<Thought>` or JSON `reasoning` fields. The parser should be extended as more
   providers are integrated.

4. **Multiple agentContext scopes**: If an AHSP is nested inside another subprocess, the scope
   filter correctly narrows to the specific element instance. Multi-instance AHSP scenarios were
   not tested.
