---

name: frontend-unit-test
description: Use when writing, modifying, or debugging unit tests (*.test.tsx) in the orchestration cluster webapp (webapp/client/apps/orchestration-cluster-webapp/src/). Covers Vitest browser mode, MSW mocking, and vitest-browser-react rendering.

---

# Frontend Unit Testing

Unit tests in `@camunda/orchestration-cluster-webapp` run in a **real Chromium browser** via Vitest Browser Mode — not jsdom or happy-dom. This matters because components render in an actual DOM with real layout, events, and browser APIs. MSW intercepts all HTTP at the service worker level.

This is fundamentally different from the `@testing-library/react` + `vi.mock()` approach used in the legacy Operate and Tasklist frontends. If you're familiar with those patterns, read the key rules carefully — several habits from the legacy apps will produce broken or flaky tests here.

## Key rules

- Import `it` from `#/vitest-modules/test-extend`, not from `vitest`. The custom fixture auto-starts an MSW service worker before each test and resets/stops it after. Importing from `vitest` directly means no MSW interception — HTTP calls will fail or hit real endpoints.
- Use `render()` from `vitest-browser-react`, not from `@testing-library/react`. The vitest-browser-react renderer is designed for browser-mode Vitest and handles the real DOM lifecycle correctly. Do NOT import `screen` from `@testing-library/react` — it does not exist in this setup.
- For components that need routing context (pages, components using `<Link>`, `useNavigate`, route hooks), use `renderWithRouter(Component, {path, initialEntry?})` from `#/vitest-modules/render-with-router` instead of bare `render()`. It mounts the component in a minimal isolated route tree backed by an in-memory history — no full `routeTree.gen` is loaded, no `beforeLoad` runs.
- Both `render()` and `renderWithRouter()` return the `screen` object — you MUST capture the return value: `const screen = await renderWithRouter(MyPage, {path: '/my-path'})`. There is no global `screen` import.
- Use `expect.element()` for DOM assertions — it retries automatically until the assertion passes or times out. This replaces the `waitFor` / `findBy*` / `screen.findByRole` patterns you may know from Testing Library. There is no `waitFor` here.
- Perform all UI interactions (clicks, typing, filling, selecting, tabbing, hovering) with `userEvent` imported from `vitest/browser` — e.g. `await userEvent.click(screen.getByRole('button', {name: /save/i}))`, `await userEvent.fill(screen.getByLabelText('Name'), 'value')`. It dispatches a realistic full browser event sequence (pointer, mouse, focus, input). Keep using `screen.getBy*` for queries and `expect.element()` for assertions — `userEvent` is only for interactions. Do NOT use locator `.click()` or `.fill()` methods directly.
- Mock HTTP through the `worker` fixture using endpoint mocks from `#/shared-test-modules/mock-handlers`. Each mock is an individually named export (e.g., `mockCurrentUserEndpoint`, `mockLoginEndpoint`) created with `createEndpointMock` from `#/shared-test-modules/mock-endpoint`. Both unit and Playwright tests use the same definitions. All endpoint mocks must be defined in `apps/orchestration-cluster-webapp/shared-test-modules/mock-handlers.ts` — never create `createEndpointMock` calls inline in test files. Never use `vi.mock()` for API calls; it couples tests to implementation details and breaks on refactors.
- Prefer user-facing selectors in this order: `getByRole`, `getByLabelText`, `getByText`, and
  `getByTitle`. Use `getByTestId` only when no meaningful user-facing selector is available.
- String locators in this application use exact matching. Match the complete, case-sensitive text,
  accessible name, label, or title. Regular expressions control their own matching behavior.
- `screen.getBy*()` returns a lazy, retryable `Locator`, not a DOM element. Keep locators unresolved
  and compose queries from them instead of using raw DOM traversal.
- Prefer `getByRole` for interactive elements and elements with meaningful roles. The `name` option
  matches the accessible name, which may include nested controls, icons, tooltips, badges, or hidden
  accessible text. Use `getByText` for independently rendered static text and `getByTitle` when an
  existing `title` attribute is the stable identifier.
- Do not add or change production ARIA roles, `aria-label`, `title`, hidden text, wrapper elements,
  or test IDs solely to make a test selector possible. ARIA must describe genuine user-facing
  semantics. Improve the selector first; change production markup only when the existing markup has
  a real semantic or accessibility problem.
