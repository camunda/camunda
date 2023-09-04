/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect, Route} from '@playwright/test';
import {test} from '../test-fixtures';
import {DecisionInstancesDto} from 'modules/api/decisionInstances/fetchDecisionInstances';
import {
  mockDecisionInstances,
  mockGroupedDecisions,
  mockBatchOperations,
  mockDecisionXml,
} from './decisions.mocks';
import {DecisionDto} from 'modules/api/decisions/fetchGroupedDecisions';

function mockResponses({
  batchOperations,
  groupedDecisions,
  decisionInstances,
  decisionXml,
}: {
  batchOperations: OperationEntity[];
  groupedDecisions: DecisionDto[];
  decisionInstances: DecisionInstancesDto;
  decisionXml: string;
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
        status: 200,
        body: JSON.stringify(batchOperations),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/decisions/grouped')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(groupedDecisions),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/decision-instances')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(decisionInstances),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('xml')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(decisionXml),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

test.describe('decisions page', () => {
  for (const theme of ['light', 'dark']) {
    test(`empty page - ${theme}`, async ({page, commonPage, decisionsPage}) => {
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
          groupedDecisions: mockGroupedDecisions,
          decisionXml: '',
          decisionInstances: {
            decisionInstances: [],
            totalCount: 0,
          },
        }),
      );

      await decisionsPage.navigateToDecisions({
        searchParams: {
          evaluated: 'true',
          failed: 'true',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`error page - ${theme}`, async ({page, commonPage, decisionsPage}) => {
      await commonPage.changeTheme(theme);

      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isDecisionsFiltersCollapsed: true,
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(/^.*\/api.*$/i, (route) => {
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

        if (route.request().url().includes('/api/decisions/grouped')) {
          return route.fulfill({
            status: 200,
            body: JSON.stringify(mockGroupedDecisions),
            headers: {
              'content-type': 'application/json',
            },
          });
        }

        if (route.request().url().includes('/api/batch-operations')) {
          return route.fulfill({
            status: 500,
            body: '',
            headers: {
              'content-type': 'application/json',
            },
          });
        }

        if (route.request().url().includes('xml')) {
          return route.fulfill({
            status: 500,
            body: '',
            headers: {
              'content-type': 'application/json',
            },
          });
        }

        if (route.request().url().includes('/api/decision-instances')) {
          return route.fulfill({
            status: 500,
            body: '',
            headers: {
              'content-type': 'application/json',
            },
          });
        }

        return route.continue();
      });

      await decisionsPage.navigateToDecisions({
        searchParams: {
          evaluated: 'true',
          failed: 'true',
          name: 'invoiceClassification',
          version: '2',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data - ${theme}`, async ({
      page,
      commonPage,
      decisionsPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedDecisions: mockGroupedDecisions,
          batchOperations: mockBatchOperations,
          decisionInstances: mockDecisionInstances,
          decisionXml: mockDecisionXml,
        }),
      );

      await decisionsPage.navigateToDecisions({
        searchParams: {
          evaluated: 'true',
          failed: 'true',
          name: 'invoiceClassification',
          version: '2',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data and operations panel expanded - ${theme}`, async ({
      page,
      commonPage,
      decisionsPage,
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
          groupedDecisions: mockGroupedDecisions,
          batchOperations: mockBatchOperations,
          decisionInstances: mockDecisionInstances,
          decisionXml: mockDecisionXml,
        }),
      );

      await decisionsPage.navigateToDecisions({
        searchParams: {
          evaluated: 'true',
          failed: 'true',
          name: 'invoiceClassification',
          version: '2',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`optional filters visible - ${theme}`, async ({
      page,
      commonPage,
      decisionsPage,
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
          groupedDecisions: mockGroupedDecisions,
          batchOperations: mockBatchOperations,
          decisionInstances: mockDecisionInstances,
          decisionXml: mockDecisionXml,
        }),
      );

      await decisionsPage.navigateToDecisions({
        searchParams: {
          evaluated: 'true',
          failed: 'true',
          name: 'invoiceClassification',
          version: '2',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await decisionsPage.displayOptionalFilter('Process Instance Key');
      await decisionsPage.displayOptionalFilter('Decision Instance Key(s)');
      await decisionsPage.decisionInstanceKeysFilter.type('aaa');
      await decisionsPage.displayOptionalFilter('Evaluation Date Range');

      await expect(page).toHaveScreenshot();
    });
  }
});
