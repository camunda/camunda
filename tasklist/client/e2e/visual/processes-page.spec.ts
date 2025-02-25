/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Route, type Request} from '@playwright/test';
import {test} from '@/visual-fixtures';
import {URL_API_V1_PATTERN} from '@/constants';

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

    return route.continue();
  };
}

test.describe('processes page', () => {
  test('consent modal', async ({page}) => {
    await page.route(URL_API_V1_PATTERN, mockResponses());

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state', async ({page}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    })()`);
    await page.route(URL_API_V1_PATTERN, mockResponses());

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
    await page.route(URL_API_V1_PATTERN, mockResponses());

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty search', async ({page}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasConsentedToStartProcess', 'true');
    })()`);
    await page.route(URL_API_V1_PATTERN, mockResponses());

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
      URL_API_V1_PATTERN,
      mockResponses([
        {
          id: '2251799813685285',
          name: 'multipleVersions',
          bpmnProcessId: 'multipleVersions',
          version: 1,
          startEventFormId: null,
        },
        {
          id: '2251799813685271',
          name: 'Order process',
          bpmnProcessId: 'orderProcess',
          version: 1,
          startEventFormId: null,
        },
      ]),
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
        body: `window.clientConfig = {
        "isEnterprise":false,
        "canLogout":true,
        "isLoginDelegated":false,
        "contextPath":"",
        "baseName":"",
        "organizationId":null,
        "clusterId":null,
        "stage":null,
        "mixpanelToken":null,
        "mixpanelAPIHost":null,
        "isMultiTenancyEnabled": true
      };`,
      }),
    );
    await page.route(
      URL_API_V1_PATTERN,
      mockResponses([
        {
          id: '2251799813685285',
          name: 'multipleVersions',
          bpmnProcessId: 'multipleVersions',
          version: 1,
          startEventFormId: null,
        },
        {
          id: '2251799813685271',
          name: 'Order process',
          bpmnProcessId: 'orderProcess',
          version: 1,
          startEventFormId: null,
        },
      ]),
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
      URL_API_V1_PATTERN,
      mockResponses([
        {
          id: '2251799813685285',
          name: 'startForm',
          bpmnProcessId: 'startForm',
          version: 1,
          startEventFormId: 'startFormForm',
        },
        {
          id: '2251799813685271',
          name: 'Order process',
          bpmnProcessId: 'orderProcess',
          version: 1,
          startEventFormId: null,
        },
      ]),
    );

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });
});
