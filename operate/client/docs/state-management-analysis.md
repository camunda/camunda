# Operate Frontend: State Management Analysis

> MobX + TanStack Query usage audit across `operate/client/src/`

---

## Architecture Overview

The codebase is in an **active migration** from MobX → TanStack Query for server state.

| Layer | Tool | Examples |
|-------|------|---------|
| **Server state** (target) | TanStack Query | 70+ query hooks in `modules/queries/`, 18 mutation hooks in `modules/mutations/` |
| **UI / interaction state** | MobX | modification mode, selection, theme, panel layout |
| **Legacy server state** | MobX (to migrate) | `licenseTag.ts`, `decisionDefinition.ts`, `elementInstancesTreeStore.ts` |

---

## 1. MobX Usage

### Store Inventory (20 stores, all module-level singletons)

| Store | Lines | Category |
|-------|-------|----------|
| `modifications.ts` | ~450 | **God store** — modification lifecycle, scope IDs, token ops |
| `elementInstancesTreeStore.ts` | 523 | **God store** — reimplements `useInfiniteQuery` + polling |
| `instancesSelection.ts` | 252 | Domain |
| `processInstanceMigration.ts` | 196 | Domain |
| `authentication.ts` | 175 | Auth (**layer violation** — imports `reactQueryClient` directly) |
| `notifications.tsx` | 115 | UI |
| `currentTheme.ts` | 99 | UI |
| `licenseTag.ts` | 68 | Server state in MobX — should be `useQuery` |
| `decisionDefinition.ts` | 42 | Server state in MobX — duplicates TanStack Query data |
| Others (11 stores) | 30–72 each | UI flags, filters, panels |

All stores use the functional API (`makeAutoObservable` / `makeObservable`). Zero decorator usage.

### Key Findings

- **Missing `observer` bug** — `VariablesFinalForm.tsx:27` reads `modificationsStore` inside `useMemo` without being wrapped in `observer`. MobX changes are silently ignored.
- **`when()` on non-observable** — `ProcessInstance/index.tsx:183` passes React state (`processTitle` from a TanStack Query hook) to `when()`, which only tracks MobX observables. Combined with a missing `useEffect` dependency array, this fires on every render but never reacts to actual changes.
- **`computed()` in render** — `TopPanel/index.tsx:202` creates a new `computed` box on every render, defeating memoization.
- **Undeclared actions** — `elementInstancesTreeStore.ts:417,490` mutates `isPollRequestRunning` outside any declared action (store uses explicit `makeObservable`).
- **Leaked listener** — `currentTheme.ts:42–47` registers `matchMedia.addEventListener` in the constructor with no cleanup (low risk since singleton).

### Disposal Patterns

Generally correct. All `reaction`/`when`/`autorun` in components are disposed via `useEffect` return. The `instancesSelection` store disposes its `autorunDisposer` in `reset()`.

---

## 2. TanStack Query Usage

### QueryClient

```ts
// modules/react-query/reactQueryClient.ts:11
const reactQueryClient = new QueryClient(); // ⚠️ No defaultOptions
```

**No `defaultOptions` set** — all 50+ queries inherit `staleTime: 0`, refetching on every mount.

### `staleTime` Configuration

| Hook | `staleTime` | Note |
|------|------------|------|
| `useCurrentUser` | `Infinity` | ✅ Session data |
| `useProcessDefinitionNames` | `120_000` | ✅ 2 min cache |
| `useProcessInstancesPaginated` | `5000` | Aligned with polling |
| `useProcessInstance` | `500` | Effectively near-zero |
| ~45 other hooks | **none** | Default 0 — re-fetch on every mount |

### Query Key Design

Keys are centralized in `modules/queries/queryKeys.ts` (~246 lines) using a factory pattern — **well-designed**.

**Exception**: `useProcessInstancesStatistics.tsx:17` defines a local `PROCESS_INSTANCES_STATISTICS_QUERY_KEY` string, bypassing the registry. These queries cannot be invalidated via the standard key structure.

**Stability concern**: Same file uses `...Object.values(payload)` in key construction — property insertion order could cause key mismatches. Better: `[key, processDefinitionKey, payload]`.

