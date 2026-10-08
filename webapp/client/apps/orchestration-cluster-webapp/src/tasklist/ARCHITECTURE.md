---
architecture_md: 1
component: camunda/camunda/webapp/client/apps/orchestration-cluster-webapp/src/tasklist
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [frontend-app]
summary: The Tasklist pod of the unified orchestration-cluster webapp, the browser UI at /tasklist where task workers find, claim and complete BPMN user tasks (with form-js forms or a variables editor) and start processes, built on the REST API v2 through the app's shared http layer.
team: { name: camunda/employee-engagement-tasklist, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/tasklist] }
owns:
  - "The task inbox: the user-task list with its filters (assigned to me, unassigned, completed, ...), sorting, infinite scroll, auto-select-next-task, and the URL search schema that holds them (modules/available-tasks/searchSchema.ts)"
  - "Custom task filters: the editor modals, the filter model (customFiltersSchema.ts) and how it maps to a user-task search request (getTasksRequestBody, prepareCustomFiltersParams)"
  - "Task detail: header, assign/unassign and complete flows (XState machines taskAssignmentMachine, taskCompletionMachine), denial and error messages, polling while a task is ASSIGNING, UPDATING, COMPLETING or CANCELING"
  - "Completing a task with its form: rendering the form-js schema the user-task form endpoint returns (FormManager, CamundaFormRenderer), loading only the variables the form uses, mapping submitted form data back to variables, document upload and preview in forms"
  - "Completing a task without a form: the variables editor (add, edit and validate JSON variables, Monaco JSON modal)"
  - "The task History tab (the user task's audit log) and the Process tab (BPMN diagram with the task's element highlighted, bpmn-js NavigatedViewer)"
  - "The Processes page: the list of startable processes, its filters, starting a process with or without a start form, the first-time start consent, and copying a start-form link"
  - "Tasklist preferences kept in the browser: the tasklist.* localStorage keys (custom filters, auto-select next task, native notifications, start consent, has-completed-task)"
  - "The tasklist.* translation keys and their wording in the app's locale files"
does_not_own:
  - { concept: "Shared webapp infrastructure: request wrapper, endpoints, the queries dictionary, errors, client and boot config, browser-storage, componentAccess, i18n setup, theme, header and app switcher, shared login/error/forbidden/404 pages", owner: camunda/camunda/webapp/client }
  - { concept: "The route tree and its shells (_shadcn, _carbon, _auth guard, session heartbeat, C3 provider); CODEOWNERS gives src/routes/ to the webapp team even for the tasklist route files", owner: camunda/camunda/webapp/client }
  - { concept: "@camunda/camunda-api-zod-schemas: request/response schemas and types for the REST API", owner: camunda/camunda/webapp/client }
  - { concept: "Unit-test and Playwright infrastructure (vitest-modules, shared-test-modules, MSW mock handlers, pw-modules)", owner: camunda/camunda/webapp/client }
  - { concept: "The REST endpoints Tasklist calls (user tasks, process definitions, start forms, documents, variables, audit logs)", owner: camunda/camunda/zeebe/gateway-rest }
  - { concept: "User-task lifecycle: assignment rules, completion semantics, task listeners, candidate users and groups", owner: camunda/camunda/zeebe/engine }
  - { concept: "Who may see, assign or complete a task, and access to the Tasklist component (authorizedComponents)", owner: camunda/camunda/security }
  - { concept: "Serving the SPA at /tasklist/**, custom.css, login redirect, camunda.webapps.tasklist.ui-enabled", owner: camunda/camunda/webapp/server }
  - { concept: "Form rendering, form fields, FEEL templating and validation inside a form", owner: bpmn-io/form-js }
  - { concept: "Document storage behind /v2/documents", owner: camunda/camunda/document }
  - { concept: "Design-system components, tokens and the camunda-ds ESLint rules", owner: "@camunda/design-system" }
  - { concept: "Operate and Admin UIs in the same app", owner: camunda/camunda/webapp/client/apps/orchestration-cluster-webapp/src/operate }
