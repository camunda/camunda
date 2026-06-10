---

name: frontend-integration-test
description: Use when writing, modifying, or debugging Playwright-based tests in the orchestration cluster webapp — integration tests, visual regression tests, or accessibility tests. Use when working with MSW network-level mocking via @msw/playwright, Page Object Models, axe-core accessibility checks, or screenshot comparisons. Trigger whenever someone is working in the test/ directory of the OC webapp at webapp/client/apps/orchestration-cluster-webapp/test/.

---

# Frontend Integration, Visual, and Accessibility Testing

Playwright tests in `@camunda/orchestration-cluster-webapp` cover three categories — integration, visual regression, and accessibility — across 7 Playwright projects. Tests run against the built app served by `vite preview` on port 3003. MSW intercepts HTTP at the network level via `@msw/playwright`, so no real backend is needed.

## Key rules

- Import `test` and `expect` from `#/pw-modules/test-extend`, not from `@playwright/test`. The custom `test` fixture auto-starts MSW network interception per test and provides the `makeAxeBuilder` fixture for accessibility checks.
- Use endpoint mocks from `#/shared-test-modules/mock-handlers` with `network.use()`. Each mock is an individually named export (e.g., `mockCurrentUserEndpoint`, `mockLoginEndpoint`) created with `createEndpointMock` — both unit and Playwright tests use the same definitions. All endpoint mocks must be defined in `apps/orchestration-cluster-webapp/shared-test-modules/mock-handlers.ts` — never create `createEndpointMock` calls inline in test files.
- Prefer testing library selectors: `page.getByRole()`, `page.getByLabel()`, `page.getByText()`. Playwright includes these out of the box. They enforce accessible markup and survive structural changes. Avoid `page.locator('.css-class')` and `page.getByTestId()`.
- Place tests in the correct category directory: `test/integration/` for MSW-mocked user flows, `test/visual/` for screenshot comparisons, `test/a11y/` for accessibility checks.
- Use Page Object Model for page interactions — one class per page under `test/pages/`. Encapsulate navigation, locators, and composite actions (e.g., `fillCredentials(username, password)`) so tests read as user stories, not DOM queries. Page objects are registered as Playwright fixtures in `test/pw-modules/test-extend.ts` and destructured from the test parameters — **NEVER import page object classes in test files**. Test files must not contain any `import {LoginPage}` or `import {SomePage}` statements.

```ts
// WRONG — never do this in a test file
import {LoginPage} from '../pages/Login.page';
const loginPage = new LoginPage(page);

// CORRECT — destructure from test parameters
test('should ...', async ({loginPage, network, page}) => {
  await loginPage.goto();
});
```

- Visual tests require a containerized browser for consistent cross-machine rendering (`CONTAINERIZED_BROWSER=true` runs the official `mcr.microsoft.com/playwright` Docker image).
- Accessibility tests check both light and dark themes automatically — the Playwright config runs a11y tests through `a11y-light` and `a11y-dark` projects.

## Test categories

### Integration tests (`test/integration/`)

Test user flows across pages and components: navigation, data loading, error states, multi-step interactions. Mock the backend with MSW via the `network` fixture. Use page objects from fixtures — never import and instantiate them manually.

```ts
import {test, expect} from '#/pw-modules/test-extend';
import {mockCurrentUserEndpoint, mockLoginEndpoint} from '#/shared-test-modules/mock-handlers';
import {HttpResponse} from 'msw';

test('should redirect to the initial page on success', async ({network, page, loginPage}) => {
  network.use(
    mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
    mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 200})}),
  );

  await loginPage.goto();

  network.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json({})}));

  await loginPage.fillCredentials('demo', 'demo');
  await loginPage.submitButton.click();

  await expect(page).toHaveURL('/');
});
```

### Visual regression tests (`test/visual/`)

Screenshot comparison via `expect(page).toHaveScreenshot()`. Four projects cover light/dark themes and desktop/tablet viewports. Always use the containerized browser for deterministic rendering.

```ts
import {test, expect} from '#/pw-modules/test-extend';

test('should match snapshot', async ({page}) => {
  await page.goto('/some-page');
  await expect(page).toHaveScreenshot('some-page.png', {fullPage: true});
});
```

### Accessibility tests (`test/a11y/`)

Playwright + `@axe-core/playwright`. Use the `makeAxeBuilder` fixture and assert zero violations. The Playwright config runs every a11y test in both light and dark themes automatically.

```ts
import {test, expect} from '#/pw-modules/test-extend';

test('should have no a11y violations', async ({makeAxeBuilder, page}) => {
  await page.goto('/some-page');
  const results = await makeAxeBuilder().analyze();
  expect(results.violations).toEqual([]);
});
```

## Fixtures

The custom `test` from `#/pw-modules/test-extend` provides these fixtures:

|     Fixture      |    Auto     |                                                                Description                                                                |
|------------------|-------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `network`        | yes         | MSW interception via `@msw/playwright`. Starts before each test, stops after. Use `network.use()` to add handlers.                        |
| `handlers`       | no (option) | Pre-configure MSW handlers at suite level via `test.use({handlers: [...]})`. Useful when every test in a file shares the same mock setup. |
| `makeAxeBuilder` | no          | Creates an `AxeBuilder` instance scoped to the current page. Call `makeAxeBuilder().analyze()` to run the audit.                          |
| `loginPage`      | no          | Page object for the login page. Every page object follows this pattern — registered as a fixture, destructured in tests.                  |