### `invalidateQueries` Anti-Pattern

5 batch operation mutations place `invalidateQueries` **inside `mutationFn`** instead of `onSuccess`:

- `useCancelProcessInstancesBatchOperation.ts:38`
- `useMigrateProcessInstancesBatchOperation.ts:39`
- `useModifyProcessInstancesBatchOperation.ts:42`
- `useResolveProcessInstancesIncidentsBatchOperation.ts:38`
- `useDeleteProcessInstancesBatchOperation.ts:37`

If `invalidateQueries` throws, the mutation appears failed even though the API call succeeded. Compare with `useResolveIncident.ts:33` which correctly uses `onSuccess`.

### Good Patterns

- **`select` for transforms** — `processDefinitions.ts:56–67` keeps data mapping out of components ✅
- **Conditional `refetchInterval`** — `useProcessInstance.ts:38` only polls while instance is running ✅
- **`skipToken`** — `useProcessInstance.ts:24` uses v5 `skipToken` idiom for conditional queries ✅
- **`enabled` guards** — widely used for optional params ✅

---

## 3. MobX ↔ TanStack Query Interaction

### Layer Violation: `authentication.ts`

```ts
import {reactQueryClient} from 'modules/react-query/reactQueryClient';

// handleLogin:
await reactQueryClient.ensureQueryData(currentUserQueryOptions);
// handleLogout:
reactQueryClient.clear();
```

A MobX store directly imports and drives the TanStack Query cache. Should use callbacks or React hooks to keep layers separate.

### Server-State Duplication

| MobX Store | Duplicated Data | TanStack Query Equivalent |
|------------|----------------|--------------------------|
| `licenseTag.ts` | License data + manual loading/error state | `useQuery` with `staleTime: Infinity` |
| `decisionDefinition.ts` | Decision `{name, id}` | `useDecisionDefinitionsSearch` already fetches this |
| `elementInstancesTreeStore.ts` | Paginated tree data + polling + abort | `useInfiniteQuery` + `refetchInterval` + `AbortSignal` |

### Dual-Layer Components

80+ components use both systems. Most are correct (MobX for UI state, TanStack Query for server data). The issues are concentrated in the specific bugs listed above.

---

## 4. Issue Summary

### 🔴 Critical

| Issue | Location |
|-------|----------|
| Missing `observer` — stale modification data | `VariablesFinalForm.tsx:27,36` |
| `when()` on React state, no dep array | `ProcessInstance/index.tsx:183` |
| MobX store controls TanStack Query cache | `authentication.ts:12–13,69,119` |
| `invalidateQueries` inside `mutationFn` (×5) | Batch operation mutation hooks |

### 🟠 Significant

| Issue | Location |
|-------|----------|
| No `defaultOptions` on QueryClient | `reactQueryClient.ts:11` |
| `computed()` created every render | `TopPanel/index.tsx:202` |
| Query keys bypass centralized registry | `useProcessInstancesStatistics.tsx:17` |
| 3 stores hold server state that belongs in TanStack Query | `licenseTag.ts`, `decisionDefinition.ts`, `elementInstancesTreeStore.ts` |

### 🟡 Minor

| Issue | Location |
|-------|----------|
| `isPollRequestRunning` mutated outside action | `elementInstancesTreeStore.ts:417,490` |
| `matchMedia` listener never removed | `currentTheme.ts:42–47` |
| Business logic in reactive store | `modifications.ts:162` |
| `retry: true` without explicit retryDelay | `useResolveIncident.ts:67` |

---

## 5. Migration Target

After full migration, the architecture should be:

- **MobX**: Pure UI/interaction state only. Stores should resemble `batchModification.ts` (41 lines, UI flags) or the core of `modifications.ts` (workflow state with no API calls).
- **TanStack Query**: All server state. Consider setting global `defaultOptions` with a sensible `staleTime` (e.g., 30s) and moving all remaining server-state stores into query hooks.
- **Boundary rule**: MobX stores should never import `reactQueryClient`. Communication between layers should flow through React components or injected callbacks.
