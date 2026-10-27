---
name: frontend-docs-screenshots
description: Use when you add, change, or run the Playwright tests that make the docs.camunda.io images for the orchestration cluster webapp (webapp/client/apps/orchestration-cluster-webapp/test/docs-screenshots/). Use also for the callout helpers, getScreenshotPath, and the test:docs-screenshots script.
---

# Frontend Docs Screenshots

## Purpose

These tests make the images for docs.camunda.io pages. They do not find regressions.

- They write PNG files with `page.screenshot({path})`.
- They do not use `toHaveScreenshot()`.
- There is no CI job. Run the tests manually when a docs page needs new images.

All rules in the `frontend-integration-test` skill also apply here. Read that skill for page
objects, fixtures, endpoint mocks, and selectors.

## Files and folders

All paths are relative to `webapp/client/apps/orchestration-cluster-webapp/`.

| Path                                                  | Content                                    |
|-------------------------------------------------------|--------------------------------------------|
| `test/docs-screenshots/<app>/<page-slug>.test.ts`     | The tests for one docs page                |
| `test/docs-screenshots/<app>/<page-slug>/<image>.png` | The images for that docs page              |
| `test/docs-screenshots/screenshot-path.ts`            | `getScreenshotPath` helper                 |
| `test/docs-screenshots/callouts.ts`                   | Helpers for callouts, zoom, and isolation  |
| `shared-test-modules/api-mocks/`                      | Response factories for the endpoint mocks  |
| `shared-test-modules/mock-handlers.ts`                | Endpoint mocks                             |
| `test/pages/`                                         | Page objects                               |

`<app>` is the docs area, for example `tasklist`. `<page-slug>` is the last part of the docs page
URL.

## Procedure: add the images for a docs page

1. Open the docs page. Find the names of all images on the page.
2. Make the file `test/docs-screenshots/<app>/<page-slug>.test.ts`.
3. Put a JSDoc at the start of the file. Write the link to the docs page in it.
4. In the JSDoc, write the images that other test files make. Write the images that the file
   does not make, and give the reason.
5. Add the endpoint mocks in `test.beforeEach`. Use `network.use()`.
6. Set a fixed time with `page.clock.setFixedTime()`.
7. Set the localStorage flags that stop first-time popups. Use the `seed*` methods of the page
   objects, for example `seedHasCompletedTask()`.
8. Write one `test()` for each image. Give the test the same name as the image.
9. Before each screenshot, wait for all visible content with `expect(...).toBeVisible()`.
10. Write the screenshot with `getScreenshotPath`. Use the image name from the docs page.

### Example

```ts
/**
 * Makes the images for the Tasklist "Some page" docs page:
 * https://docs.camunda.io/docs/next/components/tasklist/userguide/some-page/
 */

import {test, expect} from '#/pw-modules/test-extend';
import {getScreenshotPath} from '../screenshot-path';

const NOW = new Date('2024-09-05T13:32:00.000Z');

test.beforeEach(async ({network, page, tasklistIndexPage}) => {
	network.use(/* endpoint mocks */);
	await page.clock.setFixedTime(NOW);
	await tasklistIndexPage.seedHasCompletedTask();
});

test('some-image', async ({page, tasklistIndexPage}) => {
	await tasklistIndexPage.goto();
	await expect(tasklistIndexPage.tasksPanel).toBeVisible();

	await page.screenshot({
		path: getScreenshotPath({baseUrl: import.meta.url, folder: 'some-page', fileName: 'some-image.png'}),
	});
});
```

## Rules

- Use fixed data in all mocks. Do not use random values or the current date.
- Put new response factories in the correct file in `shared-test-modules/api-mocks/`. Do not
  put them in the test file.
- Put new endpoint mocks in `shared-test-modules/mock-handlers.ts`.
- Add new locators to the page objects. Do not use `page.locator()` in the test file when a page
  object can supply the locator.
- Use the light theme and the Full HD viewport. The `docs-screenshots` project sets these values.
- Do not add assertions that do not help the screenshot. These tests do not test behavior.

## Helpers

### `getScreenshotPath`

`getScreenshotPath({baseUrl, folder, fileName})` gives the absolute path of an image.

- `baseUrl`: always `import.meta.url`.
- `folder`: the folder of the image, relative to the test file. Usually this is `<page-slug>`.
- `fileName`: the image name from the docs page.

### `callouts.ts`

| Helper                                    | Function                                                            |
|-------------------------------------------|---------------------------------------------------------------------|
| `addCallouts(page, callouts)`             | Draws labels and lines to elements. Returns the box of all callouts. |
| `frameApp(page, {scale})`                 | Makes the application smaller and puts it in the center.            |
| `transformApp(page, {scale, x, y})`       | Makes the application smaller and moves it to `x` and `y`.          |
| `isolateElements(page, locators)`         | Hides all elements, but not the given elements.                     |
| `getBox(locator, {fitText})`              | Returns the box of an element or of its text.                       |
| `padBox(page, box, padding)`              | Adds space around a box. Use the result as the `clip` value.        |

Each callout has these properties:

- `label`: the text of the callout.
- `target`: one locator or a list of locators.
- `side`: `top`, `bottom`, `left`, or `right`.
- `distance`, `offset`: optional. These move the label.
- `outline`: optional. Draws a line around the target.
- `fitText`: optional. Uses the box of the text, not the box of the element.

To show only a part of the page, use the box from `addCallouts` with `padBox`. Then give the
result as `clip` to `page.screenshot()`.

## Procedure: make the images

1. Make sure that Docker operates.
2. Go to `webapp/client/apps/orchestration-cluster-webapp/`.
3. Build the application with `npm run build`.
4. Run `npm run test:docs-screenshots`.
5. To run one file, add the file path. For example:
   `npm run test:docs-screenshots -- tasklist/using-tasklist.test.ts`.
6. Examine each changed PNG with `git status` and an image viewer.

The script runs the browser in the `mcr.microsoft.com/playwright` Docker image. Thus, the
images do not change between computers. The first run can take 3 minutes because Docker
downloads the image.

## Procedure: do the checks

1. Go to `webapp/client/`.
2. Run `npm run prettier:format`.
3. Run `npm run lint`.
4. Go to `apps/orchestration-cluster-webapp/`.
5. Run `npm run typecheck`.

Do not run `npx prettier` or `npx tsc` directly.

## References

- `test/docs-screenshots/tasklist/using-tasklist.test.ts`: example with callouts, zoom, and clip.
- `test/docs-screenshots/tasklist/managing-tasks.test.ts`: example with a flow of screenshots.
- `playwright.config.ts`: the `docs-screenshots` project.
- `docs/monorepo-docs/frontend/testing.md`: the testing guide.
