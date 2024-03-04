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

import {setup} from './processInstancesFilters.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {SETUP_WAITING_TIME} from './constants';
import {config} from '../config';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  test.setTimeout(SETUP_WAITING_TIME);

  await expect
    .poll(
      async () => {
        const response = await request.get(
          `${config.endpoint}/v1/process-instances/${initialData.callActivityProcessInstance.processInstanceKey}`,
        );

        return response.status();
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBe(200);
});

test.beforeEach(async ({page, dashboardPage}) => {
  await dashboardPage.navigateToDashboard();
  await page.getByRole('link', {name: /processes/i}).click();
});

test.describe('Process Instances Filters', () => {
  test('Apply Filters', async ({page, processesPage}) => {
    const callActivityProcessInstanceKey =
      initialData.callActivityProcessInstance.processInstanceKey;

    await processesPage.displayOptionalFilter('Parent Process Instance Key');
    await processesPage.parentProcessInstanceKey.type(
      callActivityProcessInstanceKey,
    );
    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).toBeVisible();

    await page.locator('label').filter({hasText: 'Completed'}).click();
    await expect(page.getByText('1 result')).toBeVisible();

    // result is the one we filtered
    await expect(
      await page
        .getByTestId('data-list')
        .getByTestId('cell-parentInstanceId')
        .innerText(),
    ).toBe(callActivityProcessInstanceKey);

    const endDate = await page
      .getByTestId('data-list')
      .getByTestId('cell-endDate')
      .innerText();

    const day = new Date(endDate).getDate();

    const allRows = page.getByTestId('data-list').getByRole('row');
    const rowCount = allRows.count();

    await expect(await rowCount).toBe(1);

    await processesPage.resetFiltersButton.click();
    await expect(processesPage.parentProcessInstanceKey).not.toBeVisible();
    await expect.poll(() => allRows.count()).toBeGreaterThan(1);

    await page.locator('label').filter({hasText: 'Completed'}).click();

    let currentRowCount = await allRows.count();
    await processesPage.displayOptionalFilter('End Date Range');
    await processesPage.pickDateTimeRange({
      fromDay: '1',
      toDay: `${day}`,
    });
    await page.getByText('Apply').click();
    await expect.poll(() => allRows.count()).toBeLessThan(currentRowCount);

    currentRowCount = await allRows.count();
    await processesPage.resetFiltersButton.click();
    await expect.poll(() => allRows.count()).toBeGreaterThan(currentRowCount);

    currentRowCount = await allRows.count();
    await processesPage.displayOptionalFilter('Error Message');
    await processesPage.errorMessageFilter.type(
      "failed to evaluate expression 'nonExistingClientId': no variable found for name 'nonExistingClientId'",
    );

    await expect.poll(() => allRows.count()).toBeLessThan(currentRowCount);

    await processesPage.displayOptionalFilter('Start Date Range');
    await processesPage.pickDateTimeRange({
      fromDay: '1',
      toDay: '1',
      fromTime: '00:00:00',
      toTime: '00:00:00',
    });
    await page.getByText('Apply').click();
    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).toBeVisible();

    await processesPage.resetFiltersButton.click();
    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).not.toBeVisible();

    await expect(processesPage.errorMessageFilter).not.toBeVisible();
    await expect(processesPage.startDateFilter).not.toBeVisible();
  });

  test('Interaction between diagram and filters', async ({
    page,
    processesPage,
  }) => {
    await processesPage.selectProcess('Process With Multiple Versions');

    await expect(await processesPage.processVersionFilter.innerText()).toBe(
      '2',
    );

    // change version and see flow node filter has been reset
    await processesPage.selectVersion('1');
    await expect(processesPage.flowNodeFilter).toHaveValue('');

    await processesPage.selectFlowNode('StartEvent_1');
    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).toBeVisible();

    // select another flow node from the diagram
    await processesPage.diagram.clickFlowNode('always fails');

    await expect(processesPage.flowNodeFilter).toHaveValue('Always fails');

    // select same flow node again and see filter is removed
    await processesPage.diagram.clickFlowNode('always fails');

    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).not.toBeVisible();

    await expect(processesPage.flowNodeFilter).toHaveValue('');
  });

  test('variable filters', async ({page, processesPage}) => {
    const {
      callActivityProcessInstance: {
        processInstanceKey: callActivityProcessInstanceKey,
      },
      orderProcessInstance: {processInstanceKey: orderProcessInstanceKey},
    } = initialData;

    // filter by process instances keys, including completed instances
    await processesPage.displayOptionalFilter('Process Instance Key(s)');
    await processesPage.processInstanceKeysFilter.fill(
      `${orderProcessInstanceKey}, ${callActivityProcessInstanceKey}`,
    );
    await processesPage.completedCheckbox.check({force: true});

    // add variable filter
    await processesPage.displayOptionalFilter('Variable');
    await processesPage.variableNameFilter.fill('filtersTest');
    await processesPage.variableValueFilter.fill('123');

    // open json editor modal and check content
    await page.getByRole('button', {name: /open json editor modal/i}).click();
    await expect(
      page.getByRole('dialog').getByText(/edit variable value/i),
    ).toBeVisible();
    await expect(page.getByRole('dialog').getByText(/123/i)).toBeVisible();

    // close modal
    await page
      .getByRole('dialog')
      .getByRole('button', {name: /cancel/i})
      .click();
    await expect(page.getByRole('dialog')).not.toBeVisible();

    // check that process instances table is filtered correctly
    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText('1 result');
    await expect(
      processesPage.processInstancesTable.getByText(orderProcessInstanceKey, {
        exact: true,
      }),
    ).toBeVisible();
    await expect(
      processesPage.processInstancesTable.getByText(
        callActivityProcessInstanceKey,
        {exact: true},
      ),
    ).not.toBeVisible();

    // switch to multiple mode and add multiple variables
    await page.getByRole('switch', {name: /multiple/i}).click({force: true});
    await processesPage.variableNameFilter.fill('filtersTest');
    await processesPage.variableValueFilter.fill('123, 456');

    // open editor modal and check content
    await page.getByRole('button', {name: /open editor modal/i}).click();
    await expect(
      page.getByRole('dialog').getByText(/edit multiple variable values/i),
    ).toBeVisible();
    await expect(page.getByRole('dialog').getByText(/123, 456/i)).toBeVisible();

    // close modal
    await page
      .getByRole('dialog')
      .getByRole('button', {name: /cancel/i})
      .click();
    await expect(page.getByRole('dialog')).not.toBeVisible();

    // check that process instances table is filtered correctly
    await expect(
      processesPage.processInstancesTable.getByRole('heading'),
    ).toContainText('2 results');
    await expect(
      processesPage.processInstancesTable.getByText(orderProcessInstanceKey, {
        exact: true,
      }),
    ).toBeVisible();
    await expect(
      processesPage.processInstancesTable.getByText(
        callActivityProcessInstanceKey,
        {exact: true},
      ),
    ).toBeVisible();
  });
});
