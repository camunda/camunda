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
| Docs screenshots  | Playwright + containerized browser   | `test/docs-screenshots/` | `npm run test:docs-screenshots` |

## Mocking the backend

[MSW](https://mswjs.io/) is the only backend mock layer. Two
entrypoints depending on the test type:

- **Unit tests**: MSW `setupWorker` (browser mode), auto-started by
  the custom `it` fixture from `#/vitest-modules/test-extend`.
- **Playwright tests**: MSW via `@msw/playwright`, auto-started by
  the `network` fixture from `#/pw-modules/test-extend`.

Both use `createEndpointMock` from
`#/shared-test-modules/mock-endpoint` to build typed request handlers.
All endpoint mocks are individually named exports from
`#/shared-test-modules/mock-handlers`, so every test (unit and Playwright)
reuses the same mock definitions. Reusable response factories live under
`#/shared-test-modules/api-mocks/`.

Unit test example:

```ts
import {HttpResponse} from 'msw';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';
import {it} from '#/vitest-modules/test-extend';

it('should render the current user', async ({worker}) => {
  worker.use(
    mockCurrentUserEndpoint({
      successResponse: HttpResponse.json(createCurrentUser()),
    }),
  );
  // render and assert...
});
```

Playwright test example:

```ts
import {HttpResponse} from 'msw';
import {test} from '#/pw-modules/test-extend';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';

test.beforeEach(({network}) => {
  network.use(
    mockCurrentUserEndpoint({
      successResponse: HttpResponse.json(createCurrentUser()),
    }),
  );
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

## Docs screenshots

Generate the images used on docs.camunda.io pages. Unlike visual
regression tests, they don't compare anything: they write PNGs with
`page.screenshot({path})`. There is no CI job — run
`npm run test:docs-screenshots` manually when a docs page needs fresh
images. The script runs the browser in the containerized Playwright
image (Docker required), with the light theme and a Full HD viewport.

- One `test/docs-screenshots/<app>/<docs-page-slug>.test.ts` per docs
  page, starting with a JSDoc that links the page. Its images go to the
  sibling `<docs-page-slug>/` folder, named as the docs page references
  them.
- Resolve output paths with `getScreenshotPath` from
  `test/docs-screenshots/screenshot-path.ts`.
- Use `test/docs-screenshots/callouts.ts` for labelled callouts,
  zooming, and cropping.
- Keep output stable: fix the clock with `page.clock.setFixedTime()`, use
  fixed mock data, and seed localStorage flags so first-time popups
  don't appear.

For the full procedure and the helper reference, see the
`frontend-docs-screenshots` skill in `.agents/skills/`.

```ts
import { test } from "#/pw-modules/test-extend";
import { getScreenshotPath } from "../screenshot-path";

test("some-page", async ({ page, tasklistIndexPage }) => {
  await page.clock.setFixedTime(new Date("2024-09-05T13:32:00.000Z"));
  await tasklistIndexPage.goto();

  await page.screenshot({
    path: getScreenshotPath({
      baseUrl: import.meta.url,
      folder: "some-page",
      fileName: "some-image.png",
    }),
  });
});
```
