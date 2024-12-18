/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {Paths} from 'modules/Routes';
import {
  mockEvaluatedDecisionInstance,
  mockEvaluatedDecisionInstanceWithoutPanels,
  mockEvaluatedDrdData,
  mockEvaluatedDrdDataWithoutPanels,
  mockEvaluatedLargeXml,
  mockEvaluatedXml,
  mockEvaluatedXmlWithoutPanels,
  mockFailedDecisionInstance,
  mockFailedDrdData,
  mockFailedXml,
  mockResponses,
} from '../mocks/decisionInstance.mocks';
import {URL_PATTERN} from '../constants';
import {clientConfigMock} from '../mocks/clientConfig';

test.beforeEach(async ({context}) => {
  await context.route('**/client-config.js', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/javascript;charset=UTF-8',
      },
      body: clientConfigMock,
    }),
  );
});

test.describe('decision instance page', () => {
  for (const theme of ['light', 'dark']) {
    //TODO: enable when https://github.com/camunda/operate/issues/3344 is implemented
    test.skip(`error page - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(URL_PATTERN, mockResponses({}));

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      await expect(page).toHaveScreenshot();
    });

    test(`evaluated - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_PATTERN,
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
        URL_PATTERN,
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
        URL_PATTERN,
        mockResponses({
          decisionInstanceDetail: mockEvaluatedDecisionInstanceWithoutPanels,
          drdData: mockEvaluatedDrdDataWithoutPanels,
          xml: mockEvaluatedXmlWithoutPanels,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      // wait for monaco-editor to be fully rendered
      await page.waitForTimeout(500);

      await expect(page).toHaveScreenshot();
    });

    test(`evaluated (with large table) - ${theme}`, async ({
      page,
      commonPage,
      decisionInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_PATTERN,
        mockResponses({
          decisionInstanceDetail: mockEvaluatedDecisionInstance,
          drdData: mockEvaluatedDrdData,
          xml: mockEvaluatedLargeXml,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      // wait for monaco-editor to be fully rendered
      await page.waitForTimeout(500);

      await decisionInstancePage.closeDrdPanel();

      // Scroll decision table to bottom right
      await page.getByText(/test annotation/i).hover();

      await expect(page).toHaveScreenshot();
    });

    test(`failed - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_PATTERN,
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
        URL_PATTERN,
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
