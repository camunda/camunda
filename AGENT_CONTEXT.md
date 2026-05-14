# Operate Notebook — Hackday Agent Context

> This file provides full context for a Claude Code session inside the devcontainer.
> Read this first before doing anything.

## Project

**Operate Notebook** — A Jupyter-style canvas inside Camunda Operate where users describe what
they want to see in natural language, and an LLM generates React components (widgets) that query
the V2 REST API and render live operational data.

**Track:** VEX (Hackday)

## What We're Building

A new section in Operate's frontend that lets users:

1. Create a **notebook/dashboard** (new route, e.g. `/notebooks/:id`)
2. Add **cells/widgets** by typing a natural language prompt
3. An LLM (copilot) converts the prompt into a **widget config** (JSON)
4. A **runtime renderer** maps the config to React components using Operate's existing primitives
5. Widgets query the **V2 REST API** for live data

### Widget Types

- **Tables/Lists** — process instances, incidents, variables, jobs (using existing `DataTable`/`SortableTable`)
- **BPMN Diagrams** — with overlays (heatmaps, stuck indicators) using existing `bpmn-js` integration
- **Charts** — time-series, distributions (may need a lightweight chart lib)
- **Action Panels** — buttons that call V2 mutations (retry incident, cancel instance, etc.)

### Widget Config Schema (Draft)

```json
{
  "type": "table | diagram | chart | action-panel",
  "title": "Stuck order-fulfillment instances",
  "query": {
    "endpoint": "/v2/process-instances/search",
    "method": "POST",
    "body": {
      "filter": { "processDefinitionId": "order-fulfillment", "state": "ACTIVE" },
      "sort": [{ "field": "startDate", "order": "ASC" }],
      "size": 50
    }
  },
  "columns": ["key", "processDefinitionId", "state", "startDate"],
  "actions": [
    { "label": "Cancel", "mutation": "/v2/process-instances/{key}/cancellation", "method": "POST" }
  ]
}
```

## Tech Stack (Existing in Operate)

- **React 18** + React Router DOM 7 (lazy-loaded routes)
- **Carbon Design System** (@carbon/react) — all UI primitives
- **MobX** for UI state, **TanStack React Query** for server state
- **styled-components** for CSS-in-JS
- **bpmn-js** / **dmn-js** for diagram rendering
- **Monaco Editor** for code/JSON editing
- **Vite** for builds, **Vitest** + Playwright for testing
- **Zod** for API schema validation (`@camunda/camunda-api-zod-schemas`)

### Key Directories

```
operate/client/src/
├── App/                          # Pages & routing
│   ├── Dashboard/               # Existing dashboard (home page)
│   ├── Processes/               # Process list
│   └── ProcessInstance/         # Instance detail (has BPMN canvas)
├── modules/
│   ├── api/v2/                  # V2 API client wrappers
│   ├── components/              # Reusable UI components (DataTable, Diagram, etc.)
│   ├── queries/                 # TanStack Query hooks
│   ├── mutations/               # TanStack Query mutations
│   ├── stores/                  # MobX stores
│   ├── hooks/                   # Custom React hooks
│   └── request/                 # HTTP request utilities (fetch + CSRF + auth)
```

### V2 API Surface (Key Endpoints for Widgets)

| Endpoint | Method | Use Case |
|----------|--------|----------|
| `/v2/process-instances/search` | POST | List/filter process instances |
| `/v2/process-instances/{key}` | GET | Instance detail |
| `/v2/process-instances/{key}/statistics/element-instances` | GET | Flow node stats |
| `/v2/process-definitions/search` | POST | List process definitions |
| `/v2/process-definitions/{key}/xml` | GET | BPMN XML for diagrams |
| `/v2/process-definitions/statistics/element-instances` | POST | Element stats per definition |
| `/v2/incidents/search` | POST | List incidents |
| `/v2/incidents/statistics/process-instances-by-error` | POST | Incident distribution |
| `/v2/incidents/{key}/resolution` | POST | Resolve incident (action) |
| `/v2/variables/search` | POST | Variable lookup |
| `/v2/jobs/search` | POST | Job queue inspection |
| `/v2/jobs/statistics/time-series` | POST | Job metrics over time |
| `/v2/user-tasks/search` | POST | User task list |
| `/v2/element-instances/search` | POST | Flow node instances |
| `/v2/process-instances/{key}/cancellation` | POST | Cancel instance (action) |
| `/v2/batch-operations/search` | POST | Batch operation status |

### Existing Component Primitives to Reuse

- `DataTable`, `PaginatedSortableTable`, `SortableTable` — tables
- `Diagram`, `DiagramShell` — BPMN canvas with overlays
- `JSONEditor`, `DiffEditor` — Monaco-based editors
- `FilterMultiSelect`, `FiltersPanel` — filtering UI
- `Modal`, `ModalStateManager` — dialogs
- `ResizablePanel` — split panes
- `InfiniteScroller` — virtualized lists
- `StateIcon`, `ElementInstanceIcon` — status indicators

