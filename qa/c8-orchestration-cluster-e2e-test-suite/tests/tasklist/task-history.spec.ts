/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createSingleInstance} from 'utils/zeebeClient';
import {navigateToApp} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.describe('Task History Audit Log', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/usertask_to_be_completed.bpmn']);
    await createSingleInstance('usertask_to_be_completed', 1);
  });

  test.beforeEach(async ({page, loginPage, taskPanelPage, taskDetailsPage}) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist');

    await taskPanelPage.openTask('usertask_to_be_completed');

    await taskDetailsPage.assignToMeButton.click();
    await expect(taskDetailsPage.unassignButton).toBeVisible();
  });

  test.afterEach(async ({page, taskDetailsPage}, testInfo) => {
    await taskDetailsPage.unassignButton.click();
    await expect(taskDetailsPage.assignToMeButton).toBeVisible();

    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('History tab is visible in task details', async ({taskDetailsPage}) => {
    await expect(taskDetailsPage.historyTabButton).toBeVisible();
  });

  test('Audit log entries are visible in task history', async ({
    taskDetailsPage,
  }) => {
    await taskDetailsPage.clickHistoryTab();

    await expect
      .poll(async () => taskDetailsPage.historyTable.getByRole('row').count(), {
        timeout: 60000,
      })
      .toBeGreaterThan(1);
  });

  test('Task history shows correct column headers', async ({
    page,
    taskDetailsPage,
  }) => {
    await taskDetailsPage.clickHistoryTab();

    await expect(
      page.getByRole('columnheader', {name: 'Operation'}),
    ).toBeVisible();
    await expect(
      page.getByRole('columnheader', {name: 'Property'}),
    ).toBeVisible();
    await expect(page.getByRole('columnheader', {name: 'Actor'})).toBeVisible();
    await expect(page.getByRole('columnheader', {name: 'Time'})).toBeVisible();
  });

  test('Task history shows assign task entry', async ({taskDetailsPage}) => {
    await taskDetailsPage.clickHistoryTab();

    await expect
      .poll(
        async () =>
          taskDetailsPage.historyTable
            .getByRole('cell', {name: 'Assign task'})
            .count(),
        {timeout: 60000},
      )
      .toBeGreaterThan(0);
  });
});
