/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DATE_REGEX, SETUP_WAITING_TIME} from './constants';
import {createDemoInstances} from './operations.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {config} from '../config';
import {ENDPOINTS} from './api/endpoints';

let initialData: Awaited<ReturnType<typeof createDemoInstances>>;

test.beforeAll(async ({request}) => {
  test.setTimeout(SETUP_WAITING_TIME);
  initialData = await createDemoInstances();
  const instanceKeys = [
    initialData.singleOperationInstance.processInstanceKey,
    ...initialData.batchOperationInstances.map(
      ({processInstanceKey}) => processInstanceKey,
    ),
  ];
  // wait until all instances are created
  await Promise.all(
    instanceKeys.map(
      async (instanceKey) =>
        await expect
          .poll(
            async () => {
              const response = await request.get(
                `${config.endpoint}/v1/process-instances/${instanceKey}`,
              );
              return response.status();
            },
            {timeout: SETUP_WAITING_TIME},
          )
          .toBe(200),
    ),
  );

  // create demo operations
  await Promise.all(
    [...new Array(50)].map(async () => {
      const response = await request.post(
        ENDPOINTS.createOperation(
          initialData.singleOperationInstance.processInstanceKey,
        ),
        {
          data: {
            operationType: 'RESOLVE_INCIDENT',
          },
        },
      );

      return response;
    }),
  );

  // wait until all operations are created
  await expect
    .poll(
      async () => {
        const response = await request.post(
          `${config.endpoint}/api/batch-operations`,
          {
            data: {
              pageSize: 50,
            },
          },
        );
        const operations = await response.json();
        return operations.length;
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBe(50);
});

test.beforeEach(async ({page, dashboardPage}) => {
  await dashboardPage.navigateToDashboard();
  await page.getByRole('link', {name: /processes/i}).click();
});

test.describe('Operations', () => {
  test('infinite scrolling', async ({page, commonPage}) => {
    await commonPage.expandOperationsPanel();
    await expect(page.getByTestId('operations-entry')).toHaveCount(20);
    await page.getByTestId('operations-entry').nth(19).scrollIntoViewIfNeeded();
    await expect(page.getByTestId('operations-entry')).toHaveCount(40);
  });

  test('Retry and Cancel single instance @roundtrip', async ({
    commonPage,
    processesPage,
    processesPage: {filtersPanel},
    page,
  }) => {
    test.slow();
    const instance = initialData.singleOperationInstance;

    // ensure page is loaded
    await expect(page.getByTestId('data-list')).toBeVisible();

    // filter by Process Instance Key
    await filtersPanel.displayOptionalFilter('Process Instance Key(s)');
    await filtersPanel.processInstanceKeysFilter.fill(
      instance.processInstanceKey,
    );

    // wait for filter to be applied
    await expect(page.getByText('1 results')).toBeVisible();

    // retry single instance using operation button
    await page
      .getByRole('button', {
        name: `Retry Instance ${instance.processInstanceKey}`,
      })
      .click();

    // expect spinner to show and disappear
    await expect(processesPage.operationSpinner).toBeVisible();
    await expect(processesPage.operationSpinner).toBeHidden({timeout: 30000});

    // cancel single instance using operation button
    await page
      .getByRole('button', {
        name: `Cancel Instance ${instance.processInstanceKey}`,
      })
      .click();

    await page
      .getByRole('button', {
        name: 'Apply',
      })
      .click();

    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).toBeVisible();

    await expect(commonPage.operationsList).toBeHidden();
    await commonPage.expandOperationsPanel();

    await expect(commonPage.operationsList).toBeVisible();

    const operationItem = commonPage.operationsList
      .getByRole('listitem')
      .nth(0);

    const operationId = await operationItem.getByRole('link').innerText();

    await expect(operationItem.getByText('Cancel')).toBeVisible();
    await expect(operationItem.getByText(DATE_REGEX)).toBeVisible();

    await operationItem.getByRole('link').click();
    await expect(filtersPanel.operationIdFilter).toHaveValue(operationId);
    await expect(page.getByText('1 results')).toBeVisible();

    const instanceRow = page.getByTestId('data-list').getByRole('row').nth(0);

    await expect(
      page.getByTestId(`CANCELED-icon-${instance.processInstanceKey}`),
    ).toBeVisible();

    await expect(instanceRow.getByText(instance.bpmnProcessId)).toBeVisible();
    await expect(
      instanceRow.getByText(instance.processInstanceKey),
    ).toBeVisible();

    await commonPage.collapseOperationsPanel();
  });

  test('Retry and cancel multiple instances @roundtrip', async ({
    commonPage,
    processesPage: {filtersPanel},
    page,
  }) => {
    test.slow();
    const instances = initialData.batchOperationInstances.slice(0, 5);

    // ensure page is loaded
    await expect(page.getByTestId('data-list')).toBeVisible();

    // filter by Process Instance Keys
    await filtersPanel.displayOptionalFilter('Process Instance Key(s)');
    await filtersPanel.processInstanceKeysFilter.fill(
      instances.map((instance) => instance.processInstanceKey).join(','),
    );

    const instancesList = page.getByTestId('data-list');

    // wait for the filter to be applied
    await expect(instancesList.getByRole('row')).toHaveCount(instances.length);

    await page.getByRole('columnheader', {name: 'Select all rows'}).click();

    await page.getByRole('button', {name: 'Retry', exact: true}).click();

    await expect(commonPage.operationsList).toBeHidden();

    await page.getByRole('button', {name: 'Apply'}).click();
    await expect(commonPage.operationsList).toBeVisible();

    await expect(page.getByTitle(/has scheduled operations/i)).toHaveCount(
      instances.length,
    );

    const operationsListItems = commonPage.operationsList.getByRole('listitem');

    // expect first operation item to have progress bar
    await expect(
      operationsListItems.nth(0).getByRole('progressbar'),
    ).toBeVisible();

    // wait for instance to finish in operation list (end time is present, progess bar gone)
    await expect(
      operationsListItems.nth(0).getByText(DATE_REGEX),
    ).toBeVisible({timeout: 30000});

    await expect(
      operationsListItems.nth(0).getByRole('progressbar'),
    ).toBeHidden();

    await filtersPanel.resetFiltersButton.click();

    await expect
      .poll(async () => {
        const count = await instancesList.getByRole('row').count();
        return count;
      })
      .toBeGreaterThan(instances.length);
    // select all instances from operation
    await operationsListItems.nth(0).getByRole('link').click();

    await expect(instancesList.getByRole('row')).toHaveCount(instances.length);

    const operationId = await operationsListItems
      .nth(0)
      .getByRole('link')
      .innerText();
    await expect(filtersPanel.operationIdFilter).toHaveValue(operationId);

    // check if all instances are shown
    await Promise.all(
      instances.map((instance) =>
        expect(
          page.getByRole('link', {
            name: instance.processInstanceKey,
          }),
        ).toBeVisible(),
      ),
    );

    await commonPage.collapseOperationsPanel();
    await page.getByRole('columnheader', {name: 'Select all rows'}).click();

    await page.getByRole('button', {name: 'Cancel', exact: true}).click();
    await page.getByRole('button', {name: 'Apply'}).click();

    await expect(commonPage.operationsList).toBeVisible();
    await expect(page.getByTitle(/has scheduled operations/i)).toHaveCount(
      instances.length,
    );

    // expect first operation item to have progress bar
    await expect(
      operationsListItems.nth(0).getByRole('progressbar'),
    ).toBeVisible();

    // expect cancel icon to show for each instance
    await Promise.all(
      instances.map((instance) =>
        expect(
          page.getByTestId(`CANCELED-icon-${instance.processInstanceKey}`),
        ).toBeVisible(),
      ),
    );
  });
});
