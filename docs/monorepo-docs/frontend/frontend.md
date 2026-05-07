# Frontend

Source of truth for frontend development of the orchestration cluster components
(Admin, Operate and Tasklist) hosted in `webapp/client`.

## Who this is for

Anyone working on the orchestration cluster frontend components.

## What you'll find here

- **[Getting started](./getting-started.md)** — clone, prerequisites, run backend + frontend locally.
- **[Project outline](./project-outline.md)** — packages and apps inside `webapp/client` and what each owns.
- **[Orchestration cluster webapp](./orchestration-cluster-webapp.md)** — deeper intro to the unified app: tech stack, layout, scripts, dev-server proxy, testing.
- **[Camunda API Zod schemas](./camunda-api-zod-schemas.md)** — installation, usage, and publishing for the `@camunda/camunda-api-zod-schemas` package.
- **[Data loading](./data-loading.md)** — TanStack Query + Zod schema patterns.
- **[Forms](./forms.md)** — form library choice and shared form-agnostic patterns.
- **[Development process](./development-process/development-process.md)** — pre-flight checklist plus playbooks for new pages, extending pages, and large features.
- **[Testing](./testing.md)** — unit (Vitest browser), integration (Playwright + MSW), a11y, and visual regression.
- **[Using AI](./using-ai.md)** — guidelines for AI-assisted frontend work.
- **[Code reviews](./code-reviews.md)** — what we look for.
- **[ADRs](./adr/adr.md)** — recorded architecture decisions.
- **[Code style](./code-style.md)** — formatting and naming conventions.
- **[Legacy components](./legacy-components.md)** — pointers to components still living outside `webapp/client`.

## Status

No component lives in `webapp/client` yet — we're currently laying the foundations.
The legacy components still ship from `identity/client`, `tasklist/client`,
`operate/client`, and `optimize/client`.

For the latest status, check the unification epic
([camunda/camunda#51305](https://github.com/camunda/camunda/issues/51305)) or the
Slack channel `#prj-pdp-3456-frontend-application-unification`.

## Get in touch

- Slack: `#team-core-features-frontend` — day-to-day questions and discussion.
- Bi-weekly team sync — ask in the Slack channel for the invite.

## Related references

- [`.github/instructions/frontend.instructions.md`](https://github.com/camunda/camunda/blob/main/.github/instructions/frontend.instructions.md) — scoped instructions auto-loaded by editors and AI agents.
- [docs.camunda.io](https://docs.camunda.io) — product documentation.
- Per-app READMEs that still apply until folded in:
  - [`operate/client/README.md`](https://github.com/camunda/camunda/blob/main/operate/client/README.md)
  - [`tasklist/client/README.md`](https://github.com/camunda/camunda/blob/main/tasklist/client/README.md)
  - [`identity/client/README.md`](https://github.com/camunda/camunda/blob/main/identity/client/README.md)
