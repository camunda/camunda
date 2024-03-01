/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
    const targetVersion = '2';

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
    await migrationView.selectTargetVersion(targetVersion);

    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'Check payment',
      targetFlowNodeName: 'Ship Articles',
    });

    await migrationView.nextButton.click();

    await expect(migrationView.summaryNotification).toContainText(
      `You are about to migrate 6 process instances from the process definition: ${bpmnProcessId} - version ${version} to the process definition: ${bpmnProcessId} - version ${targetVersion}`,
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
      targetVersion.toString(),
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
        name: targetVersion.toString(),
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
