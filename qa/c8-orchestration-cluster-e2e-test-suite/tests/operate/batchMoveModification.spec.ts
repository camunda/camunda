/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@fixtures';
import {expect} from '@playwright/test';
import {deploy, createInstances} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {
  navigateToApp,
  hideModificationHelperModal,
  gotoProcessesPage,
} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';

type ProcessInstance = {
  processInstanceKey: string;
};

let processInstances: ProcessInstance[];

const NUM_PROCESS_INSTANCES = 10;
const NUM_SELECTED_PROCESS_INSTANCES = 4;

test.beforeAll(async () => {
  await deploy(['./resources/orderProcessBatchMod_v_1.bpmn']);

  processInstances = await createInstances(
    'orderProcessBatchMod',
    1,
    NUM_PROCESS_INSTANCES,
  );

  await sleep(2000);
});

test.describe('Process Instance Batch Modification', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await hideModificationHelperModal(page);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Move Operation', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
    operateOperationPanelPage,
    operateDiagramPage,
    operateProcessModificationModePage,
  }) => {
    const processInstanceKeys = processInstances.map(
      (instance) => instance.processInstanceKey,
    );

    await test.step('Navigate to processes page with filters', async () => {
      await gotoProcessesPage(page, {
        searchParams: {
          active: 'true',
          process: 'orderProcessBatchMod',
          version: '1',
          flowNodeId: 'checkPayment',
        },
      });
    });

    await test.step('Verify correct number of instances displayed', async () => {
      await expect(
        page.getByText(`${NUM_PROCESS_INSTANCES} results`),
      ).toBeVisible();
    });

    await test.step('Select 4 process instances for move modification', async () => {
      await operateProcessesPage.selectProcessInstances(
        NUM_SELECTED_PROCESS_INSTANCES,
      );
    });

    await test.step('Start move modification', async () => {
      await operateProcessModificationModePage.startBatchMoveModification();
    });

    await test.step('Select target flow node', async () => {
      await operateDiagramPage.clickFlowNode('shipArticles');

      await operateProcessModificationModePage.expectModificationNotification(
        NUM_SELECTED_PROCESS_INSTANCES,
        'Check payment',
        'Ship Articles',
      );
    });

    await test.step('Test undo functionality', async () => {
      await operateProcessModificationModePage.clickUndoButton();

      await expect(
        operateProcessModificationModePage.modificationNotification.filter({
          hasText: 'Modification scheduled',
        }),
      ).toBeHidden();

      await expect(
        operateProcessModificationModePage.batchModificationModeText,
      ).toBeVisible();
      await expect(
        operateProcessModificationModePage.applyModificationButton,
      ).toBeDisabled();
    });

    await test.step('Re-select target flow node and apply modification', async () => {
      await operateDiagramPage.clickFlowNode('shipArticles');

      await operateProcessModificationModePage.expectModificationNotification(
        NUM_SELECTED_PROCESS_INSTANCES,
        'Check payment',
        'Ship Articles',
      );

      await operateProcessModificationModePage.applyAndConfirmModification();
    });

    await test.step('Verify operation is created and completed', async () => {
      await expect(operateOperationPanelPage.operationsList).toBeVisible();
      const operationEntry =
        operateOperationPanelPage.getModificationOperationEntry(4);

      await expect(operationEntry).toBeVisible({timeout: 120000});

      await operateOperationPanelPage.clickOperationLink(operationEntry);
    });

    await test.step('Filter and verify modified instances', async () => {
      await operateFiltersPanelPage.selectProcess(
        'Order process (Batch Modification)',
      );
      await operateFiltersPanelPage.selectVersion('1');
      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );
      await operateFiltersPanelPage.processInstanceKeysFilter.fill(
        processInstanceKeys.join(','),
      );

      await expect(
        page.getByText(`${NUM_SELECTED_PROCESS_INSTANCES} results`),
      ).toBeVisible();
    });

    await test.step('Wait for modification to complete and verify flow nodes', async () => {
      await operateOperationPanelPage.waitForOperationToComplete();

      await page.reload();

      await expect(
        operateDiagramPage.diagram.getByTestId(
          'state-overlay-checkPayment-canceled',
        ),
      ).toHaveText(NUM_SELECTED_PROCESS_INSTANCES.toString());

      await expect(
        operateDiagramPage.diagram.getByTestId(
          'state-overlay-shipArticles-active',
        ),
      ).toHaveText(NUM_SELECTED_PROCESS_INSTANCES.toString());
    });
  });

  test('Exit Modal', async ({
    page,
    operateProcessesPage,
    operateDiagramPage,
    operateProcessModificationModePage,
    operateHomePage,
    operateDashboardPage,
  }) => {
    await test.step('Navigate to processes page and select instance', async () => {
      await gotoProcessesPage(page, {
        searchParams: {
          active: 'true',
          process: 'orderProcessBatchMod',
          version: '1',
          flowNodeId: 'checkPayment',
        },
      });
      await operateProcessesPage.selectProcessInstances(1);
    });

    await test.step('Enter batch modification mode', async () => {
      await operateProcessModificationModePage.startBatchMoveModification();

      await operateDiagramPage.clickFlowNode('shipArticles');
    });

    await test.step('Test navigation interruption and modal', async () => {
      await operateHomePage.clickDashboardLink();

      await expect(
        operateProcessModificationModePage.exitModificationModeModal.dialog,
      ).toBeVisible();
      await expect(
        operateProcessModificationModePage.exitModificationModeModal.subtitle,
      ).toBeVisible();
    });

    await test.step('Test modal cancellation', async () => {
      await operateProcessModificationModePage.cancelExitModificationMode();
      await expect(
        operateProcessModificationModePage.exitModificationModeModal.dialog,
      ).toBeHidden();
      await expect(
        operateProcessModificationModePage.batchModificationModeText,
      ).toBeVisible();
    });

    await test.step('Test modal confirmation and exit', async () => {
      await operateHomePage.clickDashboardLink();

      await operateProcessModificationModePage.confirmExitModificationMode();

      await expect(
        operateProcessModificationModePage.batchModificationModeText,
      ).toBeHidden();

      await expect(operateDashboardPage.runningInstancesText).toBeVisible();

      await page.goBack();

      await expect(
        operateProcessModificationModePage.batchModificationModeText,
      ).toBeHidden();
    });
  });
});
