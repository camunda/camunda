# webapp/client

The unified frontend for Camunda 8 orchestration cluster components.

For development documentation, see the **Frontend** section of the monorepo docs:
[`docs/monorepo-docs/frontend/`](../../docs/monorepo-docs/frontend/frontend.md).

Start with [Getting started](../../docs/monorepo-docs/frontend/getting-started.md).

Operate's Carbon-compatible JSON editors live in
`apps/orchestration-cluster-webapp/src/operate/shared/Editors/`. They share the Monaco bootstrap in
`src/shared/monaco/loadMonaco.ts` and JSON helpers in `src/shared/json/` with Tasklist; new editor
consumers should reuse these rather than initialize another Monaco loader.
