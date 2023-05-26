/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect, Route, Request} from '@playwright/test';

function mockResponses(
  processes: Array<unknown> = [],
): (router: Route, request: Request) => Promise<unknown> | unknown {
  return (route) => {
    if (route.request().url().includes('v1/internal/processes')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(processes),
      });
    }

    if (route.request().url().includes('v1/internal/users/current')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          userId: 'demo',
          displayName: 'demo',
          permissions: ['READ', 'WRITE'],
          salesPlanType: null,
          roles: null,
          c8Links: [],
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

test.describe('processes page', () => {
  test('consent modal', async ({page}) => {
    await page.route(/^.*\/(graphql|v1).*$/i, mockResponses());

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state', async ({page}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    });
    await page.route(/^.*\/(graphql|v1).*$/i, mockResponses());

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state dark theme', async ({page}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
      window.localStorage.setItem('theme', '"dark"');
    });
    await page.route(/^.*\/(graphql|v1).*$/i, mockResponses());

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty search', async ({page}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    });
    await page.route(/^.*\/(graphql|v1).*$/i, mockResponses());

    await page.goto('/processes?search=foo', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('loaded processes', async ({page}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    });
    await page.route(
      /^.*\/(graphql|v1).*$/i,
      mockResponses([
        {
          id: '2251799813685285',
          name: 'multipleVersions',
          processDefinitionKey: 'multipleVersions',
        },
        {
          id: '2251799813685271',
          name: 'Order process',
          processDefinitionKey: 'orderProcess',
        },
      ]),
    );

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });
});
