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

let deployedProcess: {
  instance: ProcessInstance;
};

test.beforeAll(async ({request}) => {
  await deploy(['./resources/orderProcess_v_1.bpmn']);

  const instance = await createSingleInstance('orderProcess', 1, {
    paid: false,
  });

  deployedProcess = {
    instance: {processInstanceKey: instance.processInstanceKey},
  };

  await waitForProcessInstances(request, [instance.processInstanceKey], 1);
});

test.describe('Process Instance Audit Log', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Operations Log tab is visible in process instance details', async ({
    operateProcessInstancePage,
  }) => {
    const {processInstanceKey} = deployedProcess.instance;

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: processInstanceKey,
    });

    await expect(
      operateProcessInstancePage.operationsLogTabButton,
    ).toBeVisible();
  });

  test('Audit log entries are visible in process instance details', async ({
    operateProcessInstancePage,
  }) => {
    const {processInstanceKey} = deployedProcess.instance;

    await operateProcessInstancePage.gotoProcessInstanceOperationsLogPage({
      id: processInstanceKey,
    });

    await expect
      .poll(
        async () => operateProcessInstancePage.getOperationsLogTableRowCount(),
        {timeout: 60000},
      )
      .toBeGreaterThan(1);
  });

  test('Operations Log table shows correct column headers', async ({
    operateProcessInstancePage,
  }) => {
    const {processInstanceKey} = deployedProcess.instance;

    await operateProcessInstancePage.gotoProcessInstanceOperationsLogPage({
      id: processInstanceKey,
    });

    await expect(
      operateProcessInstancePage.operationsLogTableOperationTypeHeader,
    ).toBeVisible();
    await expect(
      operateProcessInstancePage.operationsLogTableEntityTypeHeader,
    ).toBeVisible();
    await expect(
      operateProcessInstancePage.operationsLogTableEntityKeyHeader,
    ).toBeVisible();
    await expect(
      operateProcessInstancePage.operationsLogTableActorHeader,
    ).toBeVisible();
    await expect(
      operateProcessInstancePage.operationsLogTableSortByDateHeader,
    ).toBeVisible();
  });

  test('Operations Log shows process instance create entry', async ({
    operateProcessInstancePage,
  }) => {
    const {processInstanceKey} = deployedProcess.instance;

    await operateProcessInstancePage.gotoProcessInstanceOperationsLogPage({
      id: processInstanceKey,
    });

    await expect
      .poll(
        async () =>
          operateProcessInstancePage.getOperationsLogTableProcessInstanceCellCount(),
        {timeout: 60000},
      )
      .toBeGreaterThan(0);
  });
});
