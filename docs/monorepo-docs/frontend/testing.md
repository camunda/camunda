# Testing

Testing in `@camunda/orchestration-cluster-webapp`. For the scripts
that run each suite, see the
[Scripts table](./orchestration-cluster-webapp.md#scripts).

## Stack

| Type              | Tool                                 | Location              | Script                     |
| ----------------- | ------------------------------------ | --------------------- | -------------------------- |
| Unit              | Vitest browser mode (Playwright)     | `src/**/*.test.ts(x)` | `npm run test:unit`        |
| Integration       | Playwright + MSW (`@msw/playwright`) | `test/integration/`   | `npm run test:integration` |
| Accessibility     | Playwright + `@axe-core/playwright`  | `test/a11y/`          | `npm run test:a11y`        |
| Visual regression | Playwright + containerized browser   | `test/visual/`        | `npm run test:visual`      |

## Mocking the backend

[MSW](https://mswjs.io/) is the only backend mock layer. Two
entrypoints depending on the test type:

- **Unit tests**: MSW `setupWorker` (browser mode), auto-started by
  the custom `it` fixture from `#/vitest-modules/test-extend`.
- **Playwright tests**: MSW via `@msw/playwright`, auto-started by
  the `network` fixture from `#/pw-modules/test-extend`.

Both use `createEndpointMock` from
`#/shared-test-modules/mock-endpoint` to build typed request handlers.
All endpoint mocks are defined in `#/shared-test-modules/endpoints` as
a shared dictionary, so every test (unit and Playwright) reuses the
same mock definitions.

Unit test example:

```ts
import { it } from "#/vitest-modules/test-extend";
import { endpoints } from "#/shared-test-modules/endpoints";
import { HttpResponse } from "msw";

it("should render users", async ({ worker }) => {
  worker.use(
    endpoints.users({
      successResponse: HttpResponse.json([{ name: "Alice" }]),
    }),
  );
  // render and assert...
});
```

Playwright test example:

```ts
import { test, expect } from "#/pw-modules/test-extend";
import { endpoints } from "#/shared-test-modules/endpoints";
import { HttpResponse } from "msw";

test("should render users", async ({ network, page }) => {
  network.use(
    endpoints.users({
      successResponse: HttpResponse.json([{ name: "Alice" }]),
    }),
  );
  await page.goto("/users");
  await expect(page.getByText("Alice")).toBeVisible();
});
```

## Prefer testing library selectors

Both Vitest browser mode and Playwright include testing library
selectors out of the box. Use `getByRole`, `getByLabelText`, and
`getByText` over raw DOM queries like `querySelector` or `getByTestId`.
They enforce accessible markup and make tests resilient to structural
changes.

```ts
// good
screen.getByRole("button", { name: /submit/i });
screen.getByLabelText(/username/i);

// avoid
screen.querySelector("button.submit-btn");
screen.getByTestId("submit-button");
```

## Avoid vitest mocks

Prefer MSW and real implementations over `vi.mock` and
module mocks. Vitest mocks couple tests to implementation details and
break on refactors. Reach for them only when there is no practical
alternative, such as faking time with `vi.useFakeTimers`.

## Unit vs. integration tests

### Unit tests

Test a single component, hook, or utility in isolation. Render with
`vitest-browser-react` `render()`. Use MSW to mock HTTP when the
component fetches data.

Do not mock the router. The only exception is when the component uses
`<Link>`, `useNavigate`, or another router hook that fails without a
provider. In that case wrap in a minimal router provider, nothing more.

### Integration tests

Test a feature across UI sections and pages: navigation, data loading,
error states, user flows that span multiple components. Run against the
built app via Playwright. Always mock the backend with MSW via the
`network` fixture.

## Accessibility tests

Playwright + `@axe-core/playwright`. Two Playwright projects run every
a11y test in both light and dark themes. Use the `makeAxeBuilder`
fixture and assert that `violations` is empty.

```ts
import { test, expect } from "#/pw-modules/test-extend";

test("should have no a11y violations", async ({ makeAxeBuilder, page }) => {
  await page.goto("/some-page");
  const results = await makeAxeBuilder().analyze();
  expect(results.violations).toEqual([]);
});
```

## Visual regression tests

Playwright `toHaveScreenshot`. Four projects cover light/dark and
desktop/tablet combinations. Set `CONTAINERIZED_BROWSER=true` to run
inside the official `mcr.microsoft.com/playwright` Docker image for
stable cross-machine rendering.

```ts
import { test, expect } from "#/pw-modules/test-extend";

test("should match snapshot", async ({ page }) => {
  await page.goto("/some-page");
  await expect(page).toHaveScreenshot("some-page.png", { fullPage: true });
});
```
