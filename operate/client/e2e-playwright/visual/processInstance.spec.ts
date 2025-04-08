/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {
  compensationProcessInstance,
  completedInstance,
  instanceWithIncident,
  mockResponses,
  runningInstance,
} from '../mocks/processInstance';
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

test.describe('process instance page', () => {
  for (const theme of ['light', 'dark']) {
    test(`error page - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_API_PATTERN,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          xml: runningInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: runningInstance.detail.id,
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`running instance - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_API_PATTERN,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          flowNodeInstances: runningInstance.flowNodeInstances,
          statistics: runningInstance.statistics,
          sequenceFlows: runningInstance.sequenceFlows,
          variables: runningInstance.variables,
          xml: runningInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: runningInstance.detail.id,
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`add variable state - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_API_PATTERN,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          flowNodeInstances: runningInstance.flowNodeInstances,
          statistics: runningInstance.statistics,
          sequenceFlows: runningInstance.sequenceFlows,
          variables: runningInstance.variables,
          xml: runningInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: runningInstance.detail.id,
        options: {
          waitUntil: 'networkidle',
        },
      });

      await processInstancePage.addVariableButton.click();
      await expect(page).toHaveScreenshot();
    });

    test(`edit variable state - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_API_PATTERN,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          flowNodeInstances: runningInstance.flowNodeInstances,
          statistics: runningInstance.statistics,
          sequenceFlows: runningInstance.sequenceFlows,
          variables: runningInstance.variables,
          xml: runningInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: runningInstance.detail.id,
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page
        .getByRole('button', {
          name: /edit variable/i,
        })
        .click();

      await expect(page).toHaveScreenshot();
    });

    test(`instance with incident - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_API_PATTERN,
        mockResponses({
          processInstanceDetail: instanceWithIncident.detail,
          flowNodeInstances: instanceWithIncident.flowNodeInstances,
          statistics: instanceWithIncident.statistics,
          sequenceFlows: instanceWithIncident.sequenceFlows,
          variables: instanceWithIncident.variables,
          xml: instanceWithIncident.xml,
          incidents: instanceWithIncident.incidents,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: instanceWithIncident.detail.id,
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page
        .getByRole('button', {
          name: /view 1 incident in instance/i,
        })
        .click();

      await expect(page).toHaveScreenshot();
    });

    test(`completed instance - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_API_PATTERN,
        mockResponses({
          processInstanceDetail: completedInstance.detail,
          flowNodeInstances: completedInstance.flowNodeInstances,
          statistics: completedInstance.statistics,
          sequenceFlows: completedInstance.sequenceFlows,
          variables: completedInstance.variables,
          xml: completedInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: completedInstance.detail.id,
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(processInstancePage.executionCountToggleOn).toBeEnabled();
      await processInstancePage.executionCountToggleOn.click({force: true});

      await page.getByText(/show end date/i).click();

      await expect(page).toHaveScreenshot();
    });

    test(`compensation process instance - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_API_PATTERN,
        mockResponses({
          processInstanceDetail: compensationProcessInstance.detail,
          flowNodeInstances: compensationProcessInstance.flowNodeInstances,
          statistics: compensationProcessInstance.statistics,
          sequenceFlows: compensationProcessInstance.sequenceFlows,
          variables: compensationProcessInstance.variables,
          xml: compensationProcessInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: compensationProcessInstance.detail.id,
        options: {
          waitUntil: 'networkidle',
        },
      });

      await processInstancePage.executionCountToggleOn.click({force: true});

      await page.getByText(/show end date/i).click();

      await expect(page).toHaveScreenshot();
    });
  }
});
