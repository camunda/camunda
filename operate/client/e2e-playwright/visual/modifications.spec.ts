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
import {takePercySnapshot} from '../utils/percy';

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
  test('with helper modal', async ({page, processInstancePage}) => {
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

    await page
      .getByRole('button', {
        name: /modify instance/i,
      })
      .click();

    await expect(page).toHaveScreenshot();
    await takePercySnapshot(page, 'Modifications - with helper modal');
  });

  test('with add variable state', async ({page, processInstancePage}) => {
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
    await processInstancePage.diagram
      .getFlowNodeById('Activity_0dex012')
      .click();
    await page
      .getByRole('button', {name: 'Add single element instance'})
      .click();

    await processInstancePage.addVariableButton.click();

    await expect(page).toHaveScreenshot();
    await takePercySnapshot(page, 'Modifications - with add variable state');
  });

  test('diagram badges and element instance history panel', async ({
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
        xml: instanceWithIncident.xml,
        incidents: instanceWithIncident.incidents,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: instanceWithIncident.detail.processInstanceKey,
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

    await page
      .getByRole('button', {
        name: /reset diagram zoom/i,
      })
      .click();

    await processInstancePage.diagram.clickElement('check payment');

    await expect(page.getByTestId('dropdown-spinner')).not.toBeVisible();

    await page
      .getByTitle(/move selected instance in this element to another target/i)
      .click();

    await processInstancePage.diagram.clickElement('check order items');
    await processInstancePage.diagram.clickElement('check payment');
    await page
      .getByRole('button', {
        name: /Add single element instance/i,
      })
      .click();

    await expect(page).toHaveScreenshot();
    await takePercySnapshot(page, 'Modifications - diagram badges and element instance history panel');
  });

  test('apply modifications summary modal', async ({
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

    await processInstancePage.diagram.clickElement('check payment');

    await expect(page.getByTestId('dropdown-spinner')).not.toBeVisible();

    await page
      .getByTitle(/move selected instance in this element to another target/i)
      .click();

    await processInstancePage.diagram.clickElement('check order items');

    await page.getByRole('link', {name: /variables/i}).click();

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
        name: /review modifications/i,
      })
      .click();

    await expect(
      page.getByText(/planned modifications for process instance/i),
    ).toBeVisible();

    await expect(
      page.getByRole('button', {
        name: /delete element modification/i,
      }),
    ).toBeVisible();

    await expect(
      page.getByRole('button', {
        name: /delete variable modification/i,
      }),
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
    await takePercySnapshot(page, 'Modifications - apply modifications summary modal');
  });
});
