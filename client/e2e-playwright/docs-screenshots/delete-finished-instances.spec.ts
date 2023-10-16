/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test} from '../test-fixtures';
import {expect} from '@playwright/test';

import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockFinishedProcessInstances,
  mockStatistics,
  mockResponses as mockProcessesResponses,
  mockProcessXml,
  mockNewDeleteOperation,
  mockProcessInstances,
} from '../mocks/processes.mocks';
import {
  mockResponses as mockProcessDetailResponses,
  completedInstance,
} from '../mocks/processInstance.mocks';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('delete finished instances', () => {
  test('delete finished instance from processes page', async ({
    page,
    processesPage,
    commonPage,
  }) => {
    await page.route(
      /^.*\/api.*$/i,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses,
        batchOperations: mockBatchOperations,
        processInstances: mockFinishedProcessInstances,
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {completed: 'true', canceled: 'true'},
      options: {waitUntil: 'networkidle'},
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instances-finished-instances.png',
    });

    const deleteInstanceButton = await page
      .getByRole('row', {
        name: new RegExp(
          `view instance ${mockFinishedProcessInstances.processInstances[0]?.id}`,
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
  }) => {
    const processInstancesMock = {
      totalCount: mockFinishedProcessInstances.totalCount - 1,
      processInstances: mockFinishedProcessInstances.processInstances.slice(1),
    };

    await page.route(
      /^.*\/api.*$/i,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses,
        batchOperations: [mockNewDeleteOperation, ...mockBatchOperations],
        processInstances: processInstancesMock,
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {completed: 'true', canceled: 'true'},
      options: {waitUntil: 'networkidle'},
    });

    await page.getByRole('button', {name: /expand operations/i}).click();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-operations-panel-delete-operation.png',
    });

    await page.getByRole('button', {name: /collapse operations/i}).click();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instance-detail-finished-instances.png',
    });

    const processInstanceKeyCell = await page
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
      /^.*\/api.*$/i,
      mockProcessDetailResponses({
        processInstanceDetail: completedInstance.detail,
        flowNodeInstances: completedInstance.flowNodeInstances,
        statistics: completedInstance.statistics,
        sequenceFlows: completedInstance.sequenceFlows,
        variables: completedInstance.variables,
        xml: completedInstance.xml,
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

    expect(page.getByRole('button', {name: /danger delete/i})).toBeVisible();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instance-detail-delete-operation-confirm.png',
    });

    await page.getByRole('button', {name: /danger delete/i}).click();

    await page.route(/^.*\/api.*$/i, (route) => {
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

    await expect(page.getByText('Instance deleted')).toBeVisible();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instance-deleted-notification.png',
    });
  });
});
