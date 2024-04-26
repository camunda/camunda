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

import {setup} from './batchMoveModification.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {SETUP_WAITING_TIME} from './constants';
import {config} from '../config';
import {IS_BATCH_MOVE_MODIFICATION_ENABLED} from 'modules/feature-flags';

let initialData: Awaited<ReturnType<typeof setup>>;

const NUM_PROCESS_INSTANCES = 10;
const NUM_SELECTED_PROCESS_INSTANCES = 4;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  const {processInstances} = initialData;

  // wait until all instances are created
  await Promise.all(
    processInstances.map(
      async (instance) =>
        await expect
          .poll(
            async () => {
              const response = await request.get(
                `${config.endpoint}/v1/process-instances/${instance.processInstanceKey}`,
              );
              return response.status();
            },
            {timeout: SETUP_WAITING_TIME},
          )
          .toBe(200),
    ),
  );
});

(IS_BATCH_MOVE_MODIFICATION_ENABLED ? test.describe : test.describe.skip)(
  'Process Instance Batch Modification',
  () => {
    test('Move Operation', async ({processesPage, commonPage, page}) => {
      await processesPage.navigateToProcesses({
        searchParams: {active: 'true'},
      });

      const processInstanceKeys = initialData.processInstances
        .map((instance) => instance.processInstanceKey)
        .join(',');

      await processesPage.selectProcess('Order process');
      await processesPage.selectVersion(initialData.version.toString());
      await processesPage.selectFlowNode('Check payment');

      // Filter by all process instances which have been created in setup
      await processesPage.displayOptionalFilter('Process Instance Key(s)');
      await processesPage.processInstanceKeysFilter.fill(processInstanceKeys);

      await expect(
        processesPage.processInstancesTable.getByText(
          `${NUM_PROCESS_INSTANCES} results`,
        ),
      ).toBeVisible();

      // Select 4 process instances for move modification
      await processesPage.getNthProcessInstanceCheckbox(0).click();
      await processesPage.getNthProcessInstanceCheckbox(1).click();
      await processesPage.getNthProcessInstanceCheckbox(2).click();
      await processesPage.getNthProcessInstanceCheckbox(3).click();

      await expect(
        page.getByText(`${NUM_SELECTED_PROCESS_INSTANCES} items selected`),
      ).toBeVisible();

      await processesPage.moveButton.click();

      // Confirm move modification modal
      await page.getByRole('button', {name: 'Continue'}).click();

      // Select target flow node
      await processesPage.diagram.clickFlowNode('Ship Articles');

      const notificationText = `Modification scheduled: Move ${NUM_SELECTED_PROCESS_INSTANCES} instances from “Check payment” to “Ship Articles”. Press “Apply Modification” button to confirm.`;
      await expect(page.getByText(notificationText)).toBeVisible();

      // Check that Undo button is working
      await page.getByRole('button', {name: /undo/i}).click();
      await expect(page.getByText(notificationText)).not.toBeVisible();

      // Select target flow node
      await processesPage.diagram.clickFlowNode('Ship Articles');

      await expect(page.getByText(notificationText)).toBeVisible();

      await page.getByRole('button', {name: /apply modification/i}).click();

      // Expect that modal is open with "apply modifications" title
      await expect(
        page.getByRole('heading', {name: /apply modifications/i}),
      ).toBeVisible();

      // Confirm modal
      await page.getByRole('button', {name: /^apply$/i}).click();

      // Expect Operations Panel to be visible
      await expect(commonPage.operationsList).toBeVisible();

      const modificationOperationEntry = commonPage.operationsList
        .getByRole('listitem')
        .first();
      await expect(modificationOperationEntry).toContainText('Modify');
      await expect(
        modificationOperationEntry.getByRole('progressbar'),
      ).toBeVisible();

      // Wait for migrate operation to finish
      await expect(
        modificationOperationEntry.getByRole('progressbar'),
      ).not.toBeVisible();

      await modificationOperationEntry
        .getByRole('link', {
          name: `${NUM_SELECTED_PROCESS_INSTANCES} Instances`,
        })
        .click();

      await processesPage.selectProcess('Order process');
      await processesPage.selectVersion(initialData.version.toString());

      // Filter by all process instances which have been created in setup
      await processesPage.displayOptionalFilter('Process Instance Key(s)');
      await processesPage.processInstanceKeysFilter.fill(processInstanceKeys);

      // Expect the correct number of instances related to the move modification
      await expect(
        processesPage.processInstancesTable.getByText(
          `${NUM_SELECTED_PROCESS_INSTANCES} results`,
        ),
      ).toBeVisible();

      // Expect that shipArticles flow node instances got canceled in all process instances
      await expect(
        processesPage.diagram.diagram.getByTestId(
          'state-overlay-shipArticles-active',
        ),
      ).toHaveText(NUM_SELECTED_PROCESS_INSTANCES.toString());

      // Expect that flow node instances have been created on checkPayment in all process instances
      await expect(
        processesPage.diagram.diagram.getByTestId(
          'state-overlay-checkPayment-canceled',
        ),
      ).toHaveText(NUM_SELECTED_PROCESS_INSTANCES.toString());
    });

    test('Exit Modal', async ({processesPage, page}) => {
      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          process: 'orderProcess',
          version: initialData.version.toString(),
          flowNodeId: 'checkPayment',
        },
      });

      // Select instance for move modification
      await processesPage.getNthProcessInstanceCheckbox(0).click();

      // Enter batch modification mode
      await processesPage.moveButton.click();

      // Confirm move modification modal
      await page.getByRole('button', {name: 'Continue'}).click();

      // Select target flow node
      await processesPage.diagram.clickFlowNode('Ship Articles');

      // Try to navigate to Dashboard page
      await page.getByRole('link', {name: 'Dashboard'}).click();

      // Expect navigation to be interrupted and modal to be shown
      const exitModal = page.getByRole('dialog', {
        name: /exit batch modification mode/i,
      });
      await expect(exitModal).toBeVisible();
      await expect(exitModal).toContainText(
        /about to discard all added modifications/i,
      );

      // Cancel Modal
      await exitModal.getByRole('button', {name: /cancel/i}).click();

      // Expect to be still in modification mode
      await expect(exitModal).not.toBeVisible();
      await expect(page.getByText(/batch modification mode/i)).toBeVisible();

      // Try to navigate to Dashboard page
      await page.getByRole('link', {name: 'Dashboard'}).click();

      // Confirm Exit
      await exitModal.getByRole('button', {name: /exit/i}).click();

      // Expect not to be in move modification mode
      await expect(
        page.getByText(/batch modification mode/i),
      ).not.toBeVisible();

      // Expect to be on Dashboard page
      await expect(
        page.getByText(/running process instances in total/i),
      ).toBeVisible();

      await page.goBack();

      // Expect not to be in move modification mode
      await expect(
        page.getByText(/batch modification mode/i),
      ).not.toBeVisible();
    });
  },
);
