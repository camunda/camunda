/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {test, expect} from '@playwright/test';
import subscribeFormSchema from '../resources/subscribeForm.json' assert {type: 'json'};

test.describe('start process from form page', () => {
  test('initial page', async ({page}) => {
    await page.route(/^.*\/v1.*$/i, (route) => {
      if (route.request().url().includes('v1/external/process/foo/form')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            id: 'foo',
            processDefinitionKey: '2251799813685255',
            schema: JSON.stringify(subscribeFormSchema),
            title: 'Subscribe',
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.continue();
    });

    await page.goto('/new/foo', {
      waitUntil: 'networkidle',
    });

    await expect(
      page.getByRole('heading', {
        name: 'Subscribe to newsletter',
      }),
    ).toBeVisible();
    await expect(page.getByLabel('Name')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('process not found', async ({page}) => {
    await page.route(/^.*\/v1.*$/i, (route) => {
      if (route.request().url().includes('v1/external/process/foo/form')) {
        return route.fulfill({
          status: 404,
          body: JSON.stringify({}),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.continue();
    });

    await page.goto('/new/foo', {
      waitUntil: 'networkidle',
    });

    await expect(page.getByTestId('public-form-skeleton')).not.toBeVisible({
      timeout: 10000,
    });
    await expect(
      page.getByRole('heading', {
        name: '404 - Page not found',
      }),
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('invalid schema', async ({page}) => {
    await page.route(/^.*\/v1.*$/i, (route) => {
      if (route.request().url().includes('v1/external/process/foo/form')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            id: 'foo',
            processDefinitionKey: '2251799813685255',
            schema: `${JSON.stringify(subscribeFormSchema)}invalidschema`,
            title: 'Subscribe',
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.continue();
    });

    await page.goto('/new/foo', {
      waitUntil: 'networkidle',
    });

    await expect(
      page.getByRole('heading', {
        name: 'Invalid form',
      }),
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('successful submission', async ({page}) => {
    await page.route(/^.*\/v1.*$/i, (route) => {
      if (route.request().url().includes('v1/external/process/foo/form')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            id: 'foo',
            processDefinitionKey: '2251799813685255',
            schema: JSON.stringify(subscribeFormSchema),
            title: 'Subscribe',
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/external/process/foo/start')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            id: '2251799813685254',
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.continue();
    });

    await page.goto('/new/foo', {
      waitUntil: 'networkidle',
    });

    await page.getByLabel('Name').fill('Joe Doe');
    await page.getByLabel('Email').fill('joe@doe.com');
    await page.getByRole('button', {name: 'Submit'}).click();

    await expect(
      page.getByRole('heading', {
        name: 'Success!',
      }),
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('failed submission', async ({page}) => {
    await page.route(/^.*\/v1.*$/i, (route) => {
      if (route.request().url().includes('v1/external/process/foo/form')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            id: 'foo',
            processDefinitionKey: '2251799813685255',
            schema: JSON.stringify(subscribeFormSchema),
            title: 'Subscribe',
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/external/process/foo/start')) {
        return route.fulfill({
          status: 500,
          body: JSON.stringify({}),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.continue();
    });

    await page.goto('/new/foo', {
      waitUntil: 'networkidle',
    });

    await page.getByLabel('Name').fill('Joe Doe');
    await page.getByLabel('Email').fill('joe@doe.com');
    await page.getByRole('button', {name: 'Submit'}).click();

    await expect(
      page.getByRole('heading', {
        name: 'Something went wrong',
      }),
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });
});
