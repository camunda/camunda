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

// BPMN resource paths
const BPMN_V1_PATH = './resources/orderProcessMigration_v_1.bpmn';
const BPMN_V2_PATH = './resources/orderProcessMigration_v_2.bpmn';
const BPMN_V3_PATH = './resources/orderProcessMigration_v_3.bpmn';

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
  // Deploy version 1
  await deploy([BPMN_V1_PATH]);

  // Extract process definition information
  // Version will be auto-incremented 1, 2, 3 as we deploy
  const processV1: ProcessDeployment = {
    bpmnProcessId: 'orderProcessMigration',
    version: 1,
  };

  // Create process instances for v1
  await createInstances(processV1.bpmnProcessId, processV1.version, 10, {
    key1: 'myFirstCorrelationKey',
    key2: 'mySecondCorrelationKey',
  });

  // Deploy version 2
  await deploy([BPMN_V2_PATH]);

  const processV2: ProcessDeployment = {
    bpmnProcessId: 'orderProcessMigration',
    version: 2,
  };

  // Deploy version 3
  await deploy([BPMN_V3_PATH]);

  const processV3: ProcessDeployment = {
    bpmnProcessId: 'orderProcessMigration_v3',
    version: 3,
  };

  initialData = {
    processV1,
    processV2,
    processV3,
  };
});