The `network` fixture errors on unhandled requests (except HTML page navigations), so tests fail fast if they hit an un-mocked endpoint. This is intentional — it catches missing mocks early.

Endpoint mocks are functions — always call them with a config object containing `successResponse`:

```ts
// Correct
mockCurrentUserEndpoint({successResponse: HttpResponse.json({})})
mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 200})})

// Wrong — bare reference, dot-chained, or wrong key names
mockCurrentUserEndpoint
mockLoginEndpoint.success()
mockLoginEndpoint({serverResponse: ...})  // wrong key
```

`network.use()` is synchronous and takes handlers as spread arguments, not an array:

```ts
// Correct
network.use(
  mockCurrentUserEndpoint({successResponse: HttpResponse.json({})}),
  mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 200})}),
);

// Wrong
await network.use([...]);
```

Do not use `// given / when / then` comments in frontend tests — that is a Java backend convention. Structure tests by visual grouping instead.

## Page Object Model

Encapsulate page interactions in classes under `test/pages/`. Every page object must have a `goto()` method for navigation. Use getter-based locators and composite actions (multi-step user operations like filling a form). Tests navigate via `loginPage.goto()`, never via `page.goto('/login')` directly.

```ts
// test/pages/Login.page.ts
import {type Page} from '@playwright/test';

class LoginPage {
  private page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/login');
  }

  get usernameInput() {
    return this.page.getByLabel(/username/i);
  }

  get passwordInput() {
    return this.page.getByLabel(/^password$/i);
  }

  get submitButton() {
    return this.page.getByRole('button', {name: /login/i});
  }

  get errorMessage() {
    return this.page.getByRole('alert').filter({hasText: /.+/});
  }

  async fillCredentials(username: string, password: string) {
    await this.usernameInput.fill(username);
    await this.passwordInput.fill(password);
  }
}

export {LoginPage};
```

### Registering page objects as fixtures

Page objects are never imported directly in test files. Register them as Playwright fixtures in `test/pw-modules/test-extend.ts`:

```ts
// test/pw-modules/test-extend.ts (excerpt)
import {LoginPage} from '#/pages/Login.page';

type Fixtures = {
  // ...existing fixtures...
  loginPage: LoginPage;
};

const test = base.extend<Fixtures>({
  // ...existing fixtures...
  loginPage: async ({page}, use) => {
    await use(new LoginPage(page));
  },
});
```

Usage in tests — destructure from the test parameters:

```ts
import {test, expect} from '#/pw-modules/test-extend';
import {mockCurrentUserEndpoint, mockLoginEndpoint} from '#/shared-test-modules/mock-handlers';
import {HttpResponse} from 'msw';

test('should show an error for wrong credentials', async ({network, loginPage}) => {
  network.use(
    mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
    mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
  );

  await loginPage.goto();
  await loginPage.fillCredentials('demo', 'wrong-password');
  await loginPage.submitButton.click();

  await expect(loginPage.errorMessage).toContainText(/username and password do not match/i);
});
```

## Playwright config overview

The config at `playwright.config.ts` defines 7 projects:

|        Project        |  Category   | Theme | Viewport |
|-----------------------|-------------|-------|----------|
| `visual-light`        | visual      | light | desktop  |
| `visual-dark`         | visual      | dark  | desktop  |
| `visual-light-tablet` | visual      | light | tablet   |
| `visual-dark-tablet`  | visual      | dark  | tablet   |
| `a11y-light`          | a11y        | light | desktop  |
| `a11y-dark`           | a11y        | dark  | desktop  |
| `integration`         | integration | —     | desktop  |

Tests match by directory: `visual/**/*.test.ts`, `a11y/**/*.test.ts`, `integration/**/*.test.ts`. The app is served via `npx vite preview` on port 3003 (build must exist first). Retries are 2x on CI, traces and screenshots are captured on failure.

## Commands

Run from `webapp/client/apps/orchestration-cluster-webapp/`:

```bash
npm run test:integration    # Integration tests (MSW-mocked flows)
npm run test:visual         # Visual regression (requires Docker for containerized browser)
npm run test:a11y           # Accessibility (light + dark themes)
```

Format changed files via `npm run prettier:format` from `webapp/client/` — never invoke Prettier directly.

## Template references

- `test/integration/about.test.ts` — integration test with MSW.
- `test/visual/login.test.ts` — visual regression test.
- `test/a11y/about.test.ts` — accessibility test.
- `test/pages/Login.page.ts` — Page Object Model.
- `test/pw-modules/test-extend.ts` — custom `test` fixture source.
- `shared-test-modules/mock-endpoint.ts` — `createEndpointMock` factory source.
- `shared-test-modules/mock-handlers.ts` — shared endpoint mock definitions.
- `docs/monorepo-docs/frontend/testing.md` — full testing guide.

