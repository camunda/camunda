/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {setup} from './processInstanceMigration.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {SETUP_WAITING_TIME} from './constants';
import {config} from '../config';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  const {processDefinitionKey} = initialData;

  await expect
    .poll(
      async () => {
        const response = await request.post(
          `${config.endpoint}/v1/process-instances/search`,
          {
            data: {
              filter: {
                processDefinitionKey,
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

test.beforeEach(async ({processesPage}) => {
  await processesPage.navigateToProcesses({searchParams: {active: 'true'}});
});

test.describe('Process Instance Migration', () => {
  test('Migrate Process Instances', async ({
    processesPage,
    migrationView,
    commonPage,
  }) => {
    const {bpmnProcessId, version} = initialData;

    await processesPage.selectProcess(bpmnProcessId);
    await processesPage.selectVersion(version.toString());

    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/10 results/i);

    // select 6 process instances for migration
    await processesPage.getNthProcessInstanceCheckbox(0).click();
    await processesPage.getNthProcessInstanceCheckbox(1).click();
    await processesPage.getNthProcessInstanceCheckbox(2).click();
    await processesPage.getNthProcessInstanceCheckbox(3).click();
    await processesPage.getNthProcessInstanceCheckbox(4).click();
    await processesPage.getNthProcessInstanceCheckbox(5).click();

    await processesPage.migrateButton.click();
    await processesPage.migrationModal.confirmButton.click();

    await migrationView.selectTargetProcess('orderProcessMigration');

    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'Check payment',
      targetFlowNodeName: 'Ship Articles',
    });

    await migrationView.nextButton.click();

    await expect(migrationView.summaryNotification).toContainText(
      `You are about to migrate 6 process instances from the process definition: ${bpmnProcessId} - version ${version} to the process definition: ${bpmnProcessId} - version ${
        version + 1
      }`,
    );

    await migrationView.confirmButton.click();

    await expect(commonPage.operationsList).toBeVisible();

    const migrateOperationEntry = commonPage.operationsList
      .getByRole('listitem')
      .first();

    await expect(migrateOperationEntry).toContainText('Migrate');

    await expect(migrateOperationEntry.getByRole('progressbar')).toBeVisible();

    // wait for migrate operation to finish
    await expect(
      migrateOperationEntry.getByRole('progressbar'),
    ).not.toBeVisible();

    await expect(processesPage.processNameFilter).toHaveValue(bpmnProcessId);
    expect(await processesPage.processVersionFilter.innerText()).toBe(
      (version + 1).toString(),
    );

    await migrateOperationEntry
      .getByRole('link', {name: /6 instances/i})
      .click();

    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/6 results/i);

    // expect 6 process instances to be migrated to target version
    await expect(
      processesPage.processInstancesTable.getByRole('cell', {
        name: (version + 1).toString(),
        exact: true,
      }),
    ).toHaveCount(6);

    // expect no process instances for source version
    await expect(
      processesPage.processInstancesTable.getByRole('cell', {
        name: version.toString(),
        exact: true,
      }),
    ).not.toBeVisible();

    await processesPage.removeOptionalFilter('Operation Id');

    // expect 4 process instances for source version
    await processesPage.selectProcess(bpmnProcessId);
    await processesPage.selectVersion(version.toString());
    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/4 results/i);

    await commonPage.collapseOperationsPanel();
  });
});