- Co-locate test files with source: a test for `src/shared/foo/bar.tsx` sits at `src/shared/foo/bar.test.tsx`; a test for `src/operate/components/Foo.tsx` sits next to it at `src/operate/components/Foo.test.tsx`. Pod areas follow their own conventions for test placement.
- Prefix test names with `should` (e.g., `it('should display an error on invalid credentials')`).
- Do not mock the router. Use `renderWithRouter(Component, {path})` from `#/vitest-modules/render-with-router` when the component needs routing context. It mounts the component in a fresh, isolated TanStack Router backed by an in-memory history — the component receives real route params, search params, and navigation. No full application route tree or global providers are loaded, which keeps tests fast and self-contained. Typed file-route hooks (`Route.useParams()`) will not resolve under the isolated router; use `useParams({from: '/your-path'})` instead.
- Never use `vi.mock()` for HTTP. For non-HTTP dependencies, prefer real implementations and mock
  only an unavoidable external or browser boundary.
- Do not use `// given / when / then` comments — that is a Java backend convention. Structure tests by visual grouping (blank lines between setup, action, and assertion).

## Locator composition

Keep locators unresolved so interactions and assertions retain strict matching, retry behavior, and
useful diagnostics.

Chain selectors from a parent locator to scope a descendant:

```tsx
const betaProcessLink = screen.getByTitle('Beta Process – 3 Instances in 1 Version');
const alphaProcessLink = screen.getByTitle('Alpha Process – 6 Instances in 1 Version');

await expect.element(betaProcessLink).toBeVisible();
await expect.element(alphaProcessLink).toBeVisible();

await expect.element(betaProcessLink.getByTestId('draining-indicator')).toBeVisible();
await expect.element(alphaProcessLink.getByTestId('draining-indicator')).not.toBeInTheDocument();
```

Assert that a parent exists before making a negative assertion about one of its descendants.
Otherwise, the negative assertion could pass because the entire parent failed to render.

Use `.filter()` to narrow a collection:

```tsx
const invoiceRow = screen
  .getByRole('row')
  .filter({has: screen.getByRole('link', {name: 'Invoice Process'})});

await expect.element(invoiceRow.getByRole('button', {name: 'Delete'})).toBeEnabled();
```

Available filters include `has`, `hasNot`, `hasText`, and `hasNotText`. Prefer semantic filtering
before using `.first()`, `.last()`, or `.nth()`.

Do not unwrap locators for ordinary assertions:

```tsx
// Wrong: resolves eagerly, loses locator retries, and requires unsafe casts.
const row = screen.getByText('Invoice Process').element().closest('a') as HTMLElement;
const indicator = row.querySelector('[data-testid="draining-indicator"]') as HTMLElement;

// Correct: remains scoped, strict, and retryable.
const processLink = screen.getByTitle('Invoice Process – 3 Instances in 1 Version');

await expect.element(processLink.getByTestId('draining-indicator')).toBeVisible();
```

`element()`, `query()`, `elements()`, and `findElement()` are escape hatches. Use them only when an
external library or browser API requires a raw DOM element. Never use `parentElement`, `closest`,
`querySelector`, or an element cast merely to work around a locator.

## MSW mocking

Endpoint mocks live in `#/shared-test-modules/mock-handlers`. Each entry is created with
`createEndpointMock` from `#/shared-test-modules/mock-endpoint`, which builds a typed MSW handler
factory for a given endpoint and HTTP method.

Two shapes:

- **With Zod schema validation**: pass `schema`, `successResponse`, and `failureResponse`. The handler validates the request body against the schema and returns the failure response if it doesn't match.
- **Without validation**: pass only `successResponse`. The handler always returns the success response.

The `worker` fixture is injected by the custom `it` and auto-resets between tests — no manual `worker.resetHandlers()` needed.

## Test structure

### Testing a standalone component (no routing context)

See `src/operate/shared/TenantField/TenantField.test.tsx` for a complete example that registers
`mockCurrentUserEndpoint`, renders a standalone component with providers, interacts through
`userEvent`, and asserts through `expect.element()`.

### Testing a page or component that needs routing context