## Development Workflow

### Running in This Container

This container has `--dangerously-skip-permissions` — no permission prompts.

```bash
# Build Operate module (first time or after backend changes)
./mvnw install -pl operate -am -Dquickly -T1C

# Frontend dev server
cd operate/client && npm install && npm run dev

# Run tests
cd operate/client && npm run test

# Format before commit (MANDATORY)
./mvnw license:format spotless:apply -T1C
```

### Agent Team

The orchestrator coordinates all work. These agents are available:

**Implementation agents (read/write):**
| Agent | Source | Use for |
|-------|--------|---------|
| `frontend-dev` | custom (~/.claude/agents/) | React/Carbon component implementation |
| `tdd-engineer` | custom | Write tests first, validate implementation |

**Review agents (read-only):**
| Agent | Source | Use for |
|-------|--------|---------|
| `code-reviewer` | plugin (pr-review-toolkit) | Quality, conventions, confidence ≥80 |
| `security-auditor` | plugin (code-modernization) | OWASP/CWE adversarial review |
| `architecture-critic` | plugin (code-modernization) | Skeptical design review, over-engineering |
| `code-simplifier` | plugin (pr-review-toolkit) | Catches unnecessary complexity |
| `silent-failure-hunter` | plugin (pr-review-toolkit) | Catches swallowed errors |
| `product-reviewer` | custom (has memory) | UX, product coherence, user perspective |
| `ui-qa` | custom | A11y (WCAG 2.1 AA) + Carbon compliance |

**Verification agents:**
| Agent | Source | Use for |
|-------|--------|---------|
| `e2e-verifier` | custom (has Playwright MCP) | Browser-based visual verification |

### Orchestration Matrix

**Follow this matrix exactly.** Each phase has a defined sequence of agents and a gate.

#### Phase 1: Skeleton (route + blank canvas + widget placeholder)

| Step | Agent | Task | Gate |
|------|-------|------|------|
| 1.1 | `tdd-engineer` | Write tests: route exists, NotebookPage renders, WidgetRenderer accepts config | Tests fail (red) |
| 1.2 | `frontend-dev` | Implement: route, NotebookPage, "Add Widget" button, WidgetRenderer placeholder | Tests pass (green) |
| 1.3 | Lint | `npm run lint` + `npm run fix:prettier` + spotless | Clean |
| 1.4 | `code-reviewer` | Review implementation | No Critical findings |
| 1.5 | `architecture-critic` | Review: is the component structure right? Over-engineered? | No Blockers |
| 1.6 | `product-reviewer` | Does the skeleton make sense as a UX foundation? | Not RETHINK |
| 1.7 | Build | `npm run test && npm run build` | Pass |
| 1.8 | Commit | Conventional commit to feature branch | |
| **1.9** | **HUMAN CHECKPOINT** | **Stephan pulls branch, opens in browser, gives go/no-go** | **Approval** |

#### Phase 2: Widget Runtime (table + diagram + actions)

| Step | Agent | Task | Gate |
|------|-------|------|------|
| 2.1 | `tdd-engineer` | Write tests: TableWidget renders data, DiagramWidget loads BPMN, ActionWidget calls mutations | Tests fail |
| 2.2 | `frontend-dev` | Implement TableWidget using DataTable + React Query | Tests pass |
| 2.3 | `frontend-dev` | Implement DiagramWidget using Diagram + BPMN XML fetch | Tests pass |
| 2.4 | `frontend-dev` | Implement ActionWidget with mutation buttons | Tests pass |
| 2.5 | `frontend-dev` | Wire prompt → hardcoded JSON config → renderer | Works |
| 2.6 | Lint | `npm run lint` + `npm run fix:prettier` + spotless | Clean |
| 2.7 | `code-reviewer` | Review all implementations | No Critical |
| 2.8 | `security-auditor` | Audit: XSS in dynamic rendering? Injection via widget config? Unsafe mutations? | No Critical/High |
| 2.9 | `product-reviewer` | Do widgets feel right? Is the interaction model intuitive? Useful empty/error states? | Not RETHINK |
| 2.10 | `ui-qa` | A11y + Carbon compliance on all widgets | Not BLOCKED |
| 2.11 | `silent-failure-hunter` | Check for swallowed fetch errors, missing error boundaries | Clean |
| 2.12 | `e2e-verifier` | Open browser, verify widgets render with real/mock data | Verified |
| 2.13 | Build + commit | `npm run test && npm run build` + commit | Pass |
| **2.14** | **HUMAN CHECKPOINT** | **Stephan tests widgets against real cluster** | **Approval** |

#### Phase 3: LLM Integration (copilot generates widget configs)