depends_on:
  - id: rest-api
    component: camunda/camunda/zeebe/gateway-rest
    kind: runtime-api
    contract: "Orchestration Cluster REST API v2: user tasks (search, get, assign, unassign, complete, form, variables, audit logs), process definitions (search, get, XML, start form), process instances (create), documents (create, get), variables, current user, system configuration, license"
    architecture: zeebe/gateway-rest/ARCHITECTURE.md
    versions: "same monorepo release; spec in zeebe/gateway-protocol/src/main/proto/v2/"
    workaround_policy: never
  - id: zod-schemas
    component: camunda/camunda/webapp/client
    kind: schema
    contract: "@camunda/camunda-api-zod-schemas/8.11 (types and runtime validation of every response), npm workspace package webapp/client/packages/camunda-api-zod-schemas"
    versions: "workspace package, 0.0.95 in the app's package.json; Tasklist imports the 8.11 release-line sub-module"
    workaround_policy: never
  - id: webapp-shared
    component: camunda/camunda/webapp/client
    kind: library
    contract: "#/shared/* (http/queries, http/endpoints, http/request, errors, config/getClientConfig, browser-storage/local-storage, componentAccess, i18n, json, pages/shadcn.components), the src/routes/_shadcn/_auth shell, #/vitest-modules and #/shared-test-modules"
    versions: same app, same build
    workaround_policy: never
  - id: webapp-server
    component: camunda/camunda/webapp/server
    kind: runtime-api
    contract: "WebappIndexController serves index.html for /tasklist/** with baseName, contextPath, isEnterprise, organizationId, clusterId; CustomCssController serves /custom.css; both only when camunda.webapps.tasklist.ui-enabled"
    versions: same monorepo release
    workaround_policy: never
  - id: form-js
    component: bpmn-io/form-js
    kind: library
    contract: "@bpmn-io/form-js-viewer (Form, importSchema, submit, FormFieldRegistry, FeelersTemplating, ConditionChecker; documentEndpointBuilder service replaced via additionalModules) and @bpmn-io/c4-theme CSS"
    versions: "pinned exactly (form-js-viewer 2.1.1, c4-theme 0.1.0) in orchestration-cluster-webapp/package.json"
    workaround_policy: adapter-boundary
  - id: bpmn-js
    component: bpmn-io/bpmn-js
    kind: library
    contract: "bpmn-js NavigatedViewer and outline for the Process tab; @bpmn-io/element-template-icon-renderer"
    versions: "pinned exactly in orchestration-cluster-webapp/package.json"
    workaround_policy: adapter-boundary
  - id: design-system
    component: "@camunda/design-system"
    kind: ui-library
    contract: "React components, icons, toast, PageLayout; Tailwind; enforced by the camunda-ds ESLint configs (recommended, tailwind) on src/tasklist"
    versions: "pinned exactly (0.68.1) in orchestration-cluster-webapp/package.json"
    workaround_policy: never
  - id: app-libraries
    component: "TanStack Router and Query, react-final-form, XState, i18next, Monaco, date-fns, lodash, zod"
    kind: external
    contract: "routing and loaders, server state, forms, task and start-process state machines, translations, JSON editor"
    versions: "pinned in orchestration-cluster-webapp/package.json, shared with the other pods"
    workaround_policy: adapter-boundary
consumers:
  - { who: "Task workers (end users) in SaaS and Self-Managed", via: "the /tasklist UI", promise: "UI; no API promise" }
  - { who: "Anyone holding a Tasklist link (bookmarks, copied start-form links, links from other tools)", via: "URLs /tasklist/<userTaskKey>, /tasklist/processes/<processDefinitionKey>/start and their search params", promise: "none written down" }
  - { who: "Self-Managed operators who restyle Tasklist", via: "/custom.css against Tasklist's DOM and class names", promise: "none written down" }
  - { who: camunda/camunda/webapp/client, via: "page and module imports from src/routes/_shadcn/_auth/tasklist; customFiltersSchema imported by shared/browser-storage/local-storage.ts", promise: "internal; changed in the same PR" }
  - { who: "qa/c8-orchestration-cluster-e2e-test-suite (tests/tasklist)", via: "the running UI (roles, labels, test ids)", promise: "none; nightly runs catch breakage" }
  - { who: "docs.camunda.io Tasklist pages", via: "test/docs-screenshots/tasklist", promise: "regenerated manually" }
  - { who: "Coding agents", via: ".claude/skills/tasklist-frontend", promise: "kept in step with the pod's layout" }
