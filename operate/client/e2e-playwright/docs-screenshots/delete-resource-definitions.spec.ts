/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';

import {
  mockResponses as mockProcessesResponses,
  mockDeleteProcess,
  mockProcessDefinitions,
} from '../mocks/processes.mocks';
import {
  mockDecisionXml,
  mockResponses as mockDecisionsResponses,
  mockDeleteDecision,
  mockEmptyDecisionInstances,
  mockedDecisionDefinitions,
} from '../mocks/decisions.mocks';
import {openFile} from '@/utils/openFile';
import {expect} from '@playwright/test';
import {URL_API_PATTERN} from '../constants';

test.describe.skip('delete resource definitions', () => {
  test('delete process definitions', async ({
    context,
    page,
    commonPage,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    await commonPage.mockClientConfig(context);

    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: {items: [], page: {totalItems: 0}},
        processInstances: {
          items: [],
          page: {totalItems: 0},
        },
        statistics: {
          items: [],
        },
        processXml: openFile(
          './e2e-playwright/mocks/resources/orderProcess.bpmn',
        ),
        deleteProcess: mockDeleteProcess,
      }),
    );

    await page.setViewportSize({width: 1650, height: 900});

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
        canceled: 'true',
        completed: 'true',
      },
    });

    await commonPage.addLeftArrow(filtersPanel.processNameFilter);
    await commonPage.addLeftArrow(filtersPanel.processVersionFilter);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/process-filters.png',
    });

    await commonPage.deleteArrows();

    await processesPage.gotoProcessesPage({
      searchParams: {
        process: 'orderProcess',
        version: '1',
        active: 'true',
        incidents: 'true',
        canceled: 'true',
        completed: 'true',
      },
    });

    await commonPage.addRightArrow(processesPage.deleteResourceButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/process-button.png',
    });

    await commonPage.deleteArrows();

    await commonPage.disableModalAnimation();
    await processesPage.deleteResourceButton.click();

    await processesPage.deleteResourceModal.confirmCheckbox.check({
      force: true,
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/process-modal.png',
    });

    await processesPage.deleteResourceModal.confirmButton.click();

    await commonPage.addUpArrow(page.getByRole('progressbar'));

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/process-operations-panel.png',
    });
  });

  test('delete decision definitions', async ({
    context,
    page,
    commonPage,
    decisionsPage,
  }) => {
    await commonPage.mockClientConfig(context);

    await page.route(
      URL_API_PATTERN,
      mockDecisionsResponses({
        decisionDefinitions: mockedDecisionDefinitions,
        batchOperations: {items: [], page: {totalItems: 0}},
        decisionInstances: mockEmptyDecisionInstances,
        decisionXml: mockDecisionXml,
        deleteDecision: mockDeleteDecision,
      }),
    );

    await page.setViewportSize({width: 1650, height: 900});

    await decisionsPage.gotoDecisionsPage({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
      },
    });

    await commonPage.addLeftArrow(decisionsPage.decisionNameFilter);
    await commonPage.addLeftArrow(decisionsPage.decisionVersionFilter);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/decision-filters.png',
    });

    await commonPage.deleteArrows();

    await decisionsPage.gotoDecisionsPage({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
        name: 'invoiceClassification',
        version: '2',
      },
    });

    await commonPage.addRightArrow(decisionsPage.deleteResourceButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/decision-button.png',
    });

    await commonPage.deleteArrows();

    await commonPage.disableModalAnimation();
    await decisionsPage.deleteResourceButton.click();

    await expect(
      decisionsPage.deleteResourceModal.confirmCheckbox,
    ).toBeVisible();

    await decisionsPage.deleteResourceModal.confirmCheckbox.click({
      force: true,
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/decision-modal.png',
    });

    await decisionsPage.deleteResourceModal.confirmButton.click();

    await commonPage.addUpArrow(page.getByRole('progressbar'));

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/decision-operations-panel.png',
    });
  });
});
