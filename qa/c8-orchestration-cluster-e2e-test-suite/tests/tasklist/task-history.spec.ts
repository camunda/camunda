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
    // Use a process dedicated to this suite instead of the shared
    // `usertask_to_be_completed` one. `task-details.spec.ts` runs in parallel
    // (same chromium project, 4 workers), deploys that same process, and its
    // `complete task` test opens a task by process id via `.nth(0)` — which
    // can land on, and complete, THIS suite's instance. A completed task can
    // no longer be unassigned, so the afterEach's Unassign→"Assign to me"
    // loop then times out. A unique process id keeps our instance invisible
    // to any by-name selection in another suite.
    await deploy(['./resources/usertask_for_history.bpmn']);
    // deployResources resolves once the deployment is committed, but the new
    // definition still has to be distributed to every partition before an
    // instance can be created by id+version. Because this process is deployed
    // only here (no other parallel suite pre-warms it), creating immediately
    // can hit a partition that has not applied the deployment yet and fail
    // with 404 NOT_FOUND. Retry across that short distribution window rather
    // than a fixed sleep so it waits exactly as long as needed and no longer.
    await expect(async () => {
      processInstance = await createSingleInstance('usertask_for_history', 1);
    }).toPass({timeout: 30000, intervals: [1000]});
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
      // Unassign button replaces Assign-to-me asynchronously after the
      // assign POST resolves; the default 10s races tasklist refresh on a
      // loaded shared cluster (matches the 90s afterEach budget below).
      await expect(taskDetailsPage.unassignButton).toBeVisible({
        timeout: 30000,
      });
    },
  );

  test.afterEach(async ({page, taskDetailsPage}, testInfo) => {
    // Retry the unassign in case the first click doesn't register or the UI
    // is slow to reflect the change on a loaded shared cluster.
    await expect(async () => {
      if (await taskDetailsPage.unassignButton.isVisible()) {
        await taskDetailsPage.clickUnassignButton();
      }
      await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    }).toPass({timeout: 120000, intervals: [5000]});

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
