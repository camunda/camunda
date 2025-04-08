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

test.describe('modifications', () => {
  for (const theme of ['light', 'dark']) {
    test(`with helper modal - ${theme}`, async ({
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
          name: /modify instance/i,
        })
        .click();

      await expect(page).toHaveScreenshot();
    });

    test(`with add variable state - ${theme}`, async ({
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
          name: /modify instance/i,
        })
        .click();

      await page
        .getByRole('button', {
          name: /continue/i,
        })
        .click();

      await processInstancePage.addVariableButton.click();

      await expect(page).toHaveScreenshot();
    });

    test(`diagram badges and flow node instance history panel - ${theme}`, async ({
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
          metaData: instanceWithIncident.metaData,
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
          name: /modify instance/i,
        })
        .click();

      await page
        .getByRole('button', {
          name: /continue/i,
        })
        .click();

      await page
        .getByRole('button', {
          name: /reset diagram zoom/i,
        })
        .click();

      await processInstancePage.diagram.clickFlowNode('check payment');

      await expect(page.getByTestId('dropdown-spinner')).not.toBeVisible();

      await page
        .getByTitle(
          /move selected instance in this flow node to another target/i,
        )
        .click();

      await processInstancePage.diagram.clickFlowNode('check order items');
      await processInstancePage.diagram.clickFlowNode('check payment');
      await page
        .getByRole('button', {
          name: /add single flow node instance/i,
        })
        .click();

      await expect(page).toHaveScreenshot();
    });

    test(`apply modifications summary modal - ${theme}`, async ({
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
          metaData: instanceWithIncident.metaData,
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
          name: /modify instance/i,
        })
        .click();

      await page
        .getByRole('button', {
          name: /continue/i,
        })
        .click();

      await processInstancePage.diagram.clickFlowNode('check payment');

      await expect(page.getByTestId('dropdown-spinner')).not.toBeVisible();

      await page
        .getByTitle(
          /move selected instance in this flow node to another target/i,
        )
        .click();

      await processInstancePage.diagram.clickFlowNode('check order items');

      const firstVariableValueInput = page
        .getByRole('textbox', {
          name: /value/i,
        })
        .nth(0);

      await firstVariableValueInput.clear();
      await firstVariableValueInput.fill('"test"');
      await page.keyboard.press('Tab');

      await page
        .getByRole('button', {
          name: /apply modifications/i,
        })
        .click();

      await expect(
        page.getByText(/planned modifications for process instance/i),
      ).toBeVisible();

      await expect(
        page.getByRole('button', {
          name: /delete flow node modification/i,
        }),
      ).toBeVisible();

      await expect(
        page.getByRole('button', {
          name: /delete variable modification/i,
        }),
      ).toBeVisible();

      await expect(page).toHaveScreenshot();
    });
  }
});
