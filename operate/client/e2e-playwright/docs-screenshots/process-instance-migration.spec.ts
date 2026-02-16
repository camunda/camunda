/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import * as path from 'path';

import {
  mockResponses as mockProcessesResponses,
  mockOrderProcessInstances,
  mockOrderProcessV2Instances,
  mockMigrationOperation,
  mockAhspProcessInstances,
  mockAhspProcessDefinitions,
  mockOrderProcessDefinitions,
} from '../mocks/processes.mocks';
import {openFile} from '@/utils/openFile';
import {expect} from '@playwright/test';
import {URL_API_PATTERN} from '../constants';

const baseDirectory =
  'e2e-playwright/docs-screenshots/process-instance-migration/';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('process instance migration', () => {
  test('migrate process instances', async ({
    page,
    commonPage,
    processesPage,
    processesPage: {filtersPanel},
    migrationView,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        processDefinitions: mockOrderProcessDefinitions,
        batchOperations: {items: [], page: {totalItems: 0}},
        processInstances: mockOrderProcessInstances,
        statistics: {
          items: [
            {
              elementId: 'checkPayment',
              active: 20,
              canceled: 0,
              incidents: 0,
              completed: 0,
            },
          ],
        },
        processXml: openFile(
          './e2e-playwright/mocks/resources/orderProcess_v3.bpmn',
        ),
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'false',
        canceled: 'false',
        completed: 'false',
      },
    });

    await commonPage.addLeftArrow(filtersPanel.processNameFilter);
    await commonPage.addLeftArrow(filtersPanel.processVersionFilter);

    await page.screenshot({
      path: path.join(baseDirectory, 'process-filters.png'),
    });

    await commonPage.deleteArrows();

    await processesPage.gotoProcessesPage({
      searchParams: {
        process: 'orderProcess',
        version: '1',
        active: 'true',
        incidents: 'false',
        canceled: 'false',
        completed: 'false',
      },
    });

    await processesPage.getNthProcessInstanceCheckbox(3).click();
    await processesPage.getNthProcessInstanceCheckbox(4).click();
    await processesPage.getNthProcessInstanceCheckbox(5).click();

    await commonPage.addRightArrow(
      processesPage.getNthProcessInstanceCheckbox(3),
    );
    await commonPage.addRightArrow(
      processesPage.getNthProcessInstanceCheckbox(4),
    );
    await commonPage.addRightArrow(
      processesPage.getNthProcessInstanceCheckbox(5),
    );
    await commonPage.addDownArrow(processesPage.migrateButton);

    await page.screenshot({
      path: path.join(baseDirectory, 'migrate-button.png'),
    });

    await commonPage.deleteArrows();

    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        statistics: {
          items: [
            {
              elementId: 'checkPayment',
              active: 3,
              canceled: 0,
              incidents: 0,
              completed: 0,
            },
          ],
        },
        processDefinitions: mockOrderProcessDefinitions,
        processXml: openFile(
          './e2e-playwright/mocks/resources/orderProcess_v2.bpmn',
        ),
      }),
    );

    await processesPage.migrateButton.click();
    await processesPage.migrationModal.confirmButton.click();

    await migrationView.selectTargetProcess('Order process');

    await commonPage.addDownArrow(migrationView.targetProcessComboBox);
    await commonPage.addDownArrow(migrationView.targetVersionDropdown);

    await page.screenshot({
      path: path.join(baseDirectory, 'select-target-process.png'),
    });

    await commonPage.deleteArrows();

    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'Check payment',
      targetFlowNodeName: 'Check payment',
    });

    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'Ship Articles',
      targetFlowNodeName: 'Ship Articles',
    });

    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'Request for payment',
      targetFlowNodeName: 'Request for payment',
    });

    await commonPage.addRightArrow(
      page.getByLabel(`Target element for Check payment`),
    );
    await commonPage.addRightArrow(
      page.getByLabel(`Target element for Ship Articles`),
    );
    await commonPage.addRightArrow(
      page.getByLabel(`Target element for Request for payment`),
    );

    await page.screenshot({
      path: path.join(baseDirectory, 'map-elements.png'),
    });

    await commonPage.deleteArrows();

    await migrationView.selectTargetSourceFlowNode('Check payment');

    const flowNodes = page
      .getByTestId('diagram')
      .getByText('Check payment', {exact: true});

    await commonPage.addDownArrow(flowNodes.first());
    await commonPage.addDownArrow(flowNodes.nth(1));

    await page.screenshot({
      path: path.join(baseDirectory, 'highlight-mapping.png'),
    });

    await commonPage.deleteArrows();

    await migrationView.nextButton.click();

    await commonPage.addUpArrow(page.getByTestId('state-overlay-active'));
    await commonPage.addUpArrow(page.getByTestId('modifications-overlay'));

    await page.screenshot({
      path: path.join(baseDirectory, 'summary.png'),
    });

    await commonPage.deleteArrows();

    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        processDefinitions: mockOrderProcessDefinitions,
        batchOperations: {
          items: [
            {
              ...mockMigrationOperation,
              endDate: '2023-09-29T16:23:15.684+0000',
              operationsCompletedCount: 1,
            },
          ],
          page: {totalItems: 1},
        },
        processInstances: mockOrderProcessV2Instances,
        statistics: {
          items: [
            {
              elementId: 'checkPayment',
              active: 3,
              canceled: 0,
              incidents: 0,
              completed: 0,
            },
          ],
        },
        processXml: openFile(
          './e2e-playwright/mocks/resources/orderProcess_v2.bpmn',
        ),
      }),
    );

    await migrationView.confirmButton.click();

    await migrationView.confirmMigration();

    await processesPage.diagram.moveCanvasHorizontally(-200);

    await expect(
      page.getByTestId('state-overlay-checkPayment-active'),
    ).toBeVisible();
  });

  test('migrate ad hoc subprocess process instances', async ({
    page,
    processesPage,
    migrationView,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        processDefinitions: mockAhspProcessDefinitions,
        batchOperations: {items: [], page: {totalItems: 0}},
        processInstances: mockAhspProcessInstances,
        statistics: {
          items: [
            {
              elementId: 'AD_HOC_SUBPROCESS',
              active: 3,
              canceled: 0,
              incidents: 0,
              completed: 0,
            },
            {
              elementId: 'A',
              active: 3,
              canceled: 0,
              incidents: 0,
              completed: 0,
            },
          ],
        },
        processXml: openFile(
          './e2e-playwright/mocks/resources/migration-ahsp-process_v1.bpmn',
        ),
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'false',
        canceled: 'false',
        completed: 'false',
      },
    });

    await processesPage.gotoProcessesPage({
      searchParams: {
        process: 'migration-ahsp-process_v1',
        version: '1',
        active: 'true',
      },
    });

    // Select first instance for migration
    await processesPage.getNthProcessInstanceCheckbox(0).click();
    await processesPage.getNthProcessInstanceCheckbox(1).click();
    await processesPage.getNthProcessInstanceCheckbox(2).click();

    await processesPage.migrateButton.click();
    await processesPage.migrationModal.confirmButton.click();

    // Load target process v2 with statistics
    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        statistics: {
          items: [
            {
              elementId: 'AD_HOC_SUBPROCESS',
              active: 3,
              canceled: 0,
              incidents: 0,
              completed: 0,
            },
            {
              elementId: 'D',
              active: 3,
              canceled: 0,
              incidents: 0,
              completed: 0,
            },
          ],
        },
        processDefinitions: mockAhspProcessDefinitions,
        processXml: openFile(
          './e2e-playwright/mocks/resources/migration-ahsp-process_v2.bpmn',
        ),
      }),
    );

    await migrationView.selectTargetProcess('Ad Hoc Subprocess Target');
    await migrationView.selectTargetVersion('2');

    // Verify ad hoc subprocess element is shown
    await expect(
      page.getByText('Ad Hoc Subprocess', {exact: true}),
    ).toHaveCount(4);

    // Verify user task element is shown
    await expect(page.getByText('A', {exact: true})).toHaveCount(2);

    // Map the ad hoc subprocess
    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'Ad Hoc Subprocess',
      targetFlowNodeName: 'Ad Hoc Subprocess',
    });

    // Map user task
    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'A',
      targetFlowNodeName: 'D',
    });

    await migrationView.nextButton.click();

    // Wait for overlays to appear
    await expect(
      page.getByTestId('state-overlay-active').first(),
    ).toBeVisible();
    await expect(
      page.getByTestId('modifications-overlay').first(),
    ).toBeVisible();

    // Mock the migration operation
    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        processDefinitions: mockAhspProcessDefinitions,
        batchOperations: {
          items: [mockMigrationOperation],
          page: {totalItems: 1},
        },
      }),
    );

    // Confirm migration
    await migrationView.confirmButton.click();

    await migrationView.migrationConfirmationModal
      .getByRole('textbox')
      .fill('MIGRATE');

    await migrationView.migrationConfirmationModal
      .getByRole('button', {name: /confirm/i})
      .click();

    // Mock the migrated process instances
    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        processDefinitions: mockAhspProcessDefinitions,
        batchOperations: {
          items: [
            {
              ...mockMigrationOperation,
              endDate: '2023-10-10T09:00:00.000+0000',
              operationsCompletedCount: 3,
            },
          ],
          page: {totalItems: 1},
        },
        processInstances: {
          page: {
            totalItems: 3,
          },
          items: mockAhspProcessInstances.items.map((instance) => ({
            ...instance,
            processId: '2251799813685250',
            processVersion: 2,
            bpmnProcessId: 'migration-ahsp-process_v2',
            processName: 'Ad Hoc Subprocess Target',
          })),
        },
        statistics: {
          items: [
            {
              elementId: 'AD_HOC_SUBPROCESS',
              active: 3,
              canceled: 0,
              incidents: 0,
              completed: 0,
            },
            {
              elementId: 'D',
              active: 3,
              canceled: 0,
              incidents: 0,
              completed: 0,
            },
          ],
        },
        processXml: openFile(
          './e2e-playwright/mocks/resources/migration-ahsp-process_v2.bpmn',
        ),
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        process: 'migration-ahsp-process_v2',
        version: '2',
        active: 'true',
      },
    });

    // Wait for process instances table to have data
    await expect(
      processesPage.processInstancesTable.getByRole('row'),
    ).not.toHaveCount(0);

    // Navigate to batch operations page
    await page.getByRole('button', {name: /view batch operations/i}).click();
    await page.waitForURL('**/batch-operations');

    // Verify migration operation appears in the table
    await expect(
      page.getByRole('cell', {name: 'Migrate Process Instance'}),
    ).toBeVisible();
  });
});