test.describe('Process Instance Migration', () => {
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

  test('Auto mapping migration from V1 to V2', async ({
    page,
    operateFiltersPanelPage,
  }) => {
    test.slow();

    const sourceVersion = initialData.processV1.version.toString();
    const sourceBpmnProcessId = initialData.processV1.bpmnProcessId;
    const targetVersion = initialData.processV2.version.toString();
    const targetBpmnProcessId = initialData.processV2.bpmnProcessId;

    await test.step('Navigate to processes page and filter by process name and version', async () => {
      await page.goto('/operate/processes?active=true&incidents=true');
      await sleep(2000);

      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);

      await expect(page.getByText('results')).toBeVisible({timeout: 30000});
    });

    await test.step('Select first 6 process instances for migration', async () => {
      const processInstancesTable = page.getByRole('region', {
        name: 'process instances panel',
      });

      for (let i = 0; i < 6; i++) {
        await processInstancesTable
          .getByRole('row', {name: 'select row'})
          .nth(i)
          .locator('label')
          .click();
        await sleep(100);
      }

      const migrateButton = processInstancesTable.getByRole('button', {
        name: /^migrate$/i,
      });
      await expect(migrateButton).toBeEnabled({timeout: 10000});
      await migrateButton.click();

      const migrationModal = page.getByRole('dialog', {name: 'migrate'});
      await expect(migrationModal).toBeVisible();
      await migrationModal.getByRole('button', {name: 'confirm'}).click();

      await expect(page).toHaveURL(/.*migrate.*/);
    });

    await test.step('Verify target process is preselected with auto-mapping', async () => {
      const targetProcessCombobox = page.getByRole('combobox', {
        name: 'Target',
        exact: true,
      });
      await expect(targetProcessCombobox).toHaveValue(targetBpmnProcessId);

      const targetVersionDropdown = page.getByRole('combobox', {
        name: 'Target Version',
      });
      await expect(targetVersionDropdown).toHaveText(targetVersion, {
        useInnerText: true,
      });

      await expect(
        page.getByLabel('target element for check payment'),
      ).toHaveValue('checkPayment');
      await expect(
        page.getByLabel('target element for ship articles'),
      ).toHaveValue('shipArticles');
    });

    await test.step('Proceed to summary and verify migration details', async () => {
      const nextButton = page.getByRole('button', {name: 'next'});
      await nextButton.click();

      const summaryNotification = page.getByRole('main').getByRole('status');
      await expect(summaryNotification).toContainText(
        `You are about to migrate 6 process instances from the process definition: ${sourceBpmnProcessId} - version ${sourceVersion} to the process definition: ${targetBpmnProcessId} - version ${targetVersion}`,
      );
    });

    await test.step('Confirm migration and type MIGRATE', async () => {
      const confirmButton = page.getByRole('button', {name: 'confirm'});
      await confirmButton.click();

      const migrationConfirmationModal = page.getByRole('dialog', {
        name: 'migration confirmation',
      });
      await expect(migrationConfirmationModal).toBeVisible();
      await migrationConfirmationModal.getByRole('textbox').fill('MIGRATE');
      await migrationConfirmationModal
        .getByRole('button', {name: 'confirm'})
        .click();

      await sleep(2000);
    });

    await test.step('Verify migration operation is created and completes', async () => {
      const operationsPanel = page.getByRole('complementary', {
        name: 'operations panel',
      });
      await expect(operationsPanel).toBeVisible({timeout: 10000});

      const operationsList = operationsPanel.getByRole('list');
      const migrateOperationEntry = operationsList
        .getByRole('listitem')
        .first();

      await expect(migrateOperationEntry).toContainText('Migrate', {
        timeout: 10000,
      });

      await expect(
        migrateOperationEntry.getByRole('progressbar'),
      ).not.toBeVisible({timeout: 120000});

      await expect(
        page
          .getByRole('combobox', {name: 'name'})
          .filter({hasText: targetBpmnProcessId}),
      ).toBeVisible({timeout: 10000});
    });

    await test.step('Verify 6 instances migrated to target version', async () => {
      const operationsPanel = page.getByRole('complementary', {
        name: 'operations panel',
      });
      const operationsList = operationsPanel.getByRole('list');
      const migrateOperationEntry = operationsList
        .getByRole('listitem')
        .first();

      await migrateOperationEntry.getByRole('link').click();
      await sleep(2000);

      await expect(page.getByText('6 results')).toBeVisible({
        timeout: 30000,
      });

      const dataList = page.getByTestId('data-list');
      await expect(
        dataList.getByRole('cell', {name: targetVersion, exact: true}),
      ).toHaveCount(6, {timeout: 30000});
    });
  });

  /**
   * Test manual mapping migration from ProcessV2 to ProcessV3
   * ProcessV3 has different bpmn process id and flow node names,
   * so manual mapping is required
   */
  test('Manual mapping migration from V2 to V3', async ({
    page,
    operateFiltersPanelPage,
  }) => {
    test.slow();

    const sourceVersion = initialData.processV2.version.toString();
    const sourceBpmnProcessId = initialData.processV2.bpmnProcessId;
    const targetVersion = initialData.processV3.version.toString();
    const targetBpmnProcessId = initialData.processV3.bpmnProcessId;

    await test.step('Navigate to processes page and filter by process name and version', async () => {
      await page.goto('/operate/processes?active=true&incidents=true');
      await sleep(2000);

      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);

      await expect(page.getByText('results')).toBeVisible({timeout: 30000});
    });

    await test.step('Select 3 process instances for migration', async () => {
      const processInstancesTable = page.getByRole('region', {
        name: 'process instances panel',
      });

      for (let i = 0; i < 3; i++) {
        await processInstancesTable
          .getByRole('row', {name: 'select row'})
          .nth(i)
          .locator('label')
          .click();
        await sleep(100);
      }
    });

    await test.step('Start migration and confirm', async () => {
      const processInstancesTable = page.getByRole('region', {
        name: 'process instances panel',
      });

      const migrateButton = processInstancesTable.getByRole('button', {
        name: 'migrate',
      });
      await expect(migrateButton).toBeEnabled({timeout: 10000});
      await migrateButton.click();

      const migrationModal = page.getByRole('dialog', {name: 'migrate'});
      await migrationModal.getByRole('button', {name: 'confirm'}).click();

      await expect(page).toHaveURL(/.*migrate.*/);
    });

    await test.step('Manually select target process and version', async () => {
      const targetProcessCombobox = page.getByRole('combobox', {
        name: 'Target',
        exact: true,
      });
      await targetProcessCombobox.click();
      await page
        .getByRole('option', {name: targetBpmnProcessId, exact: true})
        .click();

      const targetVersionDropdown = page.getByRole('combobox', {
        name: 'Target Version',
      });
      await targetVersionDropdown.click();
      await page
        .getByRole('option', {name: targetVersion, exact: true})
        .click();
    });

    await test.step('Manually map flow nodes', async () => {
      await page
        .getByLabel('Target element for Check payment', {exact: true})
        .selectOption('Ship Articles 2');

      await page
        .getByLabel('Target element for Task A', {exact: true})
        .selectOption('Task C2');
    });

    await test.step('Proceed to summary and verify migration details', async () => {
      await page.getByRole('button', {name: 'next'}).click();

      const summaryNotification = page.getByRole('main').getByRole('status');
      await expect(summaryNotification).toContainText(
        `You are about to migrate 3 process instances from the process definition: ${sourceBpmnProcessId} - version ${sourceVersion} to the process definition: ${targetBpmnProcessId} - version ${targetVersion}`,
      );
    });

    await test.step('Confirm migration and type MIGRATE', async () => {
      await page.getByRole('button', {name: 'confirm'}).click();

      const migrationConfirmationModal = page.getByRole('dialog', {
        name: 'migration confirmation',
      });
      await migrationConfirmationModal.getByRole('textbox').fill('MIGRATE');
      await migrationConfirmationModal
        .getByRole('button', {name: 'confirm'})
        .click();

      await sleep(2000);
    });

    await test.step('Verify migration operation completes', async () => {
      const operationsPanel = page.getByRole('complementary', {
        name: 'operations panel',
      });
      const operationsList = operationsPanel.getByRole('list');
      const migrateOperationEntry = operationsList
        .getByRole('listitem')
        .first();

      await expect(migrateOperationEntry).toContainText('Migrate', {
        timeout: 10000,
      });

      await expect(
        migrateOperationEntry.getByRole('progressbar'),
      ).not.toBeVisible({timeout: 120000});
    });

    await test.step('Verify 3 instances migrated to target version', async () => {
      const operationsPanel = page.getByRole('complementary', {
        name: 'operations panel',
      });
      const operationsList = operationsPanel.getByRole('list');
      const migrateOperationEntry = operationsList
        .getByRole('listitem')
        .first();

      await migrateOperationEntry.getByRole('link').click();
      await sleep(2000);

      await expect(page.getByText(/3 results/i)).toBeVisible({
        timeout: 30000,
      });

      const dataList = page.getByTestId('data-list');
      await expect(
        dataList.getByRole('cell', {name: targetVersion, exact: true}),
      ).toHaveCount(3, {timeout: 30000});
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