exposes:
  - { contract: "Pages (src/tasklist/pages/*Page.tsx) and modules (src/tasklist/modules/*) imported by the tasklist route files", spec: pages/, policy: "internal to the app; presentational, data passed in as props" }
  - { contract: "URL surface: /tasklist, /tasklist/<userTaskKey>[/history|/process], /tasklist/processes, /tasklist/processes/<processDefinitionKey>/start, /tasklist/login, and the validated search params", spec: modules/available-tasks/searchSchema.ts, policy: "user-visible; no written stability rule" }
  - { contract: "Persisted browser state: tasklist.* localStorage keys and the custom-filter schema", spec: ../shared/browser-storage/local-storage.ts, policy: "lives in users' browsers across upgrades; a schema change must still read old values" }
constraints:
  - { id: C1, name: API contract exists, hard: true, ref: ../../../../../../docs/monorepo-docs/frontend/development-process/before-starting.md }
  - { id: C2, name: Shared code and routes, hard: true, ref: ../../../../../../.claude/skills/tasklist-frontend/SKILL.md }
  - { id: C3, name: Permissions and component access, hard: true, ref: "../../../../../../docs/monorepo-docs/frontend/development-process/before-starting.md#does-the-feature-need-permission-handling" }
  - { id: C4, name: Eventual consistency and polling, hard: true, ref: "../../../../../../docs/monorepo-docs/frontend/development-process/before-starting.md#is-the-read-eventually-consistent" }
  - { id: C5, name: Multi-tenancy, hard: true, ref: "../../../../../../docs/monorepo-docs/frontend/development-process/before-starting.md#is-the-feature-tenant-aware" }
  - { id: C6, name: Forms and documents, hard: true, ref: modules/form-js/FormManager.ts }
  - { id: C7, name: Persisted browser state, hard: true, ref: ../shared/browser-storage/local-storage.ts }
  - { id: C8, name: Design system and accessibility, hard: true, ref: ../../../../eslint.config.js }
  - { id: C9, name: Translations, hard: false, ref: ../shared/i18n/locales/en.json }
  - { id: C10, name: SaaS and Self-Managed, hard: true, ref: ../../../../../server/src/main/java/io/camunda/webapp/controllers/WebappIndexController.java }
  - { id: C11, name: Links and E2E suites, hard: false, ref: ../../../../../../qa/c8-orchestration-cluster-e2e-test-suite }
decisions: ../../../../../../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/webapp/client/apps/orchestration-cluster-webapp/src/tasklist

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

Tasklist is the UI where task workers handle BPMN user tasks: they find tasks in an inbox, claim
them, fill in the task's form (or edit variables when it has none) and complete them, and they
start processes, with or without a start form. It is a pod area of the unified
`@camunda/orchestration-cluster-webapp`, served at `/tasklist` by `webapp/server`, and talks to the
cluster only through the REST API v2. Part of the [Orchestration Cluster system](../../../../../../SYSTEM.md)
(role `webapps`).

## 2. Ownership boundary

**Owns:** what the user sees and does in Tasklist: the inbox and its filters, task detail with its
assign/complete flows, forms and variables, the History and Process tabs, and the Processes page.
The full list is in the front matter.

Owner: [CODEOWNERS](../../../../../../CODEOWNERS) assigns `src/tasklist/` to
`@camunda/employee-engagement-tasklist` (the "Employee Engagement & Tasklist" pod in the
[tasklist-frontend skill](../../../../../../.claude/skills/tasklist-frontend/SKILL.md)). The same team
owns Tasklist's Playwright tests in `test/{integration,a11y,pages,visual}/tasklist/`, outside this
directory. Contact channel: TODO(confirm). The frontend docs name `#team-core-features-frontend`
for the whole webapp, not for this pod.

