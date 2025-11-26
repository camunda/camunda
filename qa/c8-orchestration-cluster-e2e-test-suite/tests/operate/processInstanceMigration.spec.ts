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

const PROCESS_INSTANCE_COUNT = 10;
const AUTO_MIGRATION_INSTANCE_COUNT = 6;
const MANUAL_MIGRATION_INSTANCE_COUNT = 3;

type ProcessDeployment = {
  readonly bpmnProcessId: string;
  readonly version: number;
};

type TestProcesses = {
  readonly processV1: ProcessDeployment;
  readonly processV2: ProcessDeployment;
  readonly processV3: ProcessDeployment;
};

let testProcesses: TestProcesses;

test.beforeAll(async () => {
  await deploy(['./resources/orderProcessMigration_v_1.bpmn']);
  const processV1: ProcessDeployment = {
    bpmnProcessId: 'orderProcessMigration',
    version: 1,
  };

  await Promise.all(
    [...new Array(PROCESS_INSTANCE_COUNT)].map((_, index) =>
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

  testProcesses = {
    processV1,
    processV2,
    processV3,
  };
  await sleep(2000);
});

// eslint-disable-next-line playwright/no-focused-test
test.describe.serial.only('Process Instance Migration', () => {
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
    operateProcessMigrationModePage,
  }) => {
    test.slow();
    const sourceVersion = testProcesses.processV1.version.toString();
    const sourceBpmnProcessId = testProcesses.processV1.bpmnProcessId;
    const targetVersion = testProcesses.processV2.version.toString();
    const targetBpmnProcessId = testProcesses.processV2.bpmnProcessId;

    await test.step('Filter by process name and version', async () => {
      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);

      await expect(page.getByText('results')).toBeVisible({timeout: 30000});
    });

    await test.step('Select first 6 process instances for migration', async () => {
      await operateProcessesPage.selectProcessInstances(
        AUTO_MIGRATION_INSTANCE_COUNT,
      );

      await operateProcessesPage.startMigration();
    });

    await test.step('Verify target process is preselected with auto-mapping and Complete Migration', async () => {
      await expect(
        operateProcessMigrationModePage.targetProcessCombobox,
      ).toHaveValue(targetBpmnProcessId);

      await expect(
        operateProcessMigrationModePage.targetVersionDropdown,
      ).toHaveText(targetVersion, {
        useInnerText: true,
      });

      await expect(
        page.getByLabel('target element for check payment'),
      ).toHaveValue('checkPayment');
      await expect(
        page.getByLabel('target element for ship articles'),
      ).toHaveValue('shipArticles');

      await operateProcessMigrationModePage.completeProcessInstanceMigration();
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

  test('Manual mapping migration', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessMigrationModePage,
  }) => {
    test.slow();
    const sourceVersion = testProcesses.processV2.version.toString();
    const sourceBpmnProcessId = testProcesses.processV2.bpmnProcessId;
    const targetVersion = testProcesses.processV3.version.toString();
    const targetBpmnProcessId = testProcesses.processV3.bpmnProcessId;

    await test.step('Filter by process name and version', async () => {
      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);

      await expect(page.getByText('results')).toBeVisible({timeout: 30000});
    });

    await test.step('Select 3 process instances for migration', async () => {
      await operateProcessesPage.selectProcessInstances(
        MANUAL_MIGRATION_INSTANCE_COUNT,
      );

      await operateProcessesPage.startMigration();
    });

    await test.step('Manually select target process and version', async () => {
      await operateProcessMigrationModePage.targetProcessCombobox.click();
      await page
        .getByRole('option', {name: targetBpmnProcessId, exact: true})
        .click();

      await operateProcessMigrationModePage.targetVersionDropdown.click();
      await page
        .getByRole('option', {name: targetVersion, exact: true})
        .click();
    });

    await test.step('Manually map flow nodes', async () => {
      await operateProcessMigrationModePage.mapFlowNode(
        'Check payment',
        'Ship Articles 2',
      );

      await operateProcessMigrationModePage.mapFlowNode('Task A', 'Task C2');
      await operateProcessMigrationModePage.mapFlowNode('Task B', 'Task B2');
      await operateProcessMigrationModePage.mapFlowNode('Task C', 'Task A2');
      await operateProcessMigrationModePage.mapFlowNode('Task D', 'Task D2');

      await operateProcessMigrationModePage.mapFlowNode(
        'Message interrupting',
        'Message non-interrupting 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer interrupting',
        'Timer interrupting 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Message non-interrupting',
        'Message interrupting 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer non-interrupting',
        'Timer non-interrupting 2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Message intermediate catch',
        'Message intermediate catch 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer intermediate catch',
        'Timer intermediate catch 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Message intermediate catch B',
        'Message intermediate catch B2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer intermediate catch B',
        'Timer intermediate catch B2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Message event sub process',
        'Message event sub process 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer event sub process',
        'Timer event sub process 2',
      );
      await operateProcessMigrationModePage.mapFlowNode('Task E', 'Task E2');
      await operateProcessMigrationModePage.mapFlowNode('Task F', 'Task F2');
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer start event',
        'Timer start event 2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Signal intermediate catch',
        'Signal intermediate catch 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Signal start event',
        'Signal start event',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Signal boundary event',
        'Signal boundary event 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Signal event sub process',
        'Signal event sub process 2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Message receive task',
        'Message receive task 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Business rule task',
        'Business rule task 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Script task',
        'Script task 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Send Task',
        'Send Task 2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Event based gateway',
        'Event based gateway 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Exclusive gateway',
        'Exclusive gateway 2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Multi instance sub process',
        'Multi instance sub process 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Multi instance task',
        'Multi instance task 2',
      );
    });

    await test.step('Proceed to summary and verify migration details', async () => {
      await operateProcessMigrationModePage.nextButton.click();

      const summaryNotification = page.getByRole('main').getByRole('status');
      await expect(summaryNotification).toContainText(
        `You are about to migrate 3 process instances from the process definition: ${sourceBpmnProcessId} - version ${sourceVersion} to the process definition: ${targetBpmnProcessId} - version ${targetVersion}`,
      );
    });

    await test.step('Confirm migration and type MIGRATE', async () => {
      await operateProcessMigrationModePage.clickConfirmButton();
      await operateProcessMigrationModePage.fillMigrationConfirmation(
        'MIGRATE',
      );
      await operateProcessMigrationModePage.clickMigrationConfirmationButton();
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

      await expect(
        operateProcessesPage.getVersionCells(targetVersion),
      ).toHaveCount(3, {timeout: 30000});

      await expect(
        operateProcessesPage.getVersionCells(sourceVersion),
      ).toBeHidden();
    });

    await test.step('Verify remaining instances still at source version', async () => {
      await operateFiltersPanelPage.clickResetFilters();

      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);
      await expect(page.getByText(/3 results/i)).toBeVisible({
        timeout: 30000,
      });
    });
  });

  test('Verify migrated tag on process instance', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessInstancePage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV3.bpmnProcessId;
    const targetVersion = testProcesses.processV3.version.toString();

    await test.step('Navigate to target process instances and apply filter to retrieve processes', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await expect(page.getByText('3 results')).toBeVisible({timeout: 30000});
    });

    await test.step('Open first migrated instance', async () => {
      await operateProcessesPage.clickProcessInstanceLink();
    });

    await test.step('Verify migrated tag is visible on the instance', async () => {
      await expect(operateProcessInstancePage.migratedTag).toBeVisible();
    });
  });
});
