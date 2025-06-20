/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '../visual-fixtures';
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
import {URL_API_PATTERN} from '../constants';
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
  //TODO: enable when https://github.com/camunda/operate/issues/3344 is implemented
  test.skip(`error page`, async ({page, decisionInstancePage}) => {
    await page.route(URL_API_PATTERN, mockResponses({}));

    await decisionInstancePage.gotoDecisionInstance({
      decisionInstanceKey: '1',
    });

    await expect(page).toHaveScreenshot();
  });

  test(`evaluated`, async ({page, decisionInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionInstanceDetail: mockEvaluatedDecisionInstance,
        drdData: mockEvaluatedDrdData,
        xml: mockEvaluatedXml,
      }),
    );

    await decisionInstancePage.gotoDecisionInstance({
      decisionInstanceKey: '1',
    });

    await expect(page).toHaveScreenshot();
  });

  test(`evaluated (drd panel maximised)`, async ({
    page,
    decisionInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionInstanceDetail: mockEvaluatedDecisionInstance,
        drdData: mockEvaluatedDrdData,
        xml: mockEvaluatedXml,
      }),
    );

    await decisionInstancePage.gotoDecisionInstance({
      decisionInstanceKey: '1',
    });

    await page
      .getByRole('button', {
        name: /maximize drd panel/i,
      })
      .click();
    await expect(page).toHaveScreenshot();
  });

  test(`evaluated (without input output panel)`, async ({
    page,
    decisionInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionInstanceDetail: mockEvaluatedDecisionInstanceWithoutPanels,
        drdData: mockEvaluatedDrdDataWithoutPanels,
        xml: mockEvaluatedXmlWithoutPanels,
      }),
    );

    await decisionInstancePage.gotoDecisionInstance({
      decisionInstanceKey: '1',
    });

    // wait for monaco-editor to be fully rendered
    await page.waitForTimeout(500);

    await expect(page).toHaveScreenshot();
  });

  test(`evaluated (with large table)`, async ({page, decisionInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionInstanceDetail: mockEvaluatedDecisionInstance,
        drdData: mockEvaluatedDrdData,
        xml: mockEvaluatedLargeXml,
      }),
    );

    await decisionInstancePage.gotoDecisionInstance({
      decisionInstanceKey: '1',
    });

    // wait for monaco-editor to be fully rendered
    await page.waitForTimeout(500);

    await decisionInstancePage.closeDrdPanel();

    // Scroll decision table to bottom right
    await page.getByText(/test annotation/i).hover();

    await expect(page).toHaveScreenshot();
  });

  test(`failed`, async ({page, decisionInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionInstanceDetail: mockFailedDecisionInstance,
        drdData: mockFailedDrdData,
        xml: mockFailedXml,
      }),
    );

    await decisionInstancePage.gotoDecisionInstance({
      decisionInstanceKey: '1',
    });

    await expect(page).toHaveScreenshot();
  });

  test(`failed (result tab selected)`, async ({page, decisionInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionInstanceDetail: mockFailedDecisionInstance,
        drdData: mockFailedDrdData,
        xml: mockFailedXml,
      }),
    );

    await decisionInstancePage.gotoDecisionInstance({
      decisionInstanceKey: '1',
    });

    await page
      .getByRole('tab', {
        name: /result/i,
      })
      .click();

    await expect(page).toHaveScreenshot();
  });
});
