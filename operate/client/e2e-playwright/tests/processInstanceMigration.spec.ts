/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './processInstanceMigration.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {SETUP_WAITING_TIME} from './constants';
import {config} from '../config';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();

  await expect
    .poll(
      async () => {
        const response = await request.post(
          `${config.endpoint}/v1/process-instances/search`,
          {
            data: {
              filter: {
                processDefinitionKey:
                  initialData.processV1.processDefinitionKey,
              },
            },
          },
        );

        return await response.json();
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toHaveProperty('total', 10);
});

test.describe.serial('Process Instance Migration', () => {
  /**
   * Migrate from ProcessV1 to ProcessV2
   * ProcessV1 and ProcessV2 have identical bpmnProcess id and flow node names,
   * so the target process will be preselected and flow nodes will be auto-mapped
   */
  test('Auto Mapping @roundtrip', async ({
    processesPage,
    processesPage: {filtersPanel},
    migrationView,
    commonPage,
    page,
  }) => {
    test.slow();

    const sourceVersion = initialData.processV1.version.toString();
    const sourceBpmnProcessId = initialData.processV1.bpmnProcessId;
    const targetVersion = initialData.processV2.version.toString();
    const targetBpmnProcessId = initialData.processV2.bpmnProcessId;

    await processesPage.navigateToProcesses({searchParams: {active: 'true'}});

    await filtersPanel.selectProcess(sourceBpmnProcessId);
    await filtersPanel.selectVersion(sourceVersion);

    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/results/i);

    // select 6 process instances for migration
    await processesPage.getNthProcessInstanceCheckbox(0).click();
    await processesPage.getNthProcessInstanceCheckbox(1).click();
    await processesPage.getNthProcessInstanceCheckbox(2).click();
    await processesPage.getNthProcessInstanceCheckbox(3).click();
    await processesPage.getNthProcessInstanceCheckbox(4).click();
    await processesPage.getNthProcessInstanceCheckbox(5).click();

    await processesPage.migrateButton.click();
    await processesPage.migrationModal.confirmButton.click();

    // Expect auto mapping for each flow node
    await expect(page.getByLabel(/target flow node for/i)).toHaveCount(3);
    await expect(
      page.getByLabel(/target flow node for check payment/i),
    ).toHaveValue('checkPayment');
    await expect(
      page.getByLabel(/target flow node for ship articles/i),
    ).toHaveValue('shipArticles');
    await expect(
      page.getByLabel(/target flow node for request for payment/i),
    ).toHaveValue('requestForPayment');

    // Expect pre-selected process and version
    await expect(migrationView.targetProcessComboBox).toHaveValue(
      targetBpmnProcessId,
    );
    await expect(migrationView.targetVersionDropdown).toHaveText(
      targetVersion,
      {useInnerText: true},
    );

    await migrationView.nextButton.click();

    await expect(migrationView.summaryNotification).toContainText(
      `You are about to migrate 6 process instances from the process definition: ${sourceBpmnProcessId} - version ${sourceVersion} to the process definition: ${targetBpmnProcessId} - version ${targetVersion}`,
    );

    await migrationView.confirmButton.click();
    await migrationView.confirmMigration();

    await expect(commonPage.operationsList).toBeVisible();

    const migrateOperationEntry = commonPage.operationsList
      .getByRole('listitem')
      .first();
    await expect(migrateOperationEntry).toContainText('Migrate');
    await expect(migrateOperationEntry.getByRole('progressbar')).toBeVisible();

    // wait for migrate operation to finish
    await expect(
      migrateOperationEntry.getByRole('progressbar'),
    ).not.toBeVisible({timeout: 60000});

    await expect(filtersPanel.processNameFilter).toHaveValue(
      targetBpmnProcessId,
    );
    expect(await filtersPanel.processVersionFilter.innerText()).toBe(
      targetVersion,
    );

    await migrateOperationEntry.getByRole('link').click();

    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/6 results/i);

    // expect 6 process instances to be migrated to target version
    await expect(
      processesPage.processInstancesTable.getByRole('cell', {
        name: targetVersion,
        exact: true,
      }),
    ).toHaveCount(6);

    // expect no process instances for source version
    await expect(
      processesPage.processInstancesTable.getByRole('cell', {
        name: sourceVersion,
        exact: true,
      }),
    ).not.toBeVisible();

    await filtersPanel.removeOptionalFilter('Operation Id');

    // expect 4 process instances for source version
    await filtersPanel.selectProcess(sourceBpmnProcessId);
    await filtersPanel.selectVersion(sourceVersion);
    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/4 results/i);

    await commonPage.collapseOperationsPanel();
  });

  /**
   * Migrate from ProcessV2 to ProcessV3
   * ProcessV3 has a different bpmn process id and different flow node names,
   * so no flow node auto-mapping or pre-selected processes are available.
   */
  test('Manual Mapping @roundtrip', async ({
    processesPage,
    processesPage: {filtersPanel},
    migrationView,
    commonPage,
  }) => {
    test.slow();

    const sourceVersion = initialData.processV2.version.toString();
    const sourceBpmnProcessId = initialData.processV2.bpmnProcessId;
    const targetVersion = initialData.processV3.version.toString();
    const targetBpmnProcessId = initialData.processV3.bpmnProcessId;

    await processesPage.navigateToProcesses({searchParams: {active: 'true'}});

    await filtersPanel.selectProcess(sourceBpmnProcessId);
    await filtersPanel.selectVersion(sourceVersion);

    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/results/i);

    // select 3 process instances for migration
    await processesPage.getNthProcessInstanceCheckbox(0).click();
    await processesPage.getNthProcessInstanceCheckbox(1).click();
    await processesPage.getNthProcessInstanceCheckbox(2).click();

    await processesPage.migrateButton.click();
    await processesPage.migrationModal.confirmButton.click();

    await migrationView.selectTargetProcess(targetBpmnProcessId);
    await migrationView.selectTargetVersion(targetVersion);

    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'Check payment',
      targetFlowNodeName: 'Ship Articles 2',
    });

    await migrationView.nextButton.click();

    await expect(migrationView.summaryNotification).toContainText(
      `You are about to migrate 3 process instances from the process definition: ${sourceBpmnProcessId} - version ${sourceVersion} to the process definition: ${targetBpmnProcessId} - version ${targetVersion}`,
    );

    await migrationView.confirmButton.click();
    await migrationView.confirmMigration();

    await expect(commonPage.operationsList).toBeVisible();

    const migrateOperationEntry = commonPage.operationsList
      .getByRole('listitem')
      .first();

    await expect(migrateOperationEntry).toContainText('Migrate');
    await expect(migrateOperationEntry.getByRole('progressbar')).toBeVisible();

    // wait for migrate operation to finish
    await expect(
      migrateOperationEntry.getByRole('progressbar'),
    ).not.toBeVisible({timeout: 60000});

    await expect(filtersPanel.processNameFilter).toHaveValue(
      targetBpmnProcessId,
    );
    expect(await filtersPanel.processVersionFilter.innerText()).toBe(
      targetVersion,
    );

    await migrateOperationEntry.getByRole('link').click();

    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/3 results/i);

    // expect 3 process instances to be migrated to target version
    await expect(
      processesPage.processInstancesTable.getByRole('cell', {
        name: targetVersion,
        exact: true,
      }),
    ).toHaveCount(3);

    // expect no process instances for source version
    await expect(
      processesPage.processInstancesTable.getByRole('cell', {
        name: sourceVersion,
        exact: true,
      }),
    ).not.toBeVisible();

    await filtersPanel.removeOptionalFilter('Operation Id');

    // expect 3 process instances for source version
    await filtersPanel.selectProcess(sourceBpmnProcessId);
    await filtersPanel.selectVersion(sourceVersion);
    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/3 results/i);

    await commonPage.collapseOperationsPanel();
  });

  test('Migrated date tag', async ({processesPage, processInstancePage}) => {
    const targetBpmnProcessId = initialData.processV3.bpmnProcessId;
    const targetVersion = initialData.processV3.version.toString();

    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        process: targetBpmnProcessId,
        version: targetVersion,
      },
    });

    // Navigate to the first process instance in the list, that has been migrated
    await processesPage.processInstancesTable
      .getByRole('link', {
        name: /^view instance/i,
      })
      .first()
      .click();

    // Expect the migrated tag to be visible
    await expect(
      processInstancePage.instanceHistory.getByText(/^migrated/i),
    ).toBeVisible();
  });
});