Route files are split: the tasklist-frontend skill tells the pod to add its routes under
`src/routes/_shadcn/_auth/tasklist/`, but CODEOWNERS gives all of `src/routes/` to
`@camunda/orchestration-cluster-webapps`. TODO(confirm): whether the pod owns its route files.

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A change to the request wrapper, error classes, config, storage, i18n setup, header or shared pages | `camunda/camunda/webapp/client` (`@camunda/orchestration-cluster-webapps`) | issue; engineer sign-off before changing `src/shared/` |
| A new entry in the shared queries dictionary (`#/shared/http/queries.ts`) | the pod may add it, kept free of business logic (skill § Data loading) | same PR |
| A new or changed endpoint schema | `@camunda/camunda-api-zod-schemas` in `webapp/client/packages` (`camunda-api-zod-schemas` skill) | PR, then publish |
| A new REST endpoint, filter or field on user tasks or process definitions | `camunda/camunda/zeebe/gateway-rest` (spec in `zeebe/gateway-protocol`) | issue, `component/c8-api` |
| Different assignment, completion or task-listener behavior | `camunda/camunda/zeebe/engine` | issue, `component/zeebe-engine` |
| Who can see or act on a task; access to Tasklist | `camunda/camunda/security` | issue |
| How `/tasklist` is served, custom CSS, login redirect | `camunda/camunda/webapp/server` (`.codeowners`: `@camunda/core-features`) | issue |
| A new form field or form behavior | `bpmn-io/form-js` | issue in that repo |
| A new design-system component or token | `@camunda/design-system`. TODO(confirm): repo and intake | TODO(confirm) |

## 3. Structure

Paths are relative to this directory unless they start with `src/` or `test/` (app root).

| Path | What it holds |
|---|---|
| `pages/` | Screens assembled from modules: `TasksLayoutPage`, `TaskDetailPage`, `TaskDetailsTaskPage`, `TaskDetailsHistoryPage`, `TaskDetailsProcessPage`, `TasklistProcessesPage`, `TasklistLoginPage`, `NoTaskSelectedPage` and their error/skeleton pages |
| `modules/available-tasks/` | Inbox list, filters, custom filters, search schema, request bodies |
| `modules/task-details/` | Task detail layout, assign/complete buttons and machines, error handling, process diagram |
| `modules/task-details-form/` | Task form view and variable selection for it |
| `modules/task-details-variables/` | Variables editor for tasks without a form |
| `modules/task-details-history/` | Audit-log table and detail modal |
| `modules/form-js/` | The form-js adapter: `FormManager`, `CamundaFormRenderer`, document upload |
| `modules/processes/` | Processes page tiles, filters, start-process machine and modal |
| `modules/dates/`, `modules/json/` | Small helpers used across modules |
| `src/routes/_shadcn/_auth/tasklist/`, `src/routes/_shadcn/tasklist.login.tsx` | Thin route files: loaders, pending/error components, page title, component access check |

Direction rules (from the [tasklist-frontend skill](../../../../../../.claude/skills/tasklist-frontend/SKILL.md)
and the [webapp docs](../../../../../../docs/monorepo-docs/frontend/orchestration-cluster-webapp.md)):

- Routes → pages → modules → `#/shared`. Routes load data (loader + `useSuspenseQuery`); pages and
  module components get it through props and don't fetch. Mutations live in module hooks
  (`useTaskCompletion`, `useStartProcess`).
- Tasklist imports nothing from `#/operate` or `#/admin`, and they import nothing from
  `#/tasklist`. No lint rule enforces this yet.
- One edge runs the other way: `src/shared/browser-storage/local-storage.ts` imports
  `customFiltersSchema` from this pod. TODO(confirm): accepted, or to be moved.
- Modules are flat: components in `components/`, everything else at the module root.

Variant-specific code: no separate code paths. SaaS vs Self-Managed and multi-tenancy are runtime
branches on the boot config (`organizationId`, `clusterId`) and the system configuration
(`getClientConfig()`).

The docs still list Carbon as the webapp's design system. Tasklist has moved entirely to
`@camunda/design-system` (shadcn) under the `_shadcn` route tree; only the shared Carbon shell
remains. TODO(confirm): that the Carbon-to-shadcn migration is finished for Tasklist.

## 4. Binding decisions

No ADR is specific to this pod. The frontend ADR folder named in the
[frontend ADR page](../../../../../../docs/monorepo-docs/frontend/adr/adr.md) (`webapp/client/adrs/`)
does not exist yet. Cross-cutting ADRs: [`docs/adr/`](../../../../../../docs/adr/README.md).
The rules that shape Tasklist features today come from docs and skills, not ADRs:

