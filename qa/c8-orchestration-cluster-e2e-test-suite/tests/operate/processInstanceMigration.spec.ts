/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createInstances} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp, validateURL} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';

type ProcessDeployment = {
  bpmnProcessId: string;
  version: number;
};

type SetupData = {
  processV1: ProcessDeployment;
  processV2: ProcessDeployment;
  processV3: ProcessDeployment;
};

let initialData: SetupData;

test.beforeAll(async () => {
  await deploy(['./resources/orderProcessMigration_v_1.bpmn']);
  const processV1: ProcessDeployment = {
    bpmnProcessId: 'orderProcessMigration',
    version: 1,
  };
  await Promise.all(
    [...new Array(10)].map((_, index) =>
      createInstances(processV1.bpmnProcessId, processV1.version, 1, {
        key1: 'myFirstCorrelationKey',
        key2: 'mySecondCorrelationKey',
        key3: `myCorrelationKey${index}`,
      }),
    ),
  );
  await deploy(['./resources/orderProcessMigration_v_2.bpmn']);
  const processV2: ProcessDeployment = {
    bpmnProcessId: 'orderProcessMigration',
    version: 2,
  };
  await deploy(['./resources/orderProcessMigration_v_3.bpmn']);
  const processV3: ProcessDeployment = {
    bpmnProcessId: 'newOrderProcessMigration',
    version: 1,
  };
  // await sleep(2000);
  initialData = {
    processV1,
    processV2,
    processV3,
  };
  await sleep(2000);
});

