---
name: frontend-unit-test
description: Use when writing, modifying, or debugging unit tests (*.test.tsx) in the orchestration cluster webapp (webapp/client/apps/orchestration-cluster-webapp/src/). Covers Vitest browser mode, MSW mocking, and vitest-browser-react rendering.
---

# Frontend Unit Testing

Unit tests in `@camunda/orchestration-cluster-webapp` run in real Chromium with Vitest Browser Mode.
They do not run in jsdom or happy-dom.

Use the browser DOM, browser events, and browser APIs. Use MSW to intercept HTTP requests through a
service worker.

## Required setup

- Import `it` from `#/vitest-modules/test-extend`.
- Import other test APIs, such as `describe`, `expect`, and `vi`, from `vitest`.
- Import `userEvent` from `vitest/browser`.
- Import `render` from `vitest-browser-react`.
- Do not import `screen` from `@testing-library/react`.
- Do not use `@testing-library/react`.
- Do not use `vi.mock()` for HTTP requests.

The custom `it` fixture starts MSW before each test. It also resets and stops MSW after each test.
Import this `it` even when the test does not use the `worker` argument.

## Rendering

Use `render()` for components that do not need a router.

```tsx
const screen = await render(<Component />);
```

Use `renderWithRouter()` for pages and components that use links, navigation, route parameters, or
route search parameters.

```tsx
const screen = await renderWithRouter(Page, {
  path: '/users/$id',
  initialEntry: '/users/42',
});
```

Import it from:

```tsx
import {renderWithRouter} from '#/vitest-modules/render-with-router';
```

`renderWithRouter()` creates an isolated TanStack Router with memory history and a fresh
`QueryClient`. It does not load the full route tree or run route `beforeLoad` functions.

Do not mock the router.

Typed file-route hooks, such as `Route.useParams()`, do not resolve in this isolated router. Use:

```tsx
useParams({from: '/users/$id'});
```

Both render functions return `screen`. Always capture it.

## Locators

`screen.getBy*()` returns a lazy, retryable `Locator`. It does not return a DOM element.

Use selectors in this order:

| Need | Selector |
|---|---|
| Interactive element or semantic element | `getByRole` |
| Form control | `getByLabelText` |
| Independent static text | `getByText` |
| Existing `title` attribute | `getByTitle` |
| No suitable user-facing selector | `getByTestId` |

String locators use exact matching in this application. Match the complete, case-sensitive text,
accessible name, label, or title.

A regular expression controls its own matching behavior.

The `name` option of `getByRole` matches the accessible name. The accessible name can include nested
controls, icons, tooltips, badges, or hidden accessible text.

Do not change production semantics only to make a test pass. Do not add an ARIA role, `aria-label`,
`title`, hidden text, wrapper, or test ID only for test discovery. Add ARIA only when it accurately
describes the interface.

### Compose locators

Keep locators unresolved. This preserves strict matching, retries, and useful diagnostics.

Chain a selector from its parent:

```tsx
const betaProcessLink = screen.getByTitle('Beta Process – 3 Instances in 1 Version');
const alphaProcessLink = screen.getByTitle('Alpha Process – 6 Instances in 1 Version');

await expect.element(betaProcessLink).toBeVisible();
await expect.element(alphaProcessLink).toBeVisible();

await expect.element(betaProcessLink.getByTestId('draining-indicator')).toBeVisible();
await expect.element(alphaProcessLink.getByTestId('draining-indicator')).not.toBeInTheDocument();
```

Assert that a parent is visible before you assert that a child is absent. This prevents a false pass
when the parent does not render.

Use `.filter()` to narrow a collection:

```tsx
const invoiceRow = screen
  .getByRole('row')
  .filter({has: screen.getByRole('link', {name: 'Invoice Process'})});

await expect.element(invoiceRow.getByRole('button', {name: 'Delete'})).toBeEnabled();
```

Available filters are:

- `has`
- `hasNot`
- `hasText`
- `hasNotText`

Use semantic filters before you use `.first()`, `.last()`, or `.nth()`.

Do not unwrap locators for normal assertions:

```tsx
// Wrong
const row = screen.getByText('Invoice Process').element().closest('a') as HTMLElement;
const indicator = row.querySelector('[data-testid="draining-indicator"]') as HTMLElement;

// Correct
const processLink = screen.getByTitle('Invoice Process – 3 Instances in 1 Version');

await expect.element(processLink.getByTestId('draining-indicator')).toBeVisible();
```

Use `element()`, `query()`, `elements()`, or `findElement()` only when an external library or browser
API requires a raw DOM element.

Do not use `parentElement`, `closest`, `querySelector`, or a type cast to replace Locator composition.

## Assertions

Use `expect.element()` for DOM assertions. It resolves the Locator and retries until the assertion
passes or times out.

