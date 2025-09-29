/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@/fixtures/v1-visual';
import {mockProcesses, mockProcessWithStartForm} from '@/mocks/v1/processes';

test.describe('processes page', () => {
  test('consent modal', async ({
    page,
    mockGetProcessesRequest,
    mockGetTasksRequest,
  }) => {
    mockGetProcessesRequest();
    mockGetTasksRequest();

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state', async ({
    page,
    mockGetProcessesRequest,
    mockHasConsentedToStartProcess,
    mockGetTasksRequest,
  }) => {
    mockHasConsentedToStartProcess();
    mockGetProcessesRequest();
    mockGetTasksRequest();

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state dark theme', async ({
    page,
    mockGetProcessesRequest,
    mockHasConsentedToStartProcess,
    mockGetTasksRequest,
  }) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('theme', '"dark"');
    })()`);
    mockHasConsentedToStartProcess();
    mockGetProcessesRequest();
    mockGetTasksRequest();

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty search', async ({
    page,
    mockGetProcessesRequest,
    mockHasConsentedToStartProcess,
    mockGetTasksRequest,
  }) => {
    mockHasConsentedToStartProcess();
    mockGetProcessesRequest();
    mockGetTasksRequest();

    await page.goto('/processes?search=foo');

    await expect(
      page.getByRole('heading', {
        name: 'We could not find any process with that name',
      }),
    ).toBeVisible();
    await expect(page).toHaveScreenshot();
  });

  test('loaded processes', async ({
    page,
    mockGetProcessesRequest,
    mockHasConsentedToStartProcess,
    mockGetTasksRequest,
  }) => {
    mockHasConsentedToStartProcess();
    mockGetProcessesRequest(mockProcesses);
    mockGetTasksRequest();

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('should show a tenant dropdown', async ({
    page,
    mockClientConfigRequest,
    mockGetProcessesRequest,
    mockHasConsentedToStartProcess,
    mockGetTasksRequest,
  }) => {
    mockHasConsentedToStartProcess();
    mockClientConfigRequest({
      isEnterprise: false,
      canLogout: true,
      isLoginDelegated: false,
      contextPath: '',
      baseName: '',
      organizationId: null,
      clusterId: null,
      stage: null,
      mixpanelToken: null,
      mixpanelAPIHost: null,
      isMultiTenancyEnabled: true,
      clientMode: 'v1',
    });
    mockGetProcessesRequest(mockProcesses);
    mockGetTasksRequest();

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('should show a start form tag', async ({
    page,
    mockGetProcessesRequest,
    mockHasConsentedToStartProcess,
    mockGetTasksRequest,
  }) => {
    mockHasConsentedToStartProcess();
    mockGetProcessesRequest([mockProcessWithStartForm, mockProcesses[1]]);
    mockGetTasksRequest();

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });
});
