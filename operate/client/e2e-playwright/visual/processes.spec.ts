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

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockProcessInstances,
  mockProcessInstancesWithOperationError,
  mockProcessXml,
  mockStatistics,
  mockResponses,
} from '../mocks/processes.mocks';

test.describe('processes page', () => {
  for (const theme of ['light', 'dark']) {
    test(`empty page - ${theme}`, async ({page, commonPage, processesPage}) => {
      await commonPage.changeTheme(theme);

      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          batchOperations: [],
          groupedProcesses: mockGroupedProcesses,
          statistics: [],
          processXml: '',
          processInstances: {
            processInstances: [],
            totalCount: 0,
          },
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`error page - ${theme}`, async ({page, commonPage, processesPage}) => {
      await commonPage.changeTheme(theme);

      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isFiltersCollapsed: true,
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          process: 'bigVarProcess',
          version: '1',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data and one flow node selected - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          completed: 'true',
          canceled: 'true',
          process: 'eventSubprocessProcess',
          version: '1',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.selectFlowNode('Event Subprocess task');

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data and operations panel expanded - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);
      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          completed: 'true',
          canceled: 'true',
          process: 'eventSubprocessProcess',
          version: '1',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`optional filters visible (part 1) - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);
      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.displayOptionalFilter('Variable');
      await processesPage.displayOptionalFilter('Error Message');
      await processesPage.displayOptionalFilter('Operation Id');
      await processesPage.operationIdFilter.type('aaa');
      await expect(page.getByText('Id has to be a UUID')).toBeVisible();
      await expect(page).toHaveScreenshot();
    });

    test(`optional filters visible (part 2) - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);
      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.displayOptionalFilter('Parent Process Instance Key');
      await processesPage.displayOptionalFilter('Process Instance Key(s)');
      await processesPage.displayOptionalFilter('Failed job but retries left');
      await processesPage.displayOptionalFilter('End Date Range');

      await expect(page).toHaveScreenshot();
    });

    test(`data table toolbar visible - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page.getByRole('columnheader', {name: 'Select all rows'}).click();

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data and active batchOperationId filter - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstancesWithOperationError,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          batchOperationId: 'bf547ac3-9a35-45b9-ab06-b80b43785153',
        },

        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.displayOptionalFilter('Operation Id');
      await processesPage.operationIdFilter.type(
        'bf547ac3-9a35-45b9-ab06-b80b43785153',
      );

      await expect(page.getByLabel('Sort by Operation State')).toBeInViewport();

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data, active batchOperationId filter and error message expanded - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstancesWithOperationError,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          batchOperationId: 'bf547ac3-9a35-45b9-ab06-b80b43785153',
        },

        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.displayOptionalFilter('Operation Id');
      await processesPage.operationIdFilter.type(
        'bf547ac3-9a35-45b9-ab06-b80b43785153',
      );

      const errorRow = page.getByRole('row', {name: '6755399441062827'});

      await expect(errorRow).toBeInViewport();

      await errorRow.getByRole('button', {name: 'Expand current row'}).click();

      await expect(
        page.getByText('Batch Operation Error Message'),
      ).toBeInViewport();

      await expect(page).toHaveScreenshot();
    });
  }
});
