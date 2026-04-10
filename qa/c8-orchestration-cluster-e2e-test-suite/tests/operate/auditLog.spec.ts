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
import {waitForProcessInstances} from 'utils/incidentsHelper';

type ProcessInstance = {
  processInstanceKey: string;
};

let process: {
  instance: ProcessInstance;
};

test.beforeAll(async ({request}) => {
  await deploy(['./resources/orderProcess_v_1.bpmn']);

  const instance = await createSingleInstance('orderProcess', 1, {
    paid: false,
  });

  process = {
    instance: {processInstanceKey: instance.processInstanceKey},
  };

  await waitForProcessInstances(request, [instance.processInstanceKey], 1);
});

test.describe('Audit Log (Operations Log)', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Operations Log page is accessible and shows table headers', async ({
    operateOperationsLogPage,
  }) => {
    await operateOperationsLogPage.gotoOperationsLogPage();

    await expect(operateOperationsLogPage.operationsLogHeading).toBeVisible();

    await expect(
      operateOperationsLogPage.operationTypeColumnHeader,
    ).toBeVisible();
    await expect(operateOperationsLogPage.entityTypeColumnHeader).toBeVisible();
    await expect(operateOperationsLogPage.entityKeyColumnHeader).toBeVisible();
    await expect(operateOperationsLogPage.actorColumnHeader).toBeVisible();
    await expect(operateOperationsLogPage.dateColumnHeader).toBeVisible();
  });

  test('Audit log entries are visible after instance creation', async ({
    operateOperationsLogPage,
  }) => {
    await operateOperationsLogPage.gotoOperationsLogPage();

    await expect
      .poll(async () => operateOperationsLogPage.getRowCount(), {
        timeout: 60000,
      })
      .toBeGreaterThan(1);
  });

  test('Audit log entries can be filtered by process instance key', async ({
    operateOperationsLogPage,
  }) => {
    const {processInstanceKey} = process.instance;

    await operateOperationsLogPage.gotoOperationsLogPage({
      searchParams: {processInstanceKey},
    });

    await expect(operateOperationsLogPage.operationsLogHeading).toBeVisible();

    await expect(operateOperationsLogPage.processInstanceKeyFilter).toHaveValue(
      processInstanceKey,
    );

    await expect
      .poll(async () => operateOperationsLogPage.getRowCount(), {
        timeout: 60000,
      })
      .toBeGreaterThan(1);

    await expect(
      operateOperationsLogPage.processInstanceCells.first(),
    ).toBeVisible();
  });

  test('Operations Log page title is correct', async ({
    page,
    operateOperationsLogPage,
  }) => {
    await operateOperationsLogPage.gotoOperationsLogPage();
    await expect(page).toHaveTitle('Operate: Operations Log');
  });

  test('Operations Log shows empty state when no results match filters', async ({
    page,
    operateOperationsLogPage,
  }) => {
    await operateOperationsLogPage.gotoOperationsLogPage({
      searchParams: {processInstanceKey: '0'},
    });

    await expect(page.getByText(/no operations log found/i)).toBeVisible();
  });
});
