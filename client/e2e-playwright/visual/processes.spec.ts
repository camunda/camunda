/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect, Route} from '@playwright/test';
import {test} from '../test-fixtures';
import {ProcessDto} from 'modules/api/processes/fetchGroupedProcesses';
import {ProcessInstancesDto} from 'modules/api/processInstances/fetchProcessInstances';
import {ProcessInstancesStatisticsDto} from 'modules/api/processInstances/fetchProcessInstancesStatistics';
import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockProcessInstances,
  mockProcessXml,
  mockStatistics,
} from './processes.mocks';

function mockResponses({
  batchOperations,
  groupedProcesses,
  statistics,
  processInstances,
  processXml,
}: {
  batchOperations?: OperationEntity[];
  groupedProcesses?: ProcessDto[];
  statistics?: ProcessInstancesStatisticsDto[];
  processInstances?: ProcessInstancesDto;
  processXml?: string;
}) {
  return (route: Route) => {
    if (route.request().url().includes('/api/authentications/user')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          userId: 'demo',
          displayName: 'demo',
          canLogout: true,
          permissions: ['read', 'write'],
          roles: null,
          salesPlanType: null,
          c8Links: {},
          username: 'demo',
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/batch-operations')) {
      return route.fulfill({
        status: batchOperations === undefined ? 400 : 200,
        body: JSON.stringify(batchOperations),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/processes/grouped')) {
      return route.fulfill({
        status: groupedProcesses === undefined ? 400 : 200,
        body: JSON.stringify(groupedProcesses),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/process-instances/statistics')) {
      return route.fulfill({
        status: statistics === undefined ? 400 : 200,
        body: JSON.stringify(statistics),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/process-instances')) {
      return route.fulfill({
        status: processInstances === undefined ? 400 : 200,
        body: JSON.stringify(processInstances),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('xml')) {
      return route.fulfill({
        status: processXml === undefined ? 400 : 200,
        body: JSON.stringify(processXml),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

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
  }
});
