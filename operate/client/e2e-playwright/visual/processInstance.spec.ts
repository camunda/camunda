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
  test('error page', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        processInstanceDetailV2: runningInstance.detailV2,
        callHierarchy: runningInstance.callHierarchy,
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: runningInstance.detail.id,
    });

    await expect(processInstancePage.variablesTableSpinner).not.toBeVisible();
    await processInstancePage.resetZoomButton.click();

    await expect(
      page.getByText('Variables could not be fetched'),
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('running instance', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        processInstanceDetailV2: runningInstance.detailV2,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        sequenceFlowsV2: runningInstance.sequenceFlowsV2,
        variables: runningInstance.variables,
        xml: runningInstance.xml,
        incidents: runningInstance.incidents,
        incidentsV2: runningInstance.incidentsV2,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: runningInstance.detail.id,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await expect(page).toHaveScreenshot();
  });

  test('add variable state', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        processInstanceDetailV2: runningInstance.detailV2,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        sequenceFlowsV2: runningInstance.sequenceFlowsV2,
        variables: runningInstance.variables,
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: runningInstance.detail.id,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await processInstancePage.addVariableButton.click();

    await expect(page).toHaveScreenshot();
  });

  test('edit variable state', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        processInstanceDetailV2: runningInstance.detailV2,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        sequenceFlowsV2: runningInstance.sequenceFlowsV2,
        variables: runningInstance.variables,
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: runningInstance.detail.id,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await page
      .getByRole('button', {
        name: /edit variable/i,
      })
      .click();

    await expect(page).toHaveScreenshot();
  });

  test('instance with incident', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: instanceWithIncident.detail,
        processInstanceDetailV2: instanceWithIncident.detailV2,
        callHierarchy: instanceWithIncident.callHierarchy,
        elementInstances: instanceWithIncident.elementInstances,
        statistics: instanceWithIncident.statistics,
        sequenceFlows: instanceWithIncident.sequenceFlows,
        sequenceFlowsV2: instanceWithIncident.sequenceFlowsV2,
        variables: instanceWithIncident.variables,
        xml: instanceWithIncident.xml,
        incidents: instanceWithIncident.incidents,
        incidentsV2: instanceWithIncident.incidentsV2,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: instanceWithIncident.detail.id,
    });

    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await page
      .getByRole('button', {
        name: /view 1 incident in instance/i,
      })
      .click();

    await expect(page).toHaveScreenshot();
  });

  test('completed instance', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: completedInstance.detail,
        processInstanceDetailV2: completedInstance.detailV2,
        callHierarchy: completedInstance.callHierarchy,
        elementInstances: completedInstance.elementInstances,
        statistics: completedInstance.statistics,
        sequenceFlows: completedInstance.sequenceFlows,
        sequenceFlowsV2: completedInstance.sequenceFlowsV2,
        variables: completedInstance.variables,
        xml: completedInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: completedInstance.detail.id,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await expect(processInstancePage.executionCountToggle).toBeEnabled();
    await processInstancePage.executionCountToggle.click({force: true});

    await processInstancePage.endDateToggle.click({force: true});

    await expect(page).toHaveScreenshot();
  });

  test('compensation process instance', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: compensationProcessInstance.detail,
        processInstanceDetailV2: compensationProcessInstance.detailV2,
        callHierarchy: compensationProcessInstance.callHierarchy,
        elementInstances: compensationProcessInstance.elementInstances,
        statistics: compensationProcessInstance.statistics,
        sequenceFlows: compensationProcessInstance.sequenceFlows,
        sequenceFlowsV2: compensationProcessInstance.sequenceFlowsV2,
        variables: compensationProcessInstance.variables,
        xml: compensationProcessInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: compensationProcessInstance.detail.id,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await processInstancePage.executionCountToggle.click({force: true});

    await processInstancePage.endDateToggle.click({force: true});

    await expect(page).toHaveScreenshot();
  });
});
