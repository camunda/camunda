/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
    await processesPage.selectProcess(parentBpmnProcessId);
    await processesPage.selectVersion(parentVersion.toString());

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

    await expect(processesPage.processNameFilter).toHaveValue(
      childBpmnProcessId,
    );

    expect(await processesPage.processVersionFilter.innerText()).toBe(
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

    await processesPage.removeOptionalFilter('Operation Id');

    // expect 1 process instances for source version
    await processesPage.selectProcess(childBpmnProcessId);
    await processesPage.selectVersion(childVersion);
    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/1 result/i);

    await commonPage.collapseOperationsPanel();
  });
});
