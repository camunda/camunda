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

test.describe('input output mappings', () => {
  test('input mappings with info banner', async ({
    page,
    processInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: instanceWithIncident.detail,
        callHierarchy: instanceWithIncident.callHierarchy,
        elementInstances: instanceWithIncident.elementInstances,
        statistics: instanceWithIncident.statistics,
        sequenceFlows: instanceWithIncident.sequenceFlows,
        variables: instanceWithIncident.variables,
        xml: instanceWithIncident.xml,
        incidents: instanceWithIncident.incidents,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: instanceWithIncident.detail.processInstanceKey,
    });

    await processInstancePage.resetZoomButton.click();

    await processInstancePage.diagram.clickElement('Check payment');

    await page.getByRole('link', {name: 'Input Mappings'}).click();

    await expect(
      page.getByText('Input mappings are defined while modelling the diagram.'),
    ).toBeVisible();
    await expect(page.getByText('Local Variable Name')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('input mappings after dismissing banner', async ({
    page,
    processInstancePage,
  }) => {
    await page.addInitScript(() => {
      const current = JSON.parse(
        window.localStorage.getItem('sharedState') || '{}',
      );
      window.localStorage.setItem(
        'sharedState',
        JSON.stringify({...current, hideInputMappingsHelperBanner: true}),
      );
    });

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: instanceWithIncident.detail,
        callHierarchy: instanceWithIncident.callHierarchy,
        elementInstances: instanceWithIncident.elementInstances,
        statistics: instanceWithIncident.statistics,
        sequenceFlows: instanceWithIncident.sequenceFlows,
        variables: instanceWithIncident.variables,
        xml: instanceWithIncident.xml,
        incidents: instanceWithIncident.incidents,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: instanceWithIncident.detail.processInstanceKey,
    });

    await processInstancePage.resetZoomButton.click();

    await processInstancePage.diagram.clickElement('Check payment');

    await page.getByRole('link', {name: 'Input Mappings'}).click();

    await expect(page.getByText('Local Variable Name')).toBeVisible();
    await expect(
      page.getByText('Input mappings are defined while modelling the diagram.'),
    ).not.toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('empty input mappings', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: runningInstance.variables,
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: runningInstance.detail.processInstanceKey,
    });

    await processInstancePage.resetZoomButton.click();

    await processInstancePage.diagram.clickElement('Signal user task');

    await page.getByRole('link', {name: 'Input Mappings'}).click();

    await expect(page.getByText('No Input Mappings defined')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('output mappings with info banner', async ({
    page,
    processInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: instanceWithIncident.detail,
        callHierarchy: instanceWithIncident.callHierarchy,
        elementInstances: instanceWithIncident.elementInstances,
        statistics: instanceWithIncident.statistics,
        sequenceFlows: instanceWithIncident.sequenceFlows,
        variables: instanceWithIncident.variables,
        xml: instanceWithIncident.xml,
        incidents: instanceWithIncident.incidents,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: instanceWithIncident.detail.processInstanceKey,
    });

    await processInstancePage.resetZoomButton.click();

    await processInstancePage.diagram.clickElement('Check payment');

    await page.getByRole('link', {name: 'Output Mappings'}).click();

    await expect(
      page.getByText(
        'Output mappings are defined while modelling the diagram.',
      ),
    ).toBeVisible();
    await expect(page.getByText('Process Variable Name')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('output mappings after dismissing banner', async ({
    page,
    processInstancePage,
  }) => {
    await page.addInitScript(() => {
      const current = JSON.parse(
        window.localStorage.getItem('sharedState') || '{}',
      );
      window.localStorage.setItem(
        'sharedState',
        JSON.stringify({...current, hideOutputMappingsHelperBanner: true}),
      );
    });

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: instanceWithIncident.detail,
        callHierarchy: instanceWithIncident.callHierarchy,
        elementInstances: instanceWithIncident.elementInstances,
        statistics: instanceWithIncident.statistics,
        sequenceFlows: instanceWithIncident.sequenceFlows,
        variables: instanceWithIncident.variables,
        xml: instanceWithIncident.xml,
        incidents: instanceWithIncident.incidents,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: instanceWithIncident.detail.processInstanceKey,
    });

    await processInstancePage.resetZoomButton.click();

    await processInstancePage.diagram.clickElement('Check payment');

    await page.getByRole('link', {name: 'Output Mappings'}).click();

    await expect(page.getByText('Process Variable Name')).toBeVisible();
    await expect(
      page.getByText(
        'Output mappings are defined while modelling the diagram.',
      ),
    ).not.toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('empty output mappings', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: runningInstance.variables,
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: runningInstance.detail.processInstanceKey,
    });

    await processInstancePage.resetZoomButton.click();

    await processInstancePage.diagram.clickElement('Signal user task');

    await page.getByRole('link', {name: 'Output Mappings'}).click();

    await expect(page.getByText('No Output Mappings defined')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });
});