- Pod areas own their internal structure; shared code changes need cross-pod agreement
  ([orchestration-cluster-webapp.md](../../../../../../docs/monorepo-docs/frontend/orchestration-cluster-webapp.md)).
- Data loading order of preference: route loader + suspense query first
  ([data-loading.md](../../../../../../docs/monorepo-docs/frontend/data-loading.md)).
- API access only through `@camunda/camunda-api-zod-schemas` and the REST API (SYSTEM.md DR1).
- New UI is built with `@camunda/design-system` and Tailwind
  ([design-system-migrator skill](../../../../../../.claude/skills/design-system-migrator/SKILL.md)).
- Defaults for permissions, polling, pagination and tenancy
  ([before-starting.md](../../../../../../docs/monorepo-docs/frontend/development-process/before-starting.md)).

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — API contract exists
- **Question:** Are the endpoints and fields the feature needs in the REST API v2 and in
  `@camunda/camunda-api-zod-schemas/8.11`? If not, who adds them (gateway-rest, then the zod
  package and a publish) and in which release?
- **Hard:** yes
- **Detail:** [before-starting.md](../../../../../../docs/monorepo-docs/frontend/development-process/before-starting.md),
  [camunda-api-zod-schemas.md](../../../../../../docs/monorepo-docs/frontend/camunda-api-zod-schemas.md)

### C2 — Shared code and routes
- **Question:** Does the feature need anything in `src/shared/`, the route shells or the test
  infrastructure? Who on `@camunda/orchestration-cluster-webapps` signs off? Is a new queries-dictionary
  entry free of business logic?
- **Hard:** yes
- **Detail:** [tasklist-frontend skill](../../../../../../.claude/skills/tasklist-frontend/SKILL.md) § The shared folder boundary

### C3 — Permissions and component access
- **Question:** What does the user see on a 403: a toast on an action, a forbidden page or
  section on a load? Does it depend on a new permission (security first)?
- **Hard:** yes
- **Detail:** [before-starting.md § permissions](../../../../../../docs/monorepo-docs/frontend/development-process/before-starting.md#does-the-feature-need-permission-handling)

### C4 — Eventual consistency and polling
- **Question:** Which reads are `x-eventually-consistent`? How does the UI show a write the next
  read doesn't reflect yet (task states ASSIGNING, COMPLETING, UPDATING, CANCELING are polled
  every 5 s)? Pessimistic by default?