Use `renderWithRouter(Component, {path, initialEntry?})` — it mounts the component in a minimal isolated route tree with a fresh `QueryClient` and memory history. No `beforeLoad`, no global providers, no full route tree. Pass `initialEntry` when the path contains params (e.g. `path: '/users/$id'`, `initialEntry: '/users/42'`).

```tsx
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {LoginPage} from './LoginPage';

describe('<Login />', () => {
  it('should not allow the form to be submitted with empty fields', async () => {
    const screen = await renderWithRouter(LoginPage, {path: '/login'});

    await userEvent.click(screen.getByRole('button', {name: /login/i}));

    await expect.element(screen.getByLabelText(/username/i)).toBeInvalid();
    await expect.element(screen.getByLabelText(/^password$/i)).toBeInvalid();
  });
});
```

## Assertion patterns

Use `expect.element()` for DOM assertions. It resolves the locator and retries the assertion until it
passes or times out.

```tsx
// Visibility
await expect.element(screen.getByRole('button', {name: 'Submit'})).toBeVisible();

// Complete normalized text content
await expect.element(screen.getByRole('heading')).toHaveTextContent('Dashboard');

// Partial text content
await expect
  .element(screen.getByRole('dialog'))
  .toMatchTextContent('This operation is part of a batch.');

// Regular-expression text content
await expect.element(screen.getByRole('tooltip')).toMatchTextContent(/created on/i);

// Attributes
await expect
  .element(screen.getByRole('link', {name: 'Documentation'}))
  .toHaveAttribute('href', '/docs');

// Element should not exist
await expect.element(screen.getByText('Loading...')).not.toBeInTheDocument();

// Element remains mounted but should be hidden
await expect.element(screen.getByRole('dialog')).not.toBeVisible();
```

Use `toHaveTextContent` when the element's complete normalized text is the intended contract. Use
`toMatchTextContent` for a substring or regular-expression match.

Use `not.toBeInTheDocument()` when an element should not exist. Use `not.toBeVisible()` only when the
element should remain mounted but hidden.

Do not wrap `expect.element()` in `waitFor`, and do not use `findBy*` queries.

## Parameterized tests

Use `it.for` instead of a `for`, `for...of`, or `forEach` loop whose body declares `it()` calls.
Ordinary loops inside a test body remain valid.

The extended `it` passes case data as the first callback argument and fixture context as the second.

Use `%s` for scalar cases:

```tsx
it.for(['include', 'exclude'] as const)(
  'should submit in %s mode',
  async (mode, {worker}) => {
    // ...
  },
);
```

Use named objects and `$property` placeholders when a case has multiple values:

```tsx
it.for([
  {filter: 'businessId', label: 'Business ID'},
  {filter: 'errorMessage', label: 'Error Message'},
] as const)(
  'should display $label',
  async ({filter, label}, {worker}) => {
    // ...
  },
);
```

Tuple cases are passed as one value. Destructure the tuple in the first callback argument:

```tsx
it.for([
  ['Delete', mockDeleteEndpoint],
  ['Cancel', mockCancelEndpoint],
] as const)(
  'should submit %s',
  async ([action, endpointMock], {worker}) => {
    // ...
  },
);
```

Do not declare tests inside nested loops. Build a case table first:

```tsx
const languages = ['en', 'de'] as const;
const actions = ['delete', 'cancel'] as const;
const counts = [1, 3] as const;

const cases = languages.flatMap((language) =>
  actions.flatMap((action) =>
    counts.map((count) => ({language, action, count})),
  ),
);

it.for(cases)(
  'should render the $language $action confirmation for $count instances',
  async ({language, action, count}) => {
    // ...
  },
);
```

Keep case tables and source arrays `as const` when their values need to retain literal types.

## User interactions

All interactions use `userEvent` from `vitest/browser`. It accepts both `Element` and `Locator` (the return type of `screen.getBy*`).

`userEvent` accepts locators directly. Do not resolve a locator with `.element()` before passing it
to `userEvent`.

```tsx
// Correct
await userEvent.click(screen.getByRole('button', {name: 'Save'}));
await userEvent.fill(screen.getByLabelText('Name'), 'Alice');

// Wrong
await userEvent.click(screen.getByRole('button', {name: 'Save'}).element());
```

