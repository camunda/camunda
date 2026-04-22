---
applyTo: "operate/client/**,tasklist/client/**,identity/client/**,optimize/client/**"
---

# Frontend Development Conventions

The webapps (Operate, Tasklist, Identity, Optimize) each have a separate frontend under their
`client/` directory. Although they share a tech stack, each app has its own conventions — follow
the patterns established in the app you are editing, and read that module's `client/README.md`
before making non-trivial changes.

## Tech Stack

- React 18, TypeScript, Vite, Carbon Design System (`@carbon/react`). See each app's
  `package.json` for exact versions.
- Styled Components for CSS-in-JS (Operate, Tasklist); Carbon design tokens via CSS variables.
- State: MobX for UI/client state, TanStack Query for server state. See "State management" below.
- Testing: Vitest + Testing Library (Operate, Tasklist). Playwright for E2E and visual regression.
- Linting: ESLint, Prettier; Tasklist also uses Stylelint.
- E2E: cross-app tests in `qa/c8-orchestration-cluster-e2e-test-suite/`, per-app visual
  regression tests in each `client/` directory.
- Package manager: npm (Operate, Tasklist, Identity), Yarn (Optimize).

## Common Commands

Run these from the respective `client/` directory.

### Operate, Tasklist (npm)

```bash
npm ci                # Install dependencies
npm run build         # Build for production
npm run test          # Run unit tests
npm run lint          # Lint (ESLint + TypeScript check)
npm run fix:prettier  # Auto-format with Prettier
npm run start         # Start dev server
```

### Identity (npm)

```bash
npm ci                # Install dependencies
npm run build         # Build for production
npm run lint          # Lint (ESLint + TypeScript check)
npm run dev           # Start dev server
```

Note: Identity has a minimal unit test setup (`test:unit` is a no-op).

### Optimize (Yarn)

```bash
yarn install          # Install dependencies
yarn build            # Build for production
yarn test:ci          # Run unit tests (CI/non-interactive)
yarn start            # Start dev server
```

## Component structure (Operate, Tasklist)

Components are directories, not single files. A typical component directory contains:

```
EmptyState/
  index.tsx         # Component implementation, named export: `export {EmptyState}`
  index.test.tsx    # Vitest + Testing Library tests
  styled.tsx        # Styled Components for this component
```

Guidance:

- Keep styles in `styled.tsx`; do not inline CSS in `index.tsx`. Import styled components by
  name (`import {Grid, Title} from './styled'`), not as a namespace.
- `index.*` files expose the public surface of a directory. Prefer **named exports** over
  defaults (e.g., `export {EmptyState}`).
- Nest sub-components that are private to a parent under the parent directory; lift to
  `modules/components/` only when reused.
- Every new source file starts with the Camunda license header (copy from any existing file).

## Styled Components

- Import from `styled-components`: `import styled, {css} from 'styled-components'`.
- Use **Carbon design tokens** via CSS variables (`--cds-spacing-*`, `--cds-text-*`,
  `--cds-layer`, `--cds-border-*`) rather than hard-coded values.
- Use `@carbon/elements` styles for typography (e.g., `styles.productiveHeading02`) instead of
  redefining font rules.
- Use **transient props** (prefixed with `$`) for styling-only props that should not reach the
  DOM: `styled.header<{$size: 'sm' | 'md'}>`.

```ts
import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';

const Title = styled.h3`
  ${styles.productiveHeading02};
  color: var(--cds-text-secondary);
`;

export {Title};
```

Reference: `operate/client/src/modules/components/EmptyState/styled.tsx`,
`operate/client/src/modules/components/PanelHeader/styled.tsx`.

## UI components

- Use `@carbon/react` components (`Button`, `Stack`, `InlineLoading`, `DataTable`, etc.). Do
  not introduce alternative UI libraries.
- Prefer Carbon layout primitives (`Stack`, `Grid`) over ad-hoc flex/grid wrappers when they
  fit the use case.
- Respect the Carbon spacing scale — use `var(--cds-spacing-05)` rather than raw `px`/`rem`.

## State management

Operate and Tasklist split state by ownership: server state belongs to TanStack Query, shared
client state to MobX, and component-local state to `useState`. Do not mirror server state into
MobX.

### Server state → TanStack Query

- **Queries**: one hook per query under `modules/queries/<domain>/use<Thing>.ts`.
- **Mutations**: one hook per mutation under `modules/mutations/<domain>/use<Action>.tsx`.
- **Query keys**: centralized in `modules/queries/queryKeys.ts`. Reference the key factory at
  call sites (e.g., `queryKeys.processInstance.get(processInstanceKey)`) — do not hand-write
  key arrays. This keeps invalidation consistent across queries and mutations.
