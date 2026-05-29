---

name: frontend-unit-test
description: Use when writing, modifying, or debugging unit tests in the orchestration cluster webapp at webapp/client/apps/orchestration-cluster-webapp/. Use when working with Vitest browser mode, MSW mocking, vitest-browser-react rendering, or any *.test.tsx file in the OC webapp's src/ directory. Trigger whenever someone needs to create, fix, or understand a frontend unit test in webapp/client/.

---

# Frontend Unit Testing

Unit tests in `@camunda/orchestration-cluster-webapp` run in a **real Chromium browser** via Vitest Browser Mode — not jsdom or happy-dom. This matters because components render in an actual DOM with real layout, events, and browser APIs. MSW intercepts all HTTP at the service worker level.

This is fundamentally different from the `@testing-library/react` + `vi.mock()` approach used in the legacy Operate and Tasklist frontends. If you're familiar with those patterns, read the key rules carefully — several habits from the legacy apps will produce broken or flaky tests here.

## Key rules

- Import `it` from `#/vitest-modules/test-extend`, not from `vitest`. The custom fixture auto-starts an MSW service worker before each test and resets/stops it after. Importing from `vitest` directly means no MSW interception — HTTP calls will fail or hit real endpoints.
- Use `render()` from `vitest-browser-react`, not from `@testing-library/react`. The vitest-browser-react renderer is designed for browser-mode Vitest and handles the real DOM lifecycle correctly. Do NOT import `screen` from `@testing-library/react` — it does not exist in this setup.
- For components that need routing context (pages, components using `<Link>`, `useNavigate`, route hooks), use `renderWithRouter(initialLocation)` from `#/vitest-modules/render-with-router` instead of bare `render()`. It creates a real TanStack Router with a memory history — no mocking needed.
- Both `render()` and `renderWithRouter()` return the `screen` object — you MUST capture the return value: `const screen = await renderWithRouter('/login')`. There is no global `screen` import.
- Use `expect.element()` for DOM assertions — it retries automatically until the assertion passes or times out. This replaces the `waitFor` / `findBy*` / `screen.findByRole` patterns you may know from Testing Library. There is no `waitFor` here.
- Mock HTTP through the `worker` fixture using endpoint mocks from `#/shared-test-modules/mock-handlers`. Each mock is an individually named export (e.g., `mockCurrentUserEndpoint`, `mockLoginEndpoint`) created with `createEndpointMock` from `#/shared-test-modules/mock-endpoint`. Both unit and Playwright tests use the same definitions. All endpoint mocks must be defined in `apps/orchestration-cluster-webapp/shared-test-modules/mock-handlers.ts` — never create `createEndpointMock` calls inline in test files. Never use `vi.mock()` for API calls; it couples tests to implementation details and breaks on refactors.
- Prefer testing library selectors: `getByRole`, `getByLabelText`, `getByText`. They enforce accessible markup and survive structural refactors. Avoid `querySelector` and `getByTestId` — they test DOM structure, not behavior.
- Co-locate test files with source: `src/modules/foo/bar.test.tsx` sits next to `bar.tsx`.
- Prefix test names with `should` (e.g., `it('should display an error on invalid credentials')`).
- Do not mock the router. Use `renderWithRouter(initialLocation)` from `#/vitest-modules/render-with-router` when the component needs routing context. It creates a real TanStack Router backed by an in-memory history — the component receives real route params, search params, and navigation.
- Avoid `vi.mock()` in general. Prefer MSW and real implementations. Vitest mocks couple tests to internals and break on refactors. Reach for them only when there is no practical alternative, such as faking time with `vi.useFakeTimers`.
- Do not use `// given / when / then` comments — that is a Java backend convention. Structure tests by visual grouping (blank lines between setup, action, and assertion).

## MSW mocking

Endpoint mocks live in `#/shared-test-modules/endpoints` as a shared dictionary. Each entry is created with `createEndpointMock` from `#/shared-test-modules/mock-endpoint`, which builds a typed MSW handler factory for a given endpoint + HTTP method.

Two shapes:

- **With Zod schema validation**: pass `schema`, `successResponse`, and `failureResponse`. The handler validates the request body against the schema and returns the failure response if it doesn't match.
- **Without validation**: pass only `successResponse`. The handler always returns the success response.

