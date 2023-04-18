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
    const {operationName} = route.request().postDataJSON();

    switch (operationName) {
      case 'GetCurrentUser':
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            data: {
              currentUser: {
                userId: 'demo',
                displayName: 'demo',
                permissions: ['READ', 'WRITE'],
                salesPlanType: null,
                roles: null,
                c8Links: [],
                __typename: 'User',
              },
            },
          }),
        });
      case 'GetProcesses':
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            data: {
              processes,
            },
          }),
        });
      default:
        return route.fulfill({
          status: 500,
          body: JSON.stringify({
            message: '',
          }),
        });
    }
  };
}

test.describe('processes page', () => {
  test('consent modal', async ({page}) => {
    await page.route('**/graphql', mockResponses());

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state', async ({page}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    });
    await page.route('**/graphql', mockResponses());

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
    await page.route('**/graphql', mockResponses());

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty search', async ({page}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    });
    await page.route('**/graphql', mockResponses());

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
      '**/graphql',
      mockResponses([
        {
          id: '2251799813685285',
          name: 'multipleVersions',
          processDefinitionId: 'multipleVersions',
          __typename: 'Process',
        },
        {
          id: '2251799813685271',
          name: 'Order process',
          processDefinitionId: 'orderProcess',
          __typename: 'Process',
        },
      ]),
    );

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });
});