- **Fetch layer**: queries call thin functions in `modules/api/v2/<domain>/<operation>.ts` that
  return `requestWithThrow<T>(...)`. Components never call `fetch` directly.
- Use endpoint definitions from `@camunda/camunda-api-zod-schemas/8.10` instead of
  hand-constructing URLs.

Reference: `modules/queries/processInstance/useProcessInstance.ts`,
`modules/mutations/processInstance/useDeleteProcessInstance.tsx`.

### Shared UI state → MobX

Use MobX for state shared across components that is not server-owned (selection, panel
collapsed state, in-progress edits, etc.). Scope each store to a single concern.

```ts
import {makeAutoObservable} from 'mobx';

type State = {
  isEnabled: boolean;
};

const DEFAULT_STATE: State = {
  isEnabled: false,
};

class BatchModification {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  enable = () => {
    this.state.isEnabled = true;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const batchModificationStore = new BatchModification();
```

- Export a **singleton instance**, not the class — all consumers share the same state.
- Always provide a `reset()` method that restores `DEFAULT_STATE`. Route transitions and tests
  rely on it.
- Wrap components that read stores with `observer()` from `mobx-react`:
  `const InstancesTable = observer(({...}) => {...})`.

Reference: `modules/stores/batchModification.ts`, `modules/stores/panelStates.ts`.

### Local state → `useState`/`useReducer`

For state that does not leave a component, use the React primitives as usual. Reach for MobX
only when sharing is needed.

## Request/API layer

`modules/request` exposes `requestWithThrow`, `requestAndParse`, and `request`. Use
`requestWithThrow` for anything consumed via TanStack Query:

```ts
import {endpoints, type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {requestWithThrow} from 'modules/request';

const fetchProcessInstance = (processInstanceKey: string) =>
  requestWithThrow<ProcessInstance>({
    url: endpoints.getProcessInstance.getUrl({processInstanceKey}),
    method: endpoints.getProcessInstance.method,
  });

export {fetchProcessInstance};
```

The request layer handles CSRF, session management, and `401` responses centrally — do not
re-implement these per call.

## Testing

- Co-locate tests: `index.test.tsx`, `<Name>.test.tsx`, or `<store>.test.ts`.
- Import `render` and `screen` from `modules/testing-library` (Operate) or the Tasklist
  equivalent. `render` returns a pre-configured `user` (from `@testing-library/user-event`)
  alongside the Testing Library result.
- Prefer **accessible queries** — `getByRole`, `getByText`, `getByLabelText` — over
  `getByTestId` when both work. Reserve `data-testid` for elements with no accessible name.
- **`data-testid` naming**: `<verb>-<area>-<element>`, e.g., `delete-comment-button`,
  `confirm-delete-comment-button`. Be specific; avoid generic names like `delete-button`.
- Await async UI with `findBy*` / `waitFor` — never introduce arbitrary `setTimeout` delays.
- MSW handlers live under each app's `mocks/` or `msw/` directory.

## Imports

- Use the `modules/` alias for shared code (`import {request} from 'modules/request'`). Do not
  traverse with long relative paths (`../../../modules/...`).
- Group imports: external packages, then `modules/*` aliases, then relative.
- Use **named exports** for new modules; barrel `index.*` files re-export them by name.

## Naming

- **Event handler props**: `on*` — `onClick`, `onItemDelete`, `onAlertHover`. Put the noun
  before the verb (`onItemClick`, not `onClickItem`).
- **Event handler methods**: `handle*` in the component that owns the action; pass down as
  `on*` props.
- **Booleans**: `is*` or `has*`. Avoid negations (`isAllowed`, not `isNotAllowed`).
- **Stores**: `<concept>Store` singleton (e.g., `batchModificationStore`).
- **Query/mutation hooks**: `use<Thing>` / `use<Action>` (e.g., `useProcessInstance`,
  `useDeleteProcessInstance`).

## TypeScript

- Prefer `type` aliases over `interface` for props and local shapes.
- Type component props explicitly; do not rely on inference for public components.
- Reuse generated types from `@camunda/camunda-api-zod-schemas/8.10` for server data — do not
  redefine them.

## Accessibility

- Rely on semantic HTML and Carbon components, which handle the common cases.
- Every interactive element must be reachable by keyboard and announced by screen readers
  (provide a `title`, `aria-label`, or visually-hidden text where needed).
- For icon-only buttons, ensure an accessible name (e.g., via Carbon's `IconButton`
  `label` prop or a visually hidden `<span>`).
