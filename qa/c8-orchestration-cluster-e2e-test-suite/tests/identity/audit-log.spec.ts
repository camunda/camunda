/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {relativizePath, Paths} from 'utils/relativizePath';
import {navigateToApp} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {LOGIN_CREDENTIALS} from 'utils/constants';

test.describe('Identity Audit Log', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await navigateToApp(page, 'admin');
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.users()));
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Audit log page is accessible and shows table headers', async ({
    page,
    identityAuditLogPage,
  }) => {
    await identityAuditLogPage.navigateToAuditLog();

    await expect(page).toHaveURL(relativizePath(Paths.operationsLog()));

    await expect(identityAuditLogPage.auditLogHeading).toBeVisible();

    await expect(
      page.getByRole('columnheader', {name: 'Operation type'}),
    ).toBeVisible();
    await expect(
      page.getByRole('columnheader', {name: 'Entity type'}),
    ).toBeVisible();
    await expect(
      page.getByRole('columnheader', {name: 'Reference to entity'}),
    ).toBeVisible();
    await expect(page.getByRole('columnheader', {name: 'Actor'})).toBeVisible();
    await expect(page.getByRole('columnheader', {name: 'Date'})).toBeVisible();
  });

  test('Audit log entries are visible in the Identity audit log UI', async ({
    identityAuditLogPage,
  }) => {
    await identityAuditLogPage.navigateToAuditLog();

    await expect
      .poll(
        async () =>
          await identityAuditLogPage.auditLogTable.getByRole('row').count(),
        {timeout: 60000},
      )
      .toBeGreaterThan(1);
  });

  test('Audit log can be filtered by actor', async ({identityAuditLogPage}) => {
    await identityAuditLogPage.navigateToAuditLog();

    await identityAuditLogPage.actorFilter.fill(LOGIN_CREDENTIALS.username);

    await expect
      .poll(
        async () =>
          await identityAuditLogPage.auditLogTable.getByRole('row').count(),
        {timeout: 60000},
      )
      .toBeGreaterThan(1);

    await expect(identityAuditLogPage.resetFiltersButton).toBeEnabled();

    await identityAuditLogPage.resetFiltersButton.click();

    await expect(identityAuditLogPage.actorFilter).toHaveValue('');
    await expect(identityAuditLogPage.resetFiltersButton).toBeDisabled();
  });

  test('Audit log navigation link is visible in the Identity UI', async ({
    page,
    identityAuditLogPage,
  }) => {
    await identityAuditLogPage.navigateToAuditLog();

    await expect(
      page.locator('nav a').filter({hasText: /^Operations Log$/}),
    ).toBeVisible();
  });
});
