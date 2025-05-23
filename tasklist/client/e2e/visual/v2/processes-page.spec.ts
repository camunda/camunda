/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@/fixtures/v2-visual';
import {mockProcesses} from '@/mocks/v2/processes';

test.describe('processes page', () => {
  test('consent modal', async ({page, mockQueryProcessDefinitionsRequest}) => {
    mockQueryProcessDefinitionsRequest();

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state', async ({
    page,
    mockQueryProcessDefinitionsRequest,
    mockHasConsentedToStartProcess,
  }) => {
    mockHasConsentedToStartProcess();
    mockQueryProcessDefinitionsRequest();

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state dark theme', async ({
    page,
    mockQueryProcessDefinitionsRequest,
    mockHasConsentedToStartProcess,
  }) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('theme', '"dark"');
    })()`);
    mockHasConsentedToStartProcess();
    mockQueryProcessDefinitionsRequest();

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test.skip('empty search', async ({
    page,
    mockQueryProcessDefinitionsRequest,
    mockHasConsentedToStartProcess,
  }) => {
    mockHasConsentedToStartProcess();
    mockQueryProcessDefinitionsRequest();

    await page.goto('/processes?search=foo', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('loaded processes', async ({
    page,
    mockQueryProcessDefinitionsRequest,
    mockHasConsentedToStartProcess,
  }) => {
    mockHasConsentedToStartProcess();
    mockQueryProcessDefinitionsRequest(mockProcesses);

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test.skip('should show a tenant dropdown', async ({
    page,
    mockClientConfigRequest,
    mockQueryProcessDefinitionsRequest,
    mockHasConsentedToStartProcess,
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
      clientMode: 'v2',
    });

    mockQueryProcessDefinitionsRequest();

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test.skip('should show a start form tag', async ({
    page,
    mockQueryProcessDefinitionsRequest,
    mockHasConsentedToStartProcess,
  }) => {
    mockHasConsentedToStartProcess();
    mockQueryProcessDefinitionsRequest();

    await page.goto('/processes', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });
});
