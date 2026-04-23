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
import {findUserTask} from '@requestHelpers';

type ProcessInstance = {
  processInstanceKey: string;
};

test.describe('Task History Audit Log', () => {
  let processInstance: ProcessInstance;

  test.beforeAll(async () => {
    await deploy(['./resources/usertask_to_be_completed.bpmn']);
    processInstance = await createSingleInstance('usertask_to_be_completed', 1);
  });

  test.beforeEach(
    async ({page, loginPage, taskPanelPage, taskDetailsPage, request}) => {
      await navigateToApp(page, 'tasklist');
      await loginPage.login('demo', 'demo');
      await expect(page).toHaveURL('/tasklist');

      const taskKey = await findUserTask(
        request,
        processInstance.processInstanceKey,
        'CREATED',
      );

      await taskPanelPage.goToTaskDetails(taskKey);

      await taskDetailsPage.clickAssignToMeButton();
      await expect(taskDetailsPage.unassignButton).toBeVisible();
    },
  );

  test.afterEach(async ({page, taskDetailsPage}, testInfo) => {
    await taskDetailsPage.clickUnassignButton();
    await expect(taskDetailsPage.assignToMeButton).toBeVisible();

    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('History tab is visible in task details', async ({taskDetailsPage}) => {
    await expect(taskDetailsPage.historyTabButton).toBeVisible();
  });

  test.skip('Audit log entries are visible in task history', async ({
    taskDetailsPage,
  }) => {
    await taskDetailsPage.clickHistoryTab();

    await expect
      .poll(async () => taskDetailsPage.getHistoryTableRowCount(), {
        timeout: 60000,
      })
      .toBeGreaterThan(1);
  });

  test('Task history shows correct column headers', async ({
    taskDetailsPage,
  }) => {
    await taskDetailsPage.clickHistoryTab();

    await expect(taskDetailsPage.historyTableOperationTypeHeader).toBeVisible();
    await expect(taskDetailsPage.historyTableDetailsHeader).toBeVisible();
    await expect(taskDetailsPage.historyTableActorHeader).toBeVisible();
    await expect(taskDetailsPage.historyTableDateHeader).toBeVisible();
  });

  test('Task history shows assign task entry', async ({taskDetailsPage}) => {
    await taskDetailsPage.clickHistoryTab();

    await expect
      .poll(async () => taskDetailsPage.getHistoryTableAssignCellCount(), {
        timeout: 60000,
      })
      .toBeGreaterThan(0);
  });
});
