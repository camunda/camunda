# Optimize E2E tests

Playwright tests against a real self-managed stack (Camunda, Keycloak, Identity, Elasticsearch,
Optimize). Read this before adding or fixing a test.

## Run

```bash
yarn start-backend   # stack + Optimize backend (separate terminal)
yarn e2e             # seeds data if needed, then runs the `e2e` and `visual` projects
yarn e2e --project=e2e tests/report.spec.ts -g "incident"   # narrow down
yarn e2e:lint        # must pass before pushing
```

Env overrides (defaults match `docker-compose.yml`): `E2E_OPTIMIZE_URL`, `E2E_CAMUNDA_URL`,
`E2E_DATABASE_URL`, `E2E_CAMUNDA_USER`, `E2E_CAMUNDA_PASSWORD`.

## Layout

| Path | Purpose |
|------|---------|
| `seed/dataset.ts` | The only source of truth for seeded data. Derive every expected number from it (`countOrders(...)`). |
| `seed/seed.ts`, `seed/resources/` | Deploys the BPMN models and drives the instances through the Camunda REST API. Idempotent. |
| `setup/seed.setup.ts` | `setup` project: seeds and waits until Optimize imported exactly the dataset. |
| `fixtures.ts` | `test`/`expect` with page objects, `api`, `collection`, `uniqueName`, `johnPage`, `anonymousPage`, `deleteAfterTest`. |
| `api/optimizeApi.ts` | Arranges preconditions (collections, reports, dashboards, alerts) through Optimize's API. |
| `pages/` | Page objects: locators and user actions. They may wait for an action to finish; outcomes are asserted in specs. |
| `tests/` | Functional specs. `visual/` holds screenshot tests, `cloud/` the SaaS smoke test. |

## Rules

- Import `test` and `expect` from `../fixtures`, never from `@playwright/test` (lint enforced).
- Every test is isolated: use the `collection` fixture (deleted after the test) and `uniqueName()`.
  Register anything created through the UI outside of it with `deleteAfterTest`.
- Arrange through `api`, act and assert through the UI. Only drive the feature under test in the UI.
- Locators: `getByRole` > `getByLabel` > `getByText` > `getByTestId`. CSS/XPath only inside `pages/`.
  If an element has no accessible handle, add an `aria-label` (or, last resort, `data-testid`) in `src/`.
- Assert with web-first assertions (`await expect(locator).toHaveText(...)`). Never read state and
  compare it yourself.
- Forbidden (lint enforced): `waitForTimeout`, `{force: true}`, `networkidle`, `test.skip`/`.only`,
  conditionals in tests, element handles.
- Retries are configured for CI only. Never add in-test retries, sleeps or longer timeouts to make a
  test pass, and never loosen an assertion. Find the race and wait for the real signal (a response,
  a rendered element), as done in `ReportPage.choose` and `CollectionPage.addUser`.

## Fixing a failing test

1. Open the trace: `npx playwright show-trace e2e/test-results/<test>/trace.zip` (CI uploads
   `optimize-e2e-report`). `error-context.md` next to it contains the page's accessibility tree.
2. A changed UI label or structure: fix the locator in the page object, not in the spec.
3. A changed number: check `seed/dataset.ts` and the product behaviour before touching the assertion.
4. Visual diffs: baselines are rendered on the Linux CI runner. Re-render them by dispatching the
   "Optimize E2E" workflow with `update-snapshots` and commit the uploaded artifact to
   `visual/__screenshots__/`. Local runs skip the comparison.
