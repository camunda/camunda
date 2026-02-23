/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './processInstancesFilters.mocks';
import {test} from '../e2e-fixtures';
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
  await dashboardPage.gotoDashboardPage();
  await page.getByRole('link', {name: /processes/i}).click();
});

test.describe('Process Instances Filters', () => {
  test('Apply Filters', async ({page, processesPage: {filtersPanel}}) => {
    const callActivityProcessInstanceKey =
      initialData.callActivityProcessInstance.processInstanceKey;

    await filtersPanel.displayOptionalFilter('Parent Process Instance Key');
    await filtersPanel.parentProcessInstanceKey.type(
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

    await filtersPanel.resetFiltersButton.click();
    await expect(filtersPanel.parentProcessInstanceKey).not.toBeVisible();
    await expect.poll(() => allRows.count()).toBeGreaterThan(1);

    await page.locator('label').filter({hasText: 'Completed'}).click();

    let currentRowCount = await allRows.count();
    await filtersPanel.displayOptionalFilter('End Date Range');
    await filtersPanel.pickDateTimeRange({
      fromDay: '1',
      toDay: `${day}`,
    });
    await page.getByText('Apply').click();
    await expect.poll(() => allRows.count()).toBeLessThan(currentRowCount);

    currentRowCount = await allRows.count();
    await filtersPanel.resetFiltersButton.click();
    await expect.poll(() => allRows.count()).toBeGreaterThan(currentRowCount);

    currentRowCount = await allRows.count();
    await filtersPanel.displayOptionalFilter('Error Message');
    await filtersPanel.errorMessageFilter.type(
      "failed to evaluate expression 'nonExistingClientId': no variable found for name 'nonExistingClientId'",
    );

    await expect.poll(() => allRows.count()).toBeLessThan(currentRowCount);

    await filtersPanel.displayOptionalFilter('Start Date Range');
    await filtersPanel.pickDateTimeRange({
      fromDay: '1',
      toDay: '1',
      fromTime: '00:00:00',
      toTime: '00:00:00',
    });
    await page.getByText('Apply').click();
    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).toBeVisible();

    await filtersPanel.resetFiltersButton.click();
    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).not.toBeVisible();

    await expect(filtersPanel.errorMessageFilter).not.toBeVisible();
    await expect(filtersPanel.startDateFilter).not.toBeVisible();
  });

  test('Interaction between diagram and filters', async ({
    page,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    await filtersPanel.selectProcess('Process With Multiple Versions');

    await expect(await filtersPanel.processVersionFilter.innerText()).toBe('2');

    // change version and see flow node filter has been reset
    await filtersPanel.selectVersion('1');
    await expect(filtersPanel.flowNodeFilter).toHaveValue('');

    await filtersPanel.selectFlowNode('StartEvent_1');
    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).toBeVisible();

    // select another flow node from the diagram
    await processesPage.diagram.clickFlowNode('always fails');

    await expect(filtersPanel.flowNodeFilter).toHaveValue('Always fails');

    // select same flow node again and see filter is removed
    await processesPage.diagram.clickFlowNode('always fails');

    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).not.toBeVisible();

    await expect(filtersPanel.flowNodeFilter).toHaveValue('');
  });

  test('variable filters', async ({
    page,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    const {
      callActivityProcessInstance: {
        processInstanceKey: callActivityProcessInstanceKey,
      },
      orderProcessInstance: {processInstanceKey: orderProcessInstanceKey},
    } = initialData;

    // filter by process instances keys, including completed instances
    await filtersPanel.displayOptionalFilter('Process Instance Key(s)');
    await filtersPanel.processInstanceKeysFilter.fill(
      `${orderProcessInstanceKey}, ${callActivityProcessInstanceKey}`,
    );
    await filtersPanel.completedCheckbox.check({force: true});

    // add variable filter
    await filtersPanel.displayOptionalFilter('Variable');
    await filtersPanel.variableNameFilter.fill('filtersTest');
    await filtersPanel.variableValueFilter.fill('123');

    // open json editor and check content
    await page.getByRole('button', {name: /open json editor/i}).click();
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
    await filtersPanel.multipleVariablesSwitch.click({force: true});
    await filtersPanel.variableNameFilter.fill('filtersTest');
    await filtersPanel.variableValueFilter.fill('123, 456');

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