- **Hard:** yes
- **Detail:** [before-starting.md § eventual consistency](../../../../../../docs/monorepo-docs/frontend/development-process/before-starting.md#is-the-read-eventually-consistent)

### C5 — Multi-tenancy
- **Question:** With multi-tenancy on, does the feature show and filter by tenant and pass the
  tenant in requests? Does it still work with it off?
- **Hard:** yes
- **Detail:** [before-starting.md § tenancy](../../../../../../docs/monorepo-docs/frontend/development-process/before-starting.md#is-the-feature-tenant-aware)

### C6 — Forms and documents
- **Question:** Does the change touch form rendering or submission? Does it need a form-js
  feature or a version bump (`@bpmn-io/form-js-viewer` is pinned)? Do task forms, start forms
  and document upload/preview still work?
- **Hard:** yes
- **Detail:** `modules/form-js/FormManager.ts`, `CamundaFormRenderer.tsx`.
  TODO(confirm): which form-js versions and form schema versions Tasklist must support.

### C7 — Persisted browser state
- **Question:** Does the change add or change a `tasklist.*` localStorage key or the
  custom-filter schema? Do values saved by the previous release still parse?
- **Hard:** yes
- **Detail:** `src/shared/browser-storage/local-storage.ts`, `modules/available-tasks/customFiltersSchema.ts`

### C8 — Design system and accessibility
- **Question:** Is the UI built from `@camunda/design-system` with Tailwind (camunda-ds lint
  rules)? Does it pass the axe checks in light and dark themes?
- **Hard:** yes
- **Detail:** `webapp/client/eslint.config.js`, [testing.md](../../../../../../docs/monorepo-docs/frontend/testing.md)

### C9 — Translations
- **Question:** Are new strings `tasklist.*` keys in the locale files (en, de, es, fr)? Who
  provides the non-English text?
- **Hard:** no. TODO(confirm): whether all four locales are required at merge.
- **Detail:** `src/shared/i18n/locales/`

### C10 — SaaS and Self-Managed
- **Question:** Does the feature behave the same in SaaS (C3 notifications, `organizationId`)
  and Self-Managed (context path, delegated login, `custom.css`)? Does it depend on
  `isEnterprise` or the license?
- **Hard:** yes
- **Detail:** `webapp/server/.../WebappIndexController.java`

### C11 — Links and E2E suites
- **Question:** Does the change alter a URL, a search param or the DOM that the E2E suite,
  docs screenshots or customers' `custom.css` rely on? Who updates them?
- **Hard:** no
- **Detail:** `qa/c8-orchestration-cluster-e2e-test-suite/tests/tasklist`, `test/docs-screenshots/tasklist`

## 6. Data and persistence

Tasklist stores nothing on the server. Its server state comes from the REST API (secondary
storage reads, engine commands) and is cached by TanStack Query. In the browser it keeps the
`tasklist.*` localStorage keys (C7). The client config is in sessionStorage, owned by
`webapp/client`.

## 7. Cross-cutting qualities

- **Security:** the server authorizes; the pod checks `authorizedComponents` for `tasklist` on
  entry and handles 403s (C3). Auth is a session cookie with a CSRF header, set by the shared
  request wrapper.
- **Tenancy:** runtime toggle from the system configuration (C5).
- **Accessibility:** axe tests in `test/a11y/tasklist/` (light, dark); reduced motion respected
  in forms (`usePrefersReducedMotion`).
- **Performance:** infinite scroll and virtualized lists (`@tanstack/react-virtual`); polling only
  where task state is in flux.
- **i18n:** react-i18next with locales in `src/shared/i18n/locales/` (C9).
- **Observability:** none specific to the pod. TODO(confirm).

## 8. Delivery

As [Orchestration Cluster SYSTEM.md](../../../../../../SYSTEM.md) § 6: the app is built in
`webapp/client` (Maven runs the frontend build unless `-PskipFrontendBuild`) and ships inside the
cluster artifact. Tasklist is served only when `camunda.webapps.tasklist.ui-enabled` is on.
Feature flags live in `src/shared/feature-flags.ts`; none is Tasklist's today. Backports go
through the backport action.

## 9. Testing expectations

- **Unit:** `*.test.ts(x)` next to the code, Vitest browser mode in the `shadcn` project, MSW
  mocks from `#/shared-test-modules/mock-handlers` (`frontend-unit-test` skill).
- **Integration:** `test/integration/tasklist/` with page objects in `test/pages/tasklist/`;
  route behavior is tested here, not in `src/routes/` (ESLint forbids tests there).
- **Accessibility and visual:** `test/a11y/tasklist/`, `test/visual/tasklist/` (containerized
  browser, light/dark × desktop/tablet).
- **Docs screenshots:** `test/docs-screenshots/tasklist/`, run by hand when docs pages change.
- **E2E:** `qa/c8-orchestration-cluster-e2e-test-suite/tests/tasklist` against a real cluster.
- Run from the app: `npm run typecheck`, `npm run test:unit`, `npm run test:integration`; from
  `webapp/client`: `npm run lint`.

## 10. Planning conventions

- Issues: templates `2. feature_request.yml`, `3. task.yml`, `4. epic breakdown.yml` with the
  "Tasklist" component; label `component/tasklist` (`create-issue` skill).
- TODO(confirm): plans directory and ID prefix for plan refs.

## 11. Glossary

| Term | Meaning here |
|---|---|
| Pod area | A directory of the unified webapp (`src/tasklist`, `src/operate`, `src/admin`) owned by one team |
| Task form | The form-js schema of a task with a `formKey`, fetched from the user-task form endpoint and rendered by `FormManager`; whether it was embedded in the BPMN or linked to a deployed form is resolved server-side. A task without a `formKey` gets the variables editor |
| Start form | A form on a process's start event, rendered on the Processes page before the process instance is created |
| Custom filter | A named inbox filter the user saves in the browser (`tasklist.customFilters`) |
| Task state | `CREATED`, `COMPLETED`, `CANCELED`, … from the API; ASSIGNING, UPDATING, COMPLETING and CANCELING are transitional and polled |
