/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Route, type Request} from '@playwright/test';
import {test} from '@/visual-fixtures';
import {apiURLPattern} from '@/mocks/apiURLPattern';
import {multiTenancyUser} from '@/mocks/users';
import {clientConfig} from '@/mocks/clientConfig';
import {
  noStartFormProcessQueryResult,
  processWithStartFormQueryResult,
} from '@/mocks/processes';
import {invalidLicense} from '@/mocks/licenses';

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
        body: JSON.stringify(multiTenancyUser),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('v2/license')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(invalidLicense),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    return route.continue();
  };
}

test.describe('processes page', () => {
  test('consent modal', async ({page}) => {
    await page.route(apiURLPattern, mockResponses());

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state', async ({page}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    })()`);
    await page.route(apiURLPattern, mockResponses());

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state dark theme', async ({page}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
      window.localStorage.setItem('theme', '"dark"');
    })()`);
    await page.route(apiURLPattern, mockResponses());

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty search', async ({page}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    })()`);
    await page.route(apiURLPattern, mockResponses());

    await page.goto('/processes?search=foo', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('loaded processes', async ({page}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    })()`);
    await page.route(
      apiURLPattern,
      mockResponses(noStartFormProcessQueryResult),
    );

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('should show a tenant dropdown', async ({page}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    })()`);
    await page.route('**/client-config.js', (route) =>
      route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'text/javascript;charset=UTF-8',
        },
        body: `window.clientConfig = ${JSON.stringify(clientConfig)};`,
      }),
    );
    await page.route(
      apiURLPattern,
      mockResponses(noStartFormProcessQueryResult),
    );

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('should show a start form tag', async ({page}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    })()`);
    await page.route(
      apiURLPattern,
      mockResponses(processWithStartFormQueryResult),
    );

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });
});
