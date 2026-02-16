/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import {expect} from '@playwright/test';

import {
  mockBatchOperations,
  mockResponses as mockProcessesResponses,
  mockNewDeleteOperation,
  mockFinishedOrderProcessInstances,
  mockStatistics,
  mockProcessDefinitions,
} from '../mocks/processes.mocks';
import {
  mockResponses as mockProcessDetailResponses,
  completedOrderProcessInstance,
} from '../mocks/processInstance';
import {openFile} from '@/utils/openFile';
import {URL_API_PATTERN} from '../constants';

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
      URL_API_PATTERN,
      mockProcessesResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: mockBatchOperations,
        processInstances: mockFinishedOrderProcessInstances,
        statistics: mockStatistics,
        processXml: openFile(
          './e2e-playwright/mocks/resources/orderProcess.bpmn',
        ),
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        completed: 'true',
        canceled: 'true',
      },
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
          `view instance ${mockFinishedOrderProcessInstances.items[0]?.processInstanceKey}`,
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
      page: {
        totalItems: mockFinishedOrderProcessInstances.page.totalItems - 1,
      },
      items: mockFinishedOrderProcessInstances.items.slice(1),
    };

    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: {
          ...mockBatchOperations,
          items: [mockNewDeleteOperation, ...mockBatchOperations.items],
        },
        processInstances: processInstancesMock,
        statistics: mockStatistics,
        processXml: openFile(
          './e2e-playwright/mocks/resources/orderProcess.bpmn',
        ),
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        completed: 'true',
        canceled: 'true',
      },
    });

    await filtersPanel.selectProcess('Order process');
    await filtersPanel.selectVersion('1');
    await filtersPanel.processVersionFilter.blur();

    await processesPage.diagram.moveCanvasHorizontally(200);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instance-detail-finished-instances.png',
    });

    const processInstanceKeyCell = page
      .getByRole('row', {
        name: new RegExp(
          `view instance ${processInstancesMock.items[0]?.processInstanceKey}`,
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
      URL_API_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: completedOrderProcessInstance.detail,
        processInstanceDetailV2: completedOrderProcessInstance.detailV2,
        callHierarchy: completedOrderProcessInstance.callHierarchy,
        elementInstances: completedOrderProcessInstance.elementInstances,
        statistics: completedOrderProcessInstance.statistics,
        sequenceFlows: completedOrderProcessInstance.sequenceFlows,
        sequenceFlowsV2: completedOrderProcessInstance.sequenceFlowsV2,
        xml: completedOrderProcessInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '2551799813954282',
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

    await page.route(URL_API_PATTERN, (route) => {
      if (route.request().url().includes('call-hierarchy')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(completedOrderProcessInstance.callHierarchy),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('/api/process-instances/')) {
        return route.fulfill({
          status: 404,
          body: JSON.stringify(''),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('/v2/process-instances/')) {
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

      if (route.request().url().includes('/v2/process-definitions/search')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(mockProcessDefinitions),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      route.continue();
    });

    await expect(
      page.getByText('Instance is scheduled for deletion'),
    ).toBeInViewport();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-finished-instances/operate-instance-deleted-notification.png',
    });
  });
});
