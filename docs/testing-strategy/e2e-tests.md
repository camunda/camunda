# End-to-End Tests

> Back to [Testing Strategy](./README.md)

## Definition

An E2E test exercises the full system through its external interfaces (UI or API) with all real dependencies running. These are the most expensive tests and should be used sparingly.

## Rules

1. **E2E tests do NOT block PRs** — they run nightly and on release branches only
2. **Use Playwright** for all browser-based E2E tests
3. **Use the Page Object Model** — all page interactions go through page objects, never raw selectors in test files
4. **Never use `waitForTimeout()`** — use assertion-based waiting (`expect(locator).toBeVisible()`, `expect.poll()`)
5. **Never use `actionTimeout: 0`** — always set a bounded action timeout (10 seconds)
6. **Use accessible selectors** — `getByRole()`, `getByLabel()`, `getByText()` over `getByTestId()` over CSS selectors
7. **Retries are not a substitute for reliability** — `retries: 2` in Playwright config is acceptable for CI resilience, but a test that needs retries to pass should be investigated
8. **Use `test.step()`** for multi-step tests — provides clear failure attribution

## Gold Standard Example

From `operate/client/e2e-playwright/tests/login.spec.ts`:

```typescript
test.describe('login page', () => {
  test('Log in with invalid user account', async ({loginPage, page}) => {
    await loginPage.login({username: 'demo', password: 'wrong-password'});
    await expect(
      page.getByRole('alert').getByText('Username and password do not match'),
    ).toBeVisible();
    await expect(page).toHaveURL('/operate/login');
  });

  test('Log in with valid user account', async ({loginPage, page}) => {
    await loginPage.login({username: 'demo', password: 'demo'});
    await expect(page).toHaveURL('../operate');
  });
});
```

**Why this is the standard:**
- No `waitForTimeout` — all waits are assertion-based
- Page Object Model via fixtures (`loginPage`)
- Accessible selectors (`getByRole('alert')`)
- Short, focused — one scenario per test
- No hardcoded URLs or magic strings

## Page Object Model

Every page or component that tests interact with must have a corresponding page object class:

```typescript
// pages/Login.ts
export class Login {
  private page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.getByLabel(/^username$/i);
    this.passwordInput = page.getByLabel(/^password$/i);
    this.loginButton = page.getByRole('button', {name: 'Login'});
  }

  async login(credentials: {username: string; password: string}) {
    await this.fillUsername(credentials.username);
    await this.fillPassword(credentials.password);
    await this.clickLoginButton();
  }
}
```

Reference: `operate/client/e2e-playwright/pages/Login.ts`

## Eventual Consistency in E2E Tests

Use `expect.poll()` or `expect().toPass()` for assertions that depend on asynchronous data:

```typescript
// CORRECT: polling with timeout
await expect.poll(async () => {
  const response = await request.get(`${baseUrl}/v1/process-instances/${key}`);
  return response.status();
}, {timeout: 30_000}).toBe(200);

// CORRECT: retry assertion block
await expect(async () => {
  const res = await request.get(buildUrl(`/process-instances/${key}`));
  await assertStatusCode(res, 200);
  await validateResponse({path: '/process-instances/{key}', method: 'GET', status: '200'}, res);
}).toPass(defaultAssertionOptions);

// WRONG: hardcoded wait
await page.waitForTimeout(5000);  // NEVER DO THIS
```

Reference: `operate/client/e2e-playwright/tests/processInstance.spec.ts`