The `worker` fixture is injected by the custom `it` and auto-resets between tests — no manual `worker.resetHandlers()` needed.

## Test structure

### Testing a standalone component (no routing context)

```tsx
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {mockUsersEndpoint} from '#/shared-test-modules/mock-handlers';
import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {UserList} from './UserList';

describe('<UserList />', () => {
  it('should render users from the API', async ({worker}) => {
    worker.use(
      mockUsersEndpoint({
        successResponse: HttpResponse.json([
          {name: 'Alice'},
          {name: 'Bob'},
        ]),
      }),
    );

    const screen = await render(<UserList />);

    await expect.element(screen.getByRole('cell', {name: 'Alice'})).toBeVisible();
    await expect.element(screen.getByRole('cell', {name: 'Bob'})).toBeVisible();
  });
});
```

### Testing a page or component that needs routing context

Use `renderWithRouter(initialLocation)` — it creates a real TanStack Router with memory history, so route params, search params, and navigation all work.

```tsx
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';
import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';

describe('<Login />', () => {
  it('should not allow the form to be submitted with empty fields', async ({worker}) => {
    worker.use(
      mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
    );

    const screen = await renderWithRouter('/login');

    await screen.getByRole('button', {name: /login/i}).click();

    await expect.element(screen.getByLabelText(/username/i)).toBeInvalid();
    await expect.element(screen.getByLabelText(/^password$/i)).toBeInvalid();
  });
});
```

## Assertion patterns

```ts
// Visibility
await expect.element(screen.getByRole('button', {name: /submit/i})).toBeVisible();

// Text content
await expect.element(screen.getByRole('heading')).toHaveTextContent('Dashboard');

// Attributes
await expect.element(screen.getByRole('link', {name: 'Docs'})).toHaveAttribute('href', '/docs');

// Absence — element should not be in the document
await expect.element(screen.getByText('Loading...')).not.toBeVisible();
```

`expect.element()` is always async and retries — no need to wrap in `waitFor` or use `findBy*`.

## Common gotchas

- **No `waitFor` or `findBy*`**: `expect.element()` handles async natively. Writing `await waitFor(() => ...)` will error — it doesn't exist in this setup.
- **No `queryByText` / `queryByRole`**: these don't exist. Use `getByText` / `getByRole` with `expect.element(...).not.toBeVisible()` for absence checks.
- **No `user` fixture**: there is no `userEvent` or `user` fixture. Interact directly via locators: `screen.getByRole('button').click()`, `screen.getByLabelText('Name').fill('value')`.
- **Endpoint mocks are functions**: always call them with a config object — `mockCurrentUserEndpoint({successResponse: HttpResponse.json({})})`, not `mockCurrentUserEndpoint` bare.
- **No jsdom APIs**: tests run in a real browser, so `document.querySelector` technically works but defeats the purpose. Use `screen.getBy*` queries.
- **`msw/browser`, not `msw/node`**: the MSW worker runs in the browser via `setupWorker`. If you see imports from `msw/node`, that's wrong.
- **No `vi.mock()` for HTTP**: it silently breaks in browser mode and is the wrong abstraction anyway. Use MSW.
- **`render()` returns `screen`**: unlike Testing Library where `screen` is a global import, here `render()` returns the screen object. Use `const screen = await render(<Comp />)`. Same for `renderWithRouter()` — `const screen = await renderWithRouter('/path')`.

## Commands

Run from `webapp/client/apps/orchestration-cluster-webapp/`:

```bash
npm run test:unit       # Headless Chromium — CI and local
npm run test:unit:ui    # Visible browser — useful for debugging
```

## Template references

- `src/pages/LoginPage.test.tsx` — page-level test using `renderWithRouter`.
- `src/modules/mock-test.test.tsx` — component test with MSW mocking.
- `src/vitest-modules/test-extend.ts` — custom `it` fixture source.
- `src/vitest-modules/render-with-router.tsx` — `renderWithRouter` utility source.
- `shared-test-modules/mock-endpoint.ts` — `createEndpointMock` factory source.
- `shared-test-modules/mock-handlers.ts` — shared endpoint mock definitions.
- `docs/monorepo-docs/frontend/testing.md` — full testing guide.