| Step | Agent | Task | Gate |
|------|-------|------|------|
| 3.1 | `architecture-critic` | Review approach: where does LLM call live? Proxy? Direct? Token handling? | No Blockers |
| 3.2 | `tdd-engineer` | Write tests: prompt input → LLM call → valid JSON config → rendered widget | Tests fail |
| 3.3 | `frontend-dev` | Implement copilot: prompt input, API call, config parsing, error handling | Tests pass |
| 3.4 | Lint | `npm run lint` + `npm run fix:prettier` + spotless | Clean |
| 3.5 | `security-auditor` | Audit: prompt injection? API key exposure? Unsafe eval of LLM output? | No Critical/High |
| 3.6 | `code-reviewer` | Review | No Critical |
| 3.7 | `code-simplifier` | Is the LLM integration over-engineered for a hackday? | No Blockers |
| 3.8 | `product-reviewer` | Does the prompt→widget flow feel magical? Are failure modes graceful? | Not RETHINK |
| 3.9 | `e2e-verifier` | Type a prompt in browser, verify widget appears | Verified |
| 3.10 | Build + commit | `npm run test && npm run build` + commit | Pass |
| **3.11** | **HUMAN CHECKPOINT** | **Stephan tries natural language prompts against real data** | **Approval** |

#### Phase 4: Polish (persistence, drag/drop, error boundaries)

| Step | Agent | Task | Gate |
|------|-------|------|------|
| 4.1 | `frontend-dev` | Persist notebook state to localStorage | Works |
| 4.2 | `frontend-dev` | Add error boundaries per widget (graceful failure) | Works |
| 4.3 | `frontend-dev` | Widget reorder (drag/drop or move up/down buttons) | Works |
| 4.4 | Lint | `npm run lint` + `npm run fix:prettier` + spotless | Clean |
| 4.5 | `ui-qa` | Full a11y pass on complete notebook experience | SHIP |
| 4.6 | `product-reviewer` | Full product review: is this demo-ready? Compelling story? | SHIP |
| 4.7 | `code-reviewer` + `security-auditor` | Final review | Clean |
| 4.8 | `e2e-verifier` | Full E2E: create notebook, add widgets, reorder, persist, reload | Verified |
| 4.9 | Build + commit | `npm run test && npm run build` + commit | Pass |
| **4.10** | **HUMAN CHECKPOINT** | **Stephan does final demo walkthrough** | **Done** |

### Human Checkpoint Protocol

At each checkpoint:
1. **Commit** all changes to the feature branch with conventional commit
2. **Summary** to human: what was built, all agent verdicts (pass/fail), any concerns
3. **STOP** — do not proceed to next phase without explicit human approval
4. **Prepare environment** for human testing (see below)
5. Human provides feedback → address before moving on

### Environment Preparation for Human Testing

Before each human checkpoint, ensure:

```bash
# 1. Frontend builds cleanly
cd operate/client && npm run build

# 2. If human has a local cluster, just provide the Operate URL
echo "Open: http://localhost:8080/notebooks"

# 3. If no cluster is running, start one:
# Option A: c8run (simplest)
c8run

# Option B: Docker compose (if available)
docker compose -f docker-compose-operate.yml up -d

# 4. Seed test data via V2 API (once cluster is up)
# Deploy a sample process
curl -X POST http://localhost:8080/v2/deployments \
  -F "resources=@sample-process.bpmn"

# Create process instances with various states
for i in $(seq 1 10); do
  curl -X POST http://localhost:8080/v2/process-instances \
    -H "Content-Type: application/json" \
    -d '{"processDefinitionId": "sample-process", "variables": {"orderId": "'$i'", "amount": '$((RANDOM % 10000))'}}'
done
```

The goal: human opens one URL and sees working widgets with real data. No manual setup.

## Implementation Plan

### Phase 1: Skeleton
1. Add `/notebooks` route to Operate's router
2. Create `NotebookPage` component with a blank canvas
3. Add "Add Widget" button that opens a prompt input (Monaco or textarea)
4. Create `WidgetRenderer` that takes a JSON config and renders a placeholder

### Phase 2: Widget Runtime
5. Implement `TableWidget` — maps config to `DataTable` + React Query fetch
6. Implement `DiagramWidget` — maps config to `Diagram` + BPMN XML fetch
7. Implement `ActionWidget` — buttons that call V2 mutations
8. Wire up the prompt → JSON config → renderer pipeline (hardcoded first)

### Phase 3: LLM Integration
9. Add copilot endpoint (could be a simple proxy to Claude API)
10. System prompt that knows the V2 API schema and widget config format
11. User prompt → LLM → JSON config → live widget

### Phase 4: Polish
12. Persist notebook state (localStorage or backend)
13. Drag/drop widget reordering
14. Widget resize
15. Error boundaries per widget

## Conventions (from AGENTS.md)

- Follow Google Java Format (Spotless) for any backend changes
- Frontend: Carbon Design System components, styled-components
- Tests: `should` prefix, `// given / when / then`, AssertJ (backend), Vitest (frontend)
- Commits: conventional commits, max 120 chars, no scopes
- **Always** run `./mvnw license:format spotless:apply -T1C` before committing
