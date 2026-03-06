---
applyTo: "operate/client/**,tasklist/client/**,identity/client/**,optimize/client/**"
---

# Frontend Development Conventions

The webapps (Operate, Tasklist, Identity, Optimize) each have a separate frontend under their
`client/` directory. Read the module's `client/README.md` before making changes.

## Tech Stack

- React, TypeScript, Vite, Carbon Design System (`@carbon/react`). Check each app's `package.json` for current versions.
- Testing: Vitest (Operate, Tasklist), react-scripts (Optimize legacy)
- Linting: ESLint, Prettier; Tasklist also uses Stylelint
- E2E: Playwright (Operate, Tasklist)
- Package manager: npm (Operate, Tasklist, Identity), Yarn (Optimize)

## Common Commands

Run these from the respective `client/` directory.

### Operate, Tasklist (npm)

```bash
npm ci                # Install dependencies
npm run build         # Build for production
npm run test          # Run unit tests
npm run lint          # Lint (ESLint + TypeScript check)
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
