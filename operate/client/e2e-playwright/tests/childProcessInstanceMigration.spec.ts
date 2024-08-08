/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './childProcessInstanceMigration.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {SETUP_WAITING_TIME} from './constants';
import {config} from '../config';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  const {parentProcessDefinitionKey} = initialData;

  await expect
    .poll(
      async () => {
        const response = await request.post(
          `${config.endpoint}/v1/process-instances/search`,
          {
            data: {
              filter: {
                processDefinitionKey: parentProcessDefinitionKey,
              },
            },
          },
        );

        return await response.json();
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toHaveProperty('total', 2);
});

test.beforeEach(async ({processesPage}) => {
  await processesPage.navigateToProcesses({searchParams: {active: 'true'}});
});

test.describe('Child Process Instance Migration @roundtrip', () => {
  test('Migrate Child Process Instances', async ({
    processesPage,
    processesPage: {filtersPanel},
    processInstancePage,
    migrationView,
    commonPage,
    page,
  }) => {
    test.slow();
    const {
      parentBpmnProcessId,
      parentVersion,
      processInstances,
      childBpmnProcessId,
    } = initialData;
    const targetVersion = '1';

    // Get parent process instance key
    const parentInstanceKey = processInstances[0]!.processInstanceKey;

    // Select parent process and version on filters
    await filtersPanel.selectProcess(parentBpmnProcessId);
    await filtersPanel.selectVersion(parentVersion.toString());

    // Should have 2 instances of the parent call activity process
    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/2 results/i);

    // Navigate to parent process instance page
    await processesPage.processInstancesTable
      .getByRole('link', {
        name: parentInstanceKey,
      })
      .click();

    // Click on button to see called elements (child processes) in process instance list
    await processInstancePage.instanceHeader
      .getByRole('link', {
        name: 'View all',
      })
      .click();

    // Get child process version that was called by parent process instance
    const childVersion = await processesPage.processInstancesTable
      .getByTestId('cell-processVersion')
      .innerText();

    // Check if parent process instance key is set (filtering by parent instance key is on here)
    await expect(
      processesPage.processInstancesTable.getByRole('cell', {
        name: parentInstanceKey,
      }),
    ).toBeVisible();

    // Select child process and version before going into migration
    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        process: childBpmnProcessId,
        version: childVersion,
        parentInstanceId: parentInstanceKey,
      },
    });

    await expect(
      processesPage.processInstancesTable.getByText(/1 result/i),
    ).toBeVisible();

    // Select first child process
    await processesPage.getNthProcessInstanceCheckbox(0).click();
    await expect(page.getByText(/1 item selected/i)).toBeVisible();

    // Check if migrate button is enabled
    await expect(processesPage.migrateButton).toBeEnabled();

    // Go into process instance migration
    await processesPage.migrateButton.click();
    await processesPage.migrationModal.confirmButton.click();

    // Select target process, version and map flow nodes
    await migrationView.selectTargetProcess('childProcess');
    await migrationView.selectTargetVersion(targetVersion);
    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'New Task',
      targetFlowNodeName: 'Task',
    });

    // Confirm and finish migration
    await migrationView.nextButton.click();
    await expect(migrationView.summaryNotification).toContainText(
      `You are about to migrate 1 process instance from the process definition: ${childBpmnProcessId} - version ${childVersion} to the process definition: ${childBpmnProcessId} - version ${targetVersion}`,
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
      childBpmnProcessId,
    );

    expect(await filtersPanel.processVersionFilter.innerText()).toBe(
      targetVersion.toString(),
    );

    await migrateOperationEntry.getByRole('link').click();

    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/1 result/i);

    // expect 1 process instance to be migrated to target version
    await expect(
      processesPage.processInstancesTable.getByRole('cell', {
        name: targetVersion,
        exact: true,
      }),
    ).toHaveCount(1);

    // expect parent process instance key to match initial parent process id
    await expect(
      processesPage.processInstancesTable.getByRole('cell', {
        name: parentInstanceKey,
      }),
    ).toBeVisible();

    // expect no process instances for source version
    await expect(
      processesPage.processInstancesTable.getByRole('cell', {
        name: childVersion,
        exact: true,
      }),
    ).not.toBeVisible();

    await filtersPanel.removeOptionalFilter('Operation Id');

    // expect 1 process instances for source version
    await filtersPanel.selectProcess(childBpmnProcessId);
    await filtersPanel.selectVersion(childVersion);
    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/1 result/i);

    await commonPage.collapseOperationsPanel();
  });
});
