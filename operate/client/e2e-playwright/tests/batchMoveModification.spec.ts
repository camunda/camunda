/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './batchMoveModification.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {SETUP_WAITING_TIME} from './constants';
import {config} from '../config';
import {ProcessInstancesStatisticsDto} from 'modules/api/processInstances/fetchProcessInstancesStatistics';

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

test.describe('Process Instance Batch Modification', () => {
  test('Move Operation @roundtrip', async ({
    processesPage,
    processesPage: {filtersPanel},
    commonPage,
    page,
    request,
  }) => {
    test.slow();
    const processInstanceKeys = initialData.processInstances.map(
      (instance) => instance.processInstanceKey,
    );

    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        ids: processInstanceKeys.join(','),
        process: initialData.bpmnProcessId,
        version: initialData.version.toString(),
        flowNodeId: 'checkPayment',
      },
    });

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
    await processesPage.moveModificationModal.confirmButton.click();

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
    ).not.toBeVisible({timeout: 60000});

    await modificationOperationEntry.getByRole('link').click();

    await commonPage.collapseOperationsPanel();

    await filtersPanel.selectProcess('Order process');
    await filtersPanel.selectVersion(initialData.version.toString());

    // Filter by all process instances which have been created in setup
    await filtersPanel.displayOptionalFilter('Process Instance Key(s)');
    await filtersPanel.processInstanceKeysFilter.fill(
      processInstanceKeys.join(','),
    );

    // Expect the correct number of instances related to the move modification
    await expect(
      processesPage.processInstancesTable.getByText(
        `${NUM_SELECTED_PROCESS_INSTANCES} results`,
      ),
    ).toBeVisible();

    // Wait for all process instances to be modified
    await expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/api/process-instances/statistics`,
            {
              data: {
                active: true,
                running: true,
                processIds: [initialData.processDefinitionKey],
                activityId: 'shipArticles',
                ids: processInstanceKeys,
              },
            },
          );
          const statistics: ProcessInstancesStatisticsDto[] =
            await response.json();
          const targetFlowNodeStatistics = statistics.find(({activityId}) => {
            return activityId === 'shipArticles';
          });
          return targetFlowNodeStatistics?.active;
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(NUM_SELECTED_PROCESS_INSTANCES);

    await page.reload();

    // Expect that checkPayment flow node instances got canceled in all process instances
    await expect(
      processesPage.diagram.diagram.getByTestId(
        'state-overlay-checkPayment-canceled',
      ),
    ).toHaveText(NUM_SELECTED_PROCESS_INSTANCES.toString());

    // Expect that flow node instances have been created on shipArticles in all process instances.
    expect(
      processesPage.diagram.diagram.getByTestId(
        'state-overlay-shipArticles-active',
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
    await processesPage.moveModificationModal.confirmButton.click();

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
    await expect(page.getByText(/batch modification mode/i)).not.toBeVisible();

    // Expect to be on Dashboard page
    await expect(
      page.getByText(/running process instances in total/i),
    ).toBeVisible();

    await page.goBack();

    // Expect not to be in move modification mode
    await expect(page.getByText(/batch modification mode/i)).not.toBeVisible();
  });
});
