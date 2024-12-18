/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../test-fixtures';
import {expect} from '@playwright/test';

import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockStatistics,
  mockResponses as mockProcessesResponses,
  mockNewDeleteOperation,
  mockProcessInstances,
  mockFinishedOrderProcessInstances,
} from '../mocks/processes.mocks';
import {
  mockResponses as mockProcessDetailResponses,
  completedOrderProcessInstance,
} from '../mocks/processInstance';
import {open} from 'modules/mocks/diagrams';
import {URL_PATTERN} from '../constants';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('delete finished instances', () => {
  test('delete finished instance from processes page', async ({
    page,
    processesPage,
    processesPage: {filtersPanel},
    commonPage,
  }) => {
    await page.route(
      URL_PATTERN,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses,
        batchOperations: mockBatchOperations,
        processInstances: mockFinishedOrderProcessInstances,
        statistics: mockStatistics,
        processXml: open('orderProcess.bpmn'),
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {
        completed: 'true',
        canceled: 'true',
      },
      options: {waitUntil: 'networkidle'},
    });

    await filtersPanel.selectProcess('Order process');
    await filtersPanel.selectVersion('1');
    await filtersPanel.processVersionFilter.blur();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instances-finished-instances.png',
    });

    const deleteInstanceButton = page
      .getByRole('row', {
        name: new RegExp(
          `view instance ${mockFinishedOrderProcessInstances.processInstances[0]?.id}`,
          'i',
        ),
      })
      .getByRole('button', {name: 'Delete'});

    await commonPage.addRightArrow(deleteInstanceButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instances-click-delete-operation.png',
    });

    await commonPage.deleteArrows();
    await deleteInstanceButton.click();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instances-delete-operation-confirm.png',
    });
  });

  test('observe result of deletion from processes page', async ({
    page,
    commonPage,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    const processInstancesMock = {
      totalCount: mockFinishedOrderProcessInstances.totalCount - 1,
      processInstances:
        mockFinishedOrderProcessInstances.processInstances.slice(1),
    };

    await page.route(
      URL_PATTERN,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses,
        batchOperations: [mockNewDeleteOperation, ...mockBatchOperations],
        processInstances: processInstancesMock,
        statistics: mockStatistics,
        processXml: open('orderProcess.bpmn'),
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {
        completed: 'true',
        canceled: 'true',
      },
      options: {waitUntil: 'networkidle'},
    });

    await filtersPanel.selectProcess('Order process');
    await filtersPanel.selectVersion('1');
    await filtersPanel.processVersionFilter.blur();

    await commonPage.expandOperationsPanel();

    await processesPage.diagram.moveCanvasHorizontally(-200);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-operations-panel-delete-operation.png',
    });

    await commonPage.collapseOperationsPanel();

    await processesPage.diagram.moveCanvasHorizontally(200);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instance-detail-finished-instances.png',
    });

    const processInstanceKeyCell = page
      .getByRole('row', {
        name: new RegExp(
          `view instance ${processInstancesMock.processInstances[0]?.id}`,
          'i',
        ),
      })
      .getByTestId('cell-processInstanceKey');

    await commonPage.addRightArrow(processInstanceKeyCell);
    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instance-detail-finished-instances-navigate.png',
    });
  });

  test('delete finished instance from process detail page', async ({
    page,
    processInstancePage,
    commonPage,
  }) => {
    await page.route(
      URL_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: completedOrderProcessInstance.detail,
        flowNodeInstances: completedOrderProcessInstance.flowNodeInstances,
        statistics: completedOrderProcessInstance.statistics,
        sequenceFlows: completedOrderProcessInstance.sequenceFlows,
        variables: completedOrderProcessInstance.variables,
        xml: completedOrderProcessInstance.xml,
      }),
    );

    await processInstancePage.navigateToProcessInstance({
      id: '2551799813954282',
      options: {waitUntil: 'networkidle'},
    });

    const deleteInstanceButton = await page.getByRole('button', {
      name: /delete instance/i,
    });

    await commonPage.addRightArrow(deleteInstanceButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-finished-instance-detail.png',
    });

    await commonPage.deleteArrows();

    await deleteInstanceButton.click();

    await expect(
      page.getByRole('button', {name: /danger delete/i}),
    ).toBeVisible();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instance-detail-delete-operation-confirm.png',
    });

    await page.getByRole('button', {name: /danger delete/i}).click();

    await page.route(URL_PATTERN, (route) => {
      if (route.request().url().includes('/api/process-instances/')) {
        return route.fulfill({
          status: 404,
          body: JSON.stringify(''),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('/api/batch-operations')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify([]),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('/api/processes/grouped')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(mockGroupedProcesses),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('/api/process-instances')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(mockProcessInstances),
          headers: {
            'content-type': 'application/json',
          },
        });
      }
    });

    await expect(page.getByText('Instance deleted')).toBeInViewport();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instance-deleted-notification.png',
    });
  });
});
