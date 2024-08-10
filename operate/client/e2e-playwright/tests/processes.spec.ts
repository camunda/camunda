/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './processes.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {convertToQueryString} from '../utils/convertToQueryString';
import {zeebeGrpcApi} from '../api/zeebe-grpc';
import {config} from '../config';
import {SETUP_WAITING_TIME} from './constants';
import {Paths} from 'modules/Routes';

const {deployProcesses} = zeebeGrpcApi;

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  test.setTimeout(SETUP_WAITING_TIME);

  await Promise.all([
    expect
      .poll(
        async () => {
          const response = await request.get(
            `${config.endpoint}/v1/process-instances/${initialData.instanceWithoutAnIncident.processInstanceKey}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200),
    expect
      .poll(
        async () => {
          const response = await request.get(
            `${config.endpoint}/v1/process-instances/${initialData.instanceWithAnIncident.processInstanceKey}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200),
    expect
      .poll(
        async () => {
          const response = await request.get(
            `${config.endpoint}/v1/process-instances/${initialData.instanceToCancel.processInstanceKey}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200),
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/v1/incidents/search`,
            {
              data: {
                filter: {
                  processInstanceKey: parseInt(
                    initialData.instanceWithAnIncident.processInstanceKey,
                  ),
                },
              },
            },
          );

          const incidents: {items: [{state: string}]; total: number} =
            await response.json();

          return (
            incidents.total > 0 &&
            incidents.items.filter(({state}) => state === 'PENDING').length ===
              0
          );
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBeTruthy(),
  ]);
});

test.beforeEach(async ({page, dashboardPage}) => {
  await dashboardPage.navigateToDashboard();
  await page.getByRole('link', {name: /processes/i}).click();
});

test.describe('Processes', () => {
  test('Processes Page Initial Load', async ({
    processesPage: {filtersPanel},
    page,
  }) => {
    await filtersPanel.validateCheckedState({
      checked: [
        filtersPanel.runningInstancesCheckbox,
        filtersPanel.activeCheckbox,
        filtersPanel.incidentsCheckbox,
      ],
      unChecked: [
        filtersPanel.finishedInstancesCheckbox,
        filtersPanel.completedCheckbox,
        filtersPanel.canceledCheckbox,
      ],
    });

    await expect(page.getByText('There is no Process selected')).toBeVisible();
    await expect(
      page.getByText('To see a Diagram, select a Process in the filters panel'),
    ).toBeVisible();

    await filtersPanel.displayOptionalFilter('Process Instance Key(s)');

    await filtersPanel.processInstanceKeysFilter.fill(
      `${initialData.instanceWithoutAnIncident.processInstanceKey}, ${initialData.instanceWithAnIncident.processInstanceKey}`,
    );

    const table = page.getByRole('table');

    await expect(table).toBeVisible();
    await expect(table.getByRole('row')).toHaveCount(3);

    await expect(
      table.getByTestId(
        `INCIDENT-icon-${initialData.instanceWithAnIncident.processInstanceKey}`,
      ),
    ).toBeVisible();

    await expect(
      table.getByTestId(
        `ACTIVE-icon-${initialData.instanceWithoutAnIncident.processInstanceKey}`,
      ),
    ).toBeVisible();
  });

  test('Select flow node in diagram', async ({
    processesPage,
    processesPage: {filtersPanel},
    page,
  }) => {
    const instance = initialData.instanceWithoutAnIncident;

    await filtersPanel.displayOptionalFilter('Process Instance Key(s)');

    // Filter by Process Instance Key
    await filtersPanel.processInstanceKeysFilter.fill(
      instance.processInstanceKey,
    );

    await expect(page.getByTestId('diagram')).not.toBeInViewport();

    await filtersPanel.selectProcess('Order process');

    // Select "Ship Articles" flow node
    const shipArticlesTaskId = 'shipArticles';
    await expect(page.getByTestId('diagram')).toBeInViewport();

    await processesPage.diagram.clickFlowNode('Ship Articles');
    await expect(filtersPanel.flowNodeFilter).toHaveValue('Ship Articles');

    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).toBeVisible();

    await expect(page).toHaveURL(
      `.${Paths.processes()}?${convertToQueryString({
        active: 'true',
        incidents: 'true',
        ids: instance.processInstanceKey,
        process: 'orderProcess',
        version: '1',
        flowNodeId: shipArticlesTaskId,
      })}`,
    );

    // Ensure Check Payment flow node is not selected
    await expect(
      processesPage.diagram.getFlowNode('Check Payment'),
    ).not.toHaveClass(/selected/);

    // Select "Check Payment" flow node
    const checkPaymentTaskId = 'checkPayment';

    await processesPage.diagram.clickFlowNode('Check payment');
    await expect(filtersPanel.flowNodeFilter).toHaveValue('Check payment');

    await expect(page.getByRole('table').getByRole('row')).toHaveCount(2);

    await expect(page).toHaveURL(
      `.${Paths.processes()}?${convertToQueryString({
        active: 'true',
        incidents: 'true',
        ids: instance.processInstanceKey,
        process: 'orderProcess',
        version: '1',
        flowNodeId: checkPaymentTaskId,
      })}`,
    );

    await expect(
      processesPage.diagram.getFlowNode('Check Payment'),
    ).toHaveClass(/selected/);

    // Ensure that flow node is still selected after page reload
    await page.reload();
    await expect(
      processesPage.diagram.getFlowNode('Check Payment'),
    ).toHaveClass(/selected/);
  });

  test('Wait for process creation', async ({
    processesPage,
    processesPage: {filtersPanel},
    page,
  }) => {
    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'true',
        process: 'testProcess',
        version: '1',
      },
    });

    await expect(page.getByTestId('data-table-skeleton')).toBeVisible();
    await expect(page.getByTestId('diagram-spinner')).toBeVisible();

    await expect(filtersPanel.processNameFilter).toBeDisabled();

    await deployProcesses(['newProcess.bpmn']);

    await expect(page.getByTestId('diagram')).toBeInViewport({timeout: 20000});

    await expect(page.getByTestId('data-table-skeleton')).not.toBeVisible();
    await expect(page.getByTestId('diagram-spinner')).not.toBeVisible();

    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).toBeVisible();

    await expect(filtersPanel.processNameFilter).toBeEnabled();
    await expect(filtersPanel.processNameFilter).toHaveValue('Test Process');
  });

  test('Delete process definition after canceling running instance @roundtrip', async ({
    processesPage,
    page,
  }) => {
    test.slow();
    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'true',
        process: 'processToDelete',
        version: '1',
      },
    });

    await expect(page.getByTestId('data-table-skeleton')).not.toBeVisible();
    await expect(page.getByTestId('diagram-spinner')).not.toBeVisible();

    await expect(
      page.getByRole('heading', {
        name: /process instances - 1 result/i,
      }),
    ).toBeVisible();

    await expect(
      page.getByRole('button', {
        name: 'Only process definitions without running instances can be deleted.',
      }),
    ).toBeDisabled();

    await page
      .getByRole('button', {
        name: /cancel instance/i,
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

    await expect(processesPage.deleteResourceButton).toBeEnabled();

    await processesPage.deleteResourceButton.click();
    await expect(
      processesPage.deleteResourceModal.confirmCheckbox,
    ).toBeVisible();

    await processesPage.deleteResourceModal.confirmCheckbox.click({
      force: true,
    });
    await processesPage.deleteResourceModal.confirmButton.click();

    await expect
      .poll(
        async () => {
          const response = await page.request.post(
            `${config.endpoint}/v1/process-definitions/search`,
            {
              data: {
                filter: {
                  bpmnProcessId: 'processToDelete',
                },
              },
            },
          );

          const definitions: {total: number} = await response.json();
          return definitions.total;
        },
        {timeout: 60000},
      )
      .toBe(0);
  });
});