test.describe.serial('Process Instance Migration', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Auto mapping migration', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessesMigrationPage,
  }) => {
    test.slow();

    const sourceVersion = initialData.processV1.version.toString();
    const sourceBpmnProcessId = initialData.processV1.bpmnProcessId;
    const targetVersion = initialData.processV2.version.toString();
    const targetBpmnProcessId = initialData.processV2.bpmnProcessId;

    await test.step('Filter by process name and version', async () => {
      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);

      await expect(page.getByText('results')).toBeVisible({timeout: 30000});
    });

    await test.step('Select first 6 process instances for migration', async () => {
      await operateProcessesPage.selectProcessInstances(6);

      await operateProcessesPage.startMigration();
    });

    await test.step('Verify target process is preselected with auto-mapping and Complete Migration', async () => {
      await expect(
        operateProcessesMigrationPage.targetProcessCombobox,
      ).toHaveValue(targetBpmnProcessId);

      await expect(
        operateProcessesMigrationPage.targetVersionDropdown,
      ).toHaveText(targetVersion, {
        useInnerText: true,
      });

      await expect(
        page.getByLabel('target element for check payment'),
      ).toHaveValue('checkPayment');
      await expect(
        page.getByLabel('target element for ship articles'),
      ).toHaveValue('shipArticles');

      await operateProcessesMigrationPage.completeProcessInstanceMigration();
    });

    await test.step('Verify migration operation is created and completes', async () => {
      await expect(operateProcessesPage.operationsList).toBeVisible({
        timeout: 30000,
      });

      await operateProcessesPage.waitForOperationToComplete();
    });

    await test.step('Verify 6 instances migrated to target version', async () => {
      await expect(operateProcessesPage.operationSuccessMessage).toBeVisible({
        timeout: 120000,
      });

      await operateProcessesPage.clickLatestOperationLink();

      await validateURL(page, /operationId=/);
      await expect(page.getByText('6 results')).toBeVisible({
        timeout: 30000,
      });

      await expect(operateProcessesPage.getVersionCells('2')).toHaveCount(6, {
        timeout: 30000,
      });
    });
  });

  /**
   * Test manual mapping migration from ProcessV2 to ProcessV3
   * ProcessV3 has different bpmn process id and flow node names,
   * so manual mapping is required
   */
  test('Manual mapping migration', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessesMigrationPage,
  }) => {
    test.slow();

    const sourceVersion = initialData.processV2.version.toString();
    const sourceBpmnProcessId = initialData.processV2.bpmnProcessId;
    const targetVersion = initialData.processV3.version.toString();
    const targetBpmnProcessId = initialData.processV3.bpmnProcessId;

    await test.step('Filter by process name and version', async () => {
      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);

      await expect(page.getByText('results')).toBeVisible({timeout: 30000});
    });

    await test.step('Select 3 process instances for migration', async () => {
      await operateProcessesPage.selectProcessInstances(3);

      await operateProcessesPage.startMigration();
    });

    await test.step('Manually select target process and version', async () => {
      await operateProcessesMigrationPage.targetProcessCombobox.click();
      await page
        .getByRole('option', {name: targetBpmnProcessId, exact: true})
        .click();

      await operateProcessesMigrationPage.targetVersionDropdown.click();
      await page
        .getByRole('option', {name: targetVersion, exact: true})
        .click();
    });

    await test.step('Manually map flow nodes', async () => {
      // Map Check payment to Ship Articles 2
      await operateProcessesMigrationPage.mapFlowNode(
        'Check payment',
        'Ship Articles 2',
      );

      // Map Tasks A-D (note cross mapping: A -> C2, C -> A2)
      await operateProcessesMigrationPage.mapFlowNode('Task A', 'Task C2');
      await operateProcessesMigrationPage.mapFlowNode('Task B', 'Task B2');
      await operateProcessesMigrationPage.mapFlowNode('Task C', 'Task A2');
      await operateProcessesMigrationPage.mapFlowNode('Task D', 'Task D2');

      // Map boundary events (cross mapping: interrupting <-> non-interrupting)
      await operateProcessesMigrationPage.mapFlowNode(
        'Message interrupting',
        'Message non-interrupting 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Timer interrupting',
        'Timer interrupting 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Message non-interrupting',
        'Message interrupting 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Timer non-interrupting',
        'Timer non-interrupting 2',
      );

      // Map intermediate catch events
      await operateProcessesMigrationPage.mapFlowNode(
        'Message intermediate catch',
        'Message intermediate catch 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Timer intermediate catch',
        'Timer intermediate catch 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Message intermediate catch B',
        'Message intermediate catch B2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Timer intermediate catch B',
        'Timer intermediate catch B2',
      );

      // Map event sub processes with containing tasks
      await operateProcessesMigrationPage.mapFlowNode(
        'Message event sub process',
        'Message event sub process 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Timer event sub process',
        'Timer event sub process 2',
      );
      await operateProcessesMigrationPage.mapFlowNode('Task E', 'Task E2');
      await operateProcessesMigrationPage.mapFlowNode('Task F', 'Task F2');
      await operateProcessesMigrationPage.mapFlowNode(
        'Timer start event',
        'Timer start event 2',
      );

      // Map signal elements
      await operateProcessesMigrationPage.mapFlowNode(
        'Signal intermediate catch',
        'Signal intermediate catch 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Signal start event',
        'Signal start event',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Signal boundary event',
        'Signal boundary event 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Signal event sub process',
        'Signal event sub process 2',
      );

      // Map other tasks
      await operateProcessesMigrationPage.mapFlowNode(
        'Message receive task',
        'Message receive task 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Business rule task',
        'Business rule task 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Script task',
        'Script task 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Send Task',
        'Send Task 2',
      );

      // Map gateways
      await operateProcessesMigrationPage.mapFlowNode(
        'Event based gateway',
        'Event based gateway 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Exclusive gateway',
        'Exclusive gateway 2',
      );

      // Map multi instance elements
      await operateProcessesMigrationPage.mapFlowNode(
        'Multi instance sub process',
        'Multi instance sub process 2',
      );
      await operateProcessesMigrationPage.mapFlowNode(
        'Multi instance task',
        'Multi instance task 2',
      );
    });

    await test.step('Proceed to summary and verify migration details', async () => {
      await operateProcessesMigrationPage.nextButton.click();

      const summaryNotification = page.getByRole('main').getByRole('status');
      await expect(summaryNotification).toContainText(
        `You are about to migrate 3 process instances from the process definition: ${sourceBpmnProcessId} - version ${sourceVersion} to the process definition: ${targetBpmnProcessId} - version ${targetVersion}`,
      );
    });

    await test.step('Confirm migration and type MIGRATE', async () => {
      await operateProcessesMigrationPage.confirmButton.click();
      await operateProcessesMigrationPage.modificationConfirmationInput.fill(
        'MIGRATE',
      );
      await operateProcessesMigrationPage.confirmSubButton.click();
      await sleep(2000);
    });

    await test.step('Verify migration operation is created and completes', async () => {
      await operateProcessesPage.waitForOperationToComplete();
    });

    await test.step('Verify 3 instances migrated to target version', async () => {
      await expect(operateProcessesPage.operationSuccessMessage).toBeVisible({
        timeout: 120000,
      });

      await operateProcessesPage.clickLatestOperationLink();

      await validateURL(page, /operationId=/);

      // Expect 3 process instances to be migrated to target version
      await expect(
        operateProcessesPage.getVersionCells(targetVersion),
      ).toHaveCount(3, {timeout: 30000});

      // Expect no process instances for source version
      await expect(
        operateProcessesPage.getVersionCells(sourceVersion),
      ).toBeHidden();
    });

    await test.step('Verify remaining instances still at source version', async () => {
      await operateFiltersPanelPage.clickResetFilters();

      // Expect 3 process instances remaining at source version (out of original 6)
      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);
      await expect(page.getByText(/3 results/i)).toBeVisible({
        timeout: 30000,
      });
    });
  });

  /**
   * Verify migrated date tag is visible on process instance
   */
  test('Verify migrated tag on process instance', async ({
    page,
    operateFiltersPanelPage,
  }) => {
    const targetBpmnProcessId = initialData.processV3.bpmnProcessId;
    const targetVersion = initialData.processV3.version.toString();

    await test.step('Navigate to target process instances and filter', async () => {
      await page.goto('/operate/processes?active=true&incidents=true');
      await sleep(2000);

      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await expect(page.getByText('3 results')).toBeVisible({timeout: 30000});
    });

    await test.step('Open first migrated instance', async () => {
      const firstInstanceLink = page
        .getByRole('link', {name: 'view instance'})
        .first();
      await firstInstanceLink.click();

      await expect(page).toHaveURL(/.*instances\/.*/);
    });

    await test.step('Verify migrated tag is visible on the instance', async () => {
      await expect(page.getByText(/^migrated/i)).toBeVisible({
        timeout: 10000,
      });
    });
  });
});
