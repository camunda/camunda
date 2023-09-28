/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect, Route} from '@playwright/test';
import {test} from '../test-fixtures';
import {Paths} from 'modules/Routes';
import {DecisionInstanceDto} from 'modules/api/decisionInstances/fetchDecisionInstance';
import {
  mockEvaluatedDrdData,
  mockEvaluatedDecisionInstance,
  mockEvaluatedXml,
  mockEvaluatedDecisionInstanceWithoutPanels,
  mockEvaluatedDrdDataWithoutPanels,
  mockEvaluatedXmlWithoutPanels,
  mockFailedDecisionInstance,
  mockFailedDrdData,
  mockFailedXml,
} from '../mocks/decisionInstance.mocks';
import {DrdDataDto} from 'modules/api/decisionInstances/fetchDrdData';

function mockResponses({
  decisionInstanceDetail,
  drdData,
  xml,
}: {
  decisionInstanceDetail?: DecisionInstanceDto;
  drdData?: DrdDataDto;
  xml?: string;
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

    if (route.request().url().includes('drd-data')) {
      return route.fulfill({
        status: drdData === undefined ? 400 : 200,
        body: JSON.stringify(drdData),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/decision-instances/')) {
      return route.fulfill({
        status: decisionInstanceDetail === undefined ? 400 : 200,
        body: JSON.stringify(decisionInstanceDetail),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('xml')) {
      return route.fulfill({
        status: xml === undefined ? 400 : 200,
        body: JSON.stringify(xml),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

test.describe('decision instance page', () => {
  for (const theme of ['light', 'dark']) {
    //TODO: enable when https://github.com/camunda/operate/issues/3344 is implemented
    test.skip(`error page - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(/^.*\/api.*$/i, mockResponses({}));

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      await expect(page).toHaveScreenshot();
    });

    test(`evaluated - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          decisionInstanceDetail: mockEvaluatedDecisionInstance,
          drdData: mockEvaluatedDrdData,
          xml: mockEvaluatedXml,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      await expect(page).toHaveScreenshot();
    });

    test(`evaluated (drd panel maximised) - ${theme}`, async ({
      page,
      commonPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          decisionInstanceDetail: mockEvaluatedDecisionInstance,
          drdData: mockEvaluatedDrdData,
          xml: mockEvaluatedXml,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      await page
        .getByRole('button', {
          name: /maximize drd panel/i,
        })
        .click();
      await expect(page).toHaveScreenshot();
    });

    test(`evaluated (without input output panel) - ${theme}`, async ({
      page,
      commonPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          decisionInstanceDetail: mockEvaluatedDecisionInstanceWithoutPanels,
          drdData: mockEvaluatedDrdDataWithoutPanels,
          xml: mockEvaluatedXmlWithoutPanels,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      await expect(page).toHaveScreenshot();
    });

    test(`failed - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          decisionInstanceDetail: mockFailedDecisionInstance,
          drdData: mockFailedDrdData,
          xml: mockFailedXml,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      await expect(page).toHaveScreenshot();
    });

    test(`failed (result tab selected) - ${theme}`, async ({
      page,
      commonPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          decisionInstanceDetail: mockFailedDecisionInstance,
          drdData: mockFailedDrdData,
          xml: mockFailedXml,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      await page
        .getByRole('tab', {
          name: /result/i,
        })
        .click();

      await expect(page).toHaveScreenshot();
    });
  }
});