```ts
import {userEvent} from 'vitest/browser';

// Click
await userEvent.click(screen.getByRole('button', {name: /submit/i}));

// Fill an input (clears existing value first)
await userEvent.fill(screen.getByLabelText('Name'), 'Alice');

// Type without clearing (appends to existing value)
await userEvent.type(screen.getByLabelText('Name'), ' Bob');

// Clear an input
await userEvent.clear(screen.getByLabelText('Name'));

// Select options in a <select>
await userEvent.selectOptions(screen.getByRole('combobox'), ['option-value']);

// Keyboard
await userEvent.keyboard('{Enter}');

// Tab
await userEvent.tab();

// Hover / unhover
await userEvent.hover(screen.getByText('Tooltip trigger'));
await userEvent.unhover(screen.getByText('Tooltip trigger'));
```

## Common gotchas

- **No `waitFor` or `findBy*`**: `expect.element()` handles async natively. Writing `await waitFor(() => ...)` will error — it doesn't exist in this setup.
- **No `queryByText` / `queryByRole`**: these don't exist. Use `getByText` / `getByRole` with `expect.element(...).not.toBeInTheDocument()` for absence checks.
- **`userEvent` is not a fixture — import it directly**: `import {userEvent} from 'vitest/browser';`. Use `userEvent.click()`, `userEvent.fill()`, `userEvent.type()`, etc. for all interactions. Do not use locator `.click()` or `.fill()` methods — they bypass the realistic event sequence that `userEvent` provides.
- **Endpoint mocks are functions**: always call them with a config object — `mockCurrentUserEndpoint({successResponse: HttpResponse.json({})})`, not `mockCurrentUserEndpoint` bare.
- **Locators are not DOM elements**: keep `screen.getBy*()` results as locators. Chain queries and
  assertions from them instead of using `element()`, `parentElement`, `closest`, or `querySelector`.
- **Negative child assertions need a rendered parent**: assert that the parent is visible before
  asserting that one of its descendants is absent.
- **Exact selectors include accessible content**: a role's accessible name may contain nested
  tooltip text, icons, badges, and controls. Inspect the ARIA tree and use the complete accessible
  name, an existing title, or a scoped child locator.
- **Do not change ARIA for test discovery**: adding an ARIA role or label changes what assistive
  technology announces. Only add semantics that accurately describe the interface.
- **Do not declare tests in loops**: use `it.for` for parameterized cases. Loops that perform repeated
  actions or assertions inside one test are still valid.
- **Tuple rows are not spread by `it.for`**: receive a tuple as the first callback argument and
  destructure it there.
- **`userEvent` accepts locators**: do not call `.element()` before `userEvent.click`,
  `userEvent.fill`, or other interactions.
- **`msw/browser`, not `msw/node`**: the MSW worker runs in the browser via `setupWorker`. If you see imports from `msw/node`, that's wrong.
- **No `vi.mock()` for HTTP**: it silently breaks in browser mode and is the wrong abstraction anyway. Use MSW.
- **`render()` returns `screen`**: unlike Testing Library where `screen` is a global import, here `render()` returns the screen object. Use `const screen = await render(<Comp />)`. Same for `renderWithRouter()` — `const screen = await renderWithRouter(MyPage, {path: '/my-path'})`.

## Commands

Run focused and full validation from
`webapp/client/apps/orchestration-cluster-webapp/`:

```bash
npm run test:unit -- --run src/path/to/example.test.tsx
npm run typecheck
npm run test:unit -- --run
```

Run formatting and linting from `webapp/client/`:

```bash
npm run prettier:format
npm run lint
```

Use `npm run test:unit:ui` for interactive debugging. Never invoke Prettier or `tsc` directly.

## Template references

- `src/shared/pages/LoginPage.test.tsx` — page-level test using `renderWithRouter`.
- `src/operate/shared/TenantField/TenantField.test.tsx` — standalone component test using a shared
  endpoint mock.
- `src/shared/mock-test.test.tsx` — component test with MSW mocking.
- `src/vitest-modules/test-extend.ts` — custom `it` fixture source.
- `src/vitest-modules/render-with-router.tsx` — `renderWithRouter` utility source.
- `shared-test-modules/mock-endpoint.ts` — `createEndpointMock` factory source.
- `shared-test-modules/mock-handlers.ts` — shared endpoint mock definitions.
- `docs/monorepo-docs/frontend/testing.md` — full testing guide.
