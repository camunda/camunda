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
import {navigateToApp} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';
import {waitForAssertion} from 'utils/waitForAssertion';

type ProcessDeployment = {
  readonly bpmnProcessId: string;
  readonly version: number;
  readonly processDefinitionKey: string;
};

type TestProcesses = {
  readonly parentProcess: ProcessDeployment;
  readonly childProcessV1: ProcessDeployment;
  readonly childProcessV2: ProcessDeployment;
  readonly parentProcessInstanceKeys: string[];
};

let testProcesses: TestProcesses;

test.beforeAll(async () => {
  // Deploy parent and child process v1
  await deploy([
    './resources/callActivityParentProcess.bpmn',
    './resources/childProcess_v_1.bpmn',
  ]);

  // We know the processes will be version 1 on first deployment
  const parentProcess: ProcessDeployment = {
    bpmnProcessId: 'callActivityParentProcess',
    version: 1,
    processDefinitionKey: '', // Will be retrieved from API if needed
  };

  const childProcessV1: ProcessDeployment = {
    bpmnProcessId: 'childProcess',
    version: 1,
    processDefinitionKey: '',
  };

  // Create 2 instances of parent process
  const parentInstances = await createInstances(
    parentProcess.bpmnProcessId,
    parentProcess.version,
    2,
  );

  const parentProcessInstanceKeys = parentInstances.map(
    (instance) => instance.processInstanceKey,
  );

  // Deploy child process v2
  await deploy(['./resources/childProcess_v_2.bpmn']);

  const childProcessV2: ProcessDeployment = {
    bpmnProcessId: 'childProcess',
    version: 2,
    processDefinitionKey: '',
  };

  testProcesses = {
    parentProcess,
    childProcessV1,
    childProcessV2,
    parentProcessInstanceKeys,
  };

  // Wait for instances to be indexed in Operate
  await sleep(2000);
});

test.describe.serial('Child Process Instance Migration', () => {
  test.describe.configure({retries: 0});

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

  test('Migrate Child Process Instances', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessInstancePage,
    operateProcessMigrationModePage,
    operateOperationPanelPage,
  }) => {
    test.slow();

    const {
      parentProcess,
      childProcessV1,
      childProcessV2,
      parentProcessInstanceKeys,
    } = testProcesses;
    const parentInstanceKey = parentProcessInstanceKeys[0]!;

    await test.step('Filter by parent process and version', async () => {
      await operateFiltersPanelPage.selectProcess(parentProcess.bpmnProcessId);
      await operateFiltersPanelPage.selectVersion(
        parentProcess.version.toString(),
      );

      await expect(page.getByText('2 results')).toBeVisible({
        timeout: 30000,
      });
    });

    await test.step('Navigate to parent process instance', async () => {
      await operateProcessesPage
        .processInstanceLinkByKey(parentInstanceKey)
        .click();

      await expect(operateProcessInstancePage.instanceHeader).toBeVisible();
    });

    let childVersion: string;

    await test.step('Navigate to view called child process instances', async () => {
      // Click on "View all" to see called child processes
      await operateProcessInstancePage.instanceHeader
        .getByRole('link', {
          name: 'View all',
        })
        .click();

      // Get child process version
      childVersion = await operateProcessesPage.versionCell.first().innerText();

      // Verify parent instance key is visible (filtering by parent is active)
      await expect(
        operateProcessesPage.dataList.getByRole('cell', {
          name: parentInstanceKey,
        }),
      ).toBeVisible();
    });

    await test.step('Filter by child process and parent instance', async () => {
      await operateFiltersPanelPage.selectProcess(childProcessV1.bpmnProcessId);
      await operateFiltersPanelPage.selectVersion(childVersion);

      await expect(page.getByText('1 result')).toBeVisible({
        timeout: 30000,
      });
    });

    await test.step('Select child process instance for migration', async () => {
      await operateProcessesPage.selectProcessInstances(1);
      await expect(page.getByText('1 item selected')).toBeVisible();

      await expect(operateProcessesPage.migrateButton).toBeEnabled();
    });

    await test.step('Start migration process', async () => {
      await operateProcessesPage.startMigration();
    });

    await test.step('Select target process and version', async () => {
      // Select target process
      await operateProcessMigrationModePage.targetProcessCombobox.click();
      await operateProcessMigrationModePage
        .getOptionByName(childProcessV2.bpmnProcessId)
        .click();

      // Select target version
      await operateProcessMigrationModePage.targetVersionDropdown.click();
      await operateProcessMigrationModePage
        .getOptionByName(childProcessV2.version.toString())
        .click();
    });

    await test.step('Map flow nodes', async () => {
      await operateProcessMigrationModePage.mapFlowNode('Task', 'New Task');
    });

    await test.step('Complete migration', async () => {
      await operateProcessMigrationModePage.nextButton.click();

      await expect(
        operateProcessMigrationModePage.summaryNotification,
      ).toContainText(
        `You are about to migrate 1 process instance from the process definition: ${childProcessV1.bpmnProcessId} - version ${childVersion} to the process definition: ${childProcessV2.bpmnProcessId} - version ${childProcessV2.version}`,
      );

      await operateProcessMigrationModePage.confirmButton.click();
      await operateProcessMigrationModePage.fillMigrationConfirmation(
        'MIGRATE',
      );
      await operateProcessMigrationModePage.clickMigrationConfirmationButton();
    });

    await test.step('Verify migration operation is created', async () => {
      await expect(operateProcessesPage.operationsList).toBeVisible({
        timeout: 30000,
      });

      await operateProcessesPage.expandOperationsPanel();
    });

    await test.step('Wait for migration to complete', async () => {
      await operateProcessesPage.waitForOperationToComplete();
    });

    await test.step('Verify migration completed successfully', async () => {
      const operationEntry =
        operateOperationPanelPage.getMigrationOperationEntry(1);

      await expect(operationEntry).toBeVisible({timeout: 120000});

      await operateOperationPanelPage.clickOperationLink(operationEntry);
    });

    await test.step('Verify process instance migrated to target version', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('1 result')).toBeVisible({
            timeout: 30000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      // Verify instance is at target version
      await expect(
        operateProcessesPage.getVersionCells(childProcessV2.version.toString()),
      ).toHaveCount(1, {timeout: 30000});

      // Verify parent instance key is still associated
      await expect(
        operateProcessesPage.dataList.getByRole('cell', {
          name: parentInstanceKey,
        }),
      ).toBeVisible();
    });

    await test.step('Remove operation filter and verify source version instances', async () => {
      await operateFiltersPanelPage.removeOptionalFilter('Operation Id');

      await operateFiltersPanelPage.selectProcess(childProcessV1.bpmnProcessId);
      await operateFiltersPanelPage.selectVersion(childVersion);

      await expect(page.getByText('1 result')).toBeVisible({
        timeout: 30000,
      });
    });

    await test.step('Collapse operations panel', async () => {
      await operateOperationPanelPage.collapseOperationIdField();
    });
  });
});
