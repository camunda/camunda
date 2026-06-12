---
applyTo: "operate/client/**,tasklist/client/**,identity/client/**,optimize/client/**,webapp/client/**"
---

# Frontend Development Conventions

The frontends live under `client/` directories. The orchestration cluster webapp at `webapp/client/`
is the unified frontend replacing the legacy apps. Read `webapp/client/README.md` and
`docs/monorepo-docs/frontend/frontend.md` before working on the unified app. For the legacy apps
(Operate, Tasklist, Identity, Optimize), read the module's `client/README.md` before making changes.

## Tech Stack

- React, TypeScript, Vite, Carbon Design System (`@carbon/react`). Check each app's `package.json` for current versions.
- Testing: Vitest (Operate, Tasklist), react-scripts (Optimize legacy). Identity has no unit test framework.
- Linting: ESLint, Prettier; Tasklist also uses Stylelint
- E2E: Playwright — cross-app E2E tests in `qa/c8-orchestration-cluster-e2e-test-suite/`, visual regression tests in each app's `client/` directory
- Package manager: npm (Operate, Tasklist, Identity), Yarn (Optimize)

## Common Commands

Run these from the respective `client/` directory.

### Operate, Tasklist (npm)

```bash
npm ci                # Install dependencies
npm run build         # Build for production
npm run test          # Run unit tests
npm run lint          # Lint (TypeScript + ESLint + Prettier check; runs Stylelint in tasklist)
npm run format        # Auto-format with Prettier
npm run start         # Start dev server
```

### Identity (npm)

```bash
npm ci                # Install dependencies
npm run build         # Build for production
npm run lint          # Lint (ESLint + TypeScript check)
npm run dev           # Start dev server
```

Note: Identity has minimal unit test setup (`test:unit` script is a no-op).

### Optimize (Yarn)

```bash
yarn install          # Install dependencies
yarn build            # Build for production
yarn test:ci          # Run unit tests (CI/non-interactive)
yarn start            # Start dev server
```

## Conventions

- Use Carbon Design System components; do not introduce alternative UI component libraries.
- Follow existing patterns in each app — Operate, Tasklist, and Identity have different
  conventions despite sharing the same tech stack.
- Tasklist uses Stylelint for CSS/SCSS; run `npm run stylelint` before committing style changes.
- Frontends are built as part of the Maven build by default. Skip with `-PskipFrontendBuild`.

## Orchestration Cluster Webapp (npm)

The unified frontend at `webapp/client/apps/orchestration-cluster-webapp/`. For full conventions,
see `docs/monorepo-docs/frontend/`.

```bash
# From webapp/client/
npm ci                    # Install dependencies (workspace)
npm run dev:oc            # Dev server on :3000
npm run prettier:format   # Auto-format with Prettier (always use this script, never npx prettier)
npm run lint              # ESLint + Prettier + Knip (unused exports/dependencies)

# From webapp/client/apps/orchestration-cluster-webapp/
npm run typecheck         # TypeScript across all tsconfigs (always use this script, never npx tsc)
npm run test:unit         # Vitest browser mode (headless Chromium)
npm run test:integration  # Playwright integration tests (MSW-mocked)
npm run test:a11y         # Playwright accessibility (light + dark)
npm run test:visual       # Playwright visual regression (needs Docker)
```

- Tech stack: React 19, TypeScript, Vite, TanStack Router, TanStack Query, Carbon, MSW
- Unit tests use Vitest Browser Mode (real Chromium), not jsdom. See `.claude/skills/frontend-unit-test/`.
- Playwright tests (integration, visual, a11y) use MSW via `@msw/playwright`. See `.claude/skills/frontend-integration-test/`.
- Follow the pod areas + shared + routes architecture. See `.claude/skills/frontend-feature/`.
- For migrating legacy Operate/Tasklist code to the unified app, see `.claude/skills/frontend-migrator/`.