```tsx
// Visible
await expect.element(screen.getByRole('button', {name: 'Submit'})).toBeVisible();

// Complete normalized text
await expect.element(screen.getByRole('heading')).toHaveTextContent('Dashboard');

// Partial text
await expect
  .element(screen.getByRole('dialog'))
  .toMatchTextContent('This operation is part of a batch.');

// Regular-expression text
await expect.element(screen.getByRole('tooltip')).toMatchTextContent(/created on/i);

// Attribute
await expect
  .element(screen.getByRole('link', {name: 'Documentation'}))
  .toHaveAttribute('href', '/docs');

// Not present
await expect.element(screen.getByText('Loading...')).not.toBeInTheDocument();

// Present but hidden
await expect.element(screen.getByRole('dialog')).not.toBeVisible();
```

Use `toHaveTextContent` when the complete normalized text is the contract.

Use `toMatchTextContent` for a substring or regular-expression match.

Use `not.toBeInTheDocument()` when an element must not exist.

Use `not.toBeVisible()` when an element must remain mounted but hidden.

Do not use `waitFor`, `findBy*`, `queryByText`, or `queryByRole`.

## User interactions

Use `userEvent` for all user interactions.

```tsx
import {userEvent} from 'vitest/browser';

await userEvent.click(screen.getByRole('button', {name: 'Submit'}));
await userEvent.fill(screen.getByLabelText('Name'), 'Alice');
await userEvent.type(screen.getByLabelText('Name'), ' Bob');
await userEvent.clear(screen.getByLabelText('Name'));
await userEvent.selectOptions(screen.getByRole('combobox'), ['option-value']);
await userEvent.keyboard('{Enter}');
await userEvent.tab();
await userEvent.hover(screen.getByText('Tooltip trigger'));
await userEvent.unhover(screen.getByText('Tooltip trigger'));
```

`userEvent` accepts Locators. Do not resolve a Locator before an interaction.

```tsx
// Correct
await userEvent.click(screen.getByRole('button', {name: 'Save'}));

// Wrong
await userEvent.click(screen.getByRole('button', {name: 'Save'}).element());
```

Do not use Locator `.click()` or `.fill()` methods. Use `userEvent`.

Await every interaction.

## Parameterized tests

Use `it.for` when a loop would declare multiple tests.

Do not declare `it()` inside `for`, `for...of`, or `forEach`.

Ordinary loops inside one test remain valid.

The extended `it` passes case data as the first callback argument. It passes fixture context as the
second callback argument.

### Scalar cases

Use `%s` in the title.

```tsx
it.for(['include', 'exclude'] as const)(
  'should submit in %s mode',
  async (mode, {worker}) => {
    // ...
  },
);
```

### Object cases

Use `$property` in the title.

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

### Tuple cases

`it.for` passes a tuple as one value. Destructure the tuple in the first callback argument.

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

### Cartesian products

Build the case table before you call `it.for`.

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

## HTTP mocking

Mock HTTP through the `worker` fixture.

```tsx
it('should display the current user', async ({worker}) => {
  worker.use(
    mockCurrentUserEndpoint({
      successResponse: HttpResponse.json(createCurrentUser()),
    }),
  );

  const screen = await render(<UserDetails />);

  await expect.element(screen.getByText('Demo User')).toBeVisible();
});
```

Import endpoint mocks from:

```tsx
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';
```

Define all endpoint mocks in:

```text
shared-test-modules/mock-handlers.ts
```

Do not create `createEndpointMock` calls in test files.

Call every endpoint mock with a configuration object.

```tsx
// Correct
mockCurrentUserEndpoint({
  successResponse: HttpResponse.json(createCurrentUser()),
});

// Wrong
mockCurrentUserEndpoint;
```

Use these endpoint mock forms:

- With request validation: provide `schema`, `successResponse`, and `failureResponse`.
- Without request validation: provide `successResponse`.

Use `msw/browser`, not `msw/node`.

Do not call `worker.start()`, `worker.stop()`, or `worker.resetHandlers()` in a test. The custom
fixture owns the worker lifecycle.

Do not use `vi.mock()` for HTTP. For other dependencies, prefer real implementations. Mock only an
external or browser boundary that cannot be used directly.

## Test structure

Co-locate each test with its source file.

Place setup and teardown hooks inside the `describe` block that owns their tests. If hooks apply to multiple suites, nest those suites under a parent `describe` and put the shared hooks there rather than at the file top level.

Name tests with the `should` prefix.

```tsx
it('should display an error for invalid credentials', async () => {
  // ...
});
```

Do not use `// given`, `// when`, or `// then` comments. Use blank lines to separate setup, action,
and assertion code.

## Commands

Run these commands from `webapp/client/apps/orchestration-cluster-webapp/`:

```bash
# Focused test
npm run test:unit -- --run src/path/to/example.test.tsx

# Type check
npm run typecheck

# Full unit-test suite
npm run test:unit -- --run

# Interactive debugging
npm run test:unit:ui
```

Run these commands from `webapp/client/`:

```bash
npm run prettier:format
npm run lint
```

Do not invoke Prettier or `tsc` directly.
