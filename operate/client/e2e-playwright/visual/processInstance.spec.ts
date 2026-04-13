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
        callHierarchy: runningInstance.callHierarchy,
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: runningInstance.detail.processInstanceKey,
    });

    await expect(processInstancePage.variablesTableSpinner).not.toBeVisible();
    await processInstancePage.resetZoomButton.click();

    await expect(
      page.getByText('Variables could not be fetched'),
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('helper modal', async ({page}) => {
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

    await page.goto(
      `/operate/processes/${runningInstance.detail.processInstanceKey}`,
    );

    await expect(page.getByText("Here's what moved in Operate")).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('running instance', async ({page, processInstancePage}) => {
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
        incidents: runningInstance.incidents,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: runningInstance.detail.processInstanceKey,
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
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await page
      .getByRole('button', {
        name: /edit/i,
      })
      .click();
    await processInstancePage.variablesEditor.waitForEditorToLoad();

    await expect(page).toHaveScreenshot();
  });

  test('instance with incident', async ({page, processInstancePage}) => {
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
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await page.getByRole('link', {name: 'Incidents'}).click();

    await expect(page).toHaveScreenshot();
  });

  test('instance with incident expanded row', async ({
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
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await page.getByRole('link', {name: 'Incidents'}).click();

    await page.getByRole('button', {name: /expand current row/i}).click();
    await expect(page.getByText('Job ID')).toBeVisible();
    await expect(page.getByText('Error message')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('completed instance', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: completedInstance.detail,
        callHierarchy: completedInstance.callHierarchy,
        elementInstances: completedInstance.elementInstances,
        statistics: completedInstance.statistics,
        sequenceFlows: completedInstance.sequenceFlows,
        variables: completedInstance.variables,
        xml: completedInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: completedInstance.detail.processInstanceKey,
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
        callHierarchy: compensationProcessInstance.callHierarchy,
        elementInstances: compensationProcessInstance.elementInstances,
        statistics: compensationProcessInstance.statistics,
        sequenceFlows: compensationProcessInstance.sequenceFlows,
        variables: compensationProcessInstance.variables,
        xml: compensationProcessInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: compensationProcessInstance.detail.processInstanceKey,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await processInstancePage.executionCountToggle.click({force: true});

    await processInstancePage.endDateToggle.click({force: true});

    await expect(page).toHaveScreenshot();
  });

  test('inline JSON view - running instance', async ({
    page,
    processInstancePage,
  }) => {
    const jsonVariable = {
      variableKey: '2251799813687144-payload',
      name: 'payload',
      value: '{"status":"active","count":42}',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '2251799813687144',
      scopeKey: '2251799813687144',
      rootProcessInstanceKey: null,
    };

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: [jsonVariable],
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: runningInstance.detail.processInstanceKey,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await expect(page.getByTestId('variable-payload')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('inline JSON view - scrollable', async ({page, processInstancePage}) => {
    const longJsonVariable = {
      variableKey: '2251799813687144-longPayload',
      name: 'longPayload',
      value: '{"a":1,"b":2,"c":3,"d":4,"e":5,"f":6}',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '2251799813687144',
      scopeKey: '2251799813687144',
      rootProcessInstanceKey: null,
    };

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: [longJsonVariable],
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: runningInstance.detail.processInstanceKey,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await expect(page.getByTestId('variable-longPayload')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('inline JSON edit - error state', async ({
    page,
    processInstancePage,
  }) => {
    const jsonVariable = {
      variableKey: '2251799813687144-payload',
      name: 'payload',
      value: '{"status":"active","count":42}',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '2251799813687144',
      scopeKey: '2251799813687144',
      rootProcessInstanceKey: null,
    };

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: [jsonVariable],
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: runningInstance.detail.processInstanceKey,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await page
      .getByTestId('variable-payload')
      .getByRole('button', {name: /^edit$/i})
      .click();

    await processInstancePage.variablesEditor.waitForEditorToLoad();

    await processInstancePage.variablesEditor.clear();
    await processInstancePage.variablesEditor.fill('{invalid');

    await expect(page.getByText('Value has to be JSON')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('inline JSON edit - before focusing', async ({
    page,
    processInstancePage,
  }) => {
    const jsonVariable = {
      variableKey: '2251799813687144-payload',
      name: 'payload',
      value: '{"status":"active","count":42}',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '2251799813687144',
      scopeKey: '2251799813687144',
      rootProcessInstanceKey: null,
    };

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: [jsonVariable],
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: runningInstance.detail.processInstanceKey,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await page
      .getByTestId('variable-payload')
      .getByRole('button', {name: /^edit$/i})
      .click();

    await processInstancePage.variablesEditor.waitForEditorToLoad();

    await expect(page).toHaveScreenshot();
  });

  test('inline JSON edit - error state after blurring', async ({
    page,
    processInstancePage,
  }) => {
    const jsonVariable = {
      variableKey: '2251799813687144-payload',
      name: 'payload',
      value: '{"status":"active","count":42}',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '2251799813687144',
      scopeKey: '2251799813687144',
      rootProcessInstanceKey: null,
    };

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: [jsonVariable],
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: runningInstance.detail.processInstanceKey,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await page
      .getByTestId('variable-payload')
      .getByRole('button', {name: /^edit$/i})
      .click();

    await processInstancePage.variablesEditor.waitForEditorToLoad();

    await processInstancePage.variablesEditor.clear();
    await processInstancePage.variablesEditor.fill('{invalid');

    await expect(page.getByText('Value has to be JSON')).toBeVisible();

    // Blur the editor by clicking elsewhere
    await page.getByText('Variables').click();

    await expect(page.getByText('Value has to be JSON')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('JSON viewer modal - running instance - view mode', async ({
    page,
    processInstancePage,
  }) => {
    const jsonVariable = {
      variableKey: '2251799813687144-payload',
      name: 'payload',
      value: '{"status":"active","count":42}',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '2251799813687144',
      scopeKey: '2251799813687144',
      rootProcessInstanceKey: null,
    };

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: [jsonVariable],
        xml: runningInstance.xml,
        variable: jsonVariable,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: runningInstance.detail.processInstanceKey,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await page
      .getByTestId('variable-payload')
      .getByRole('button', {name: /^open/i})
      .click();

    await expect(page.getByRole('dialog')).toBeVisible();
    await processInstancePage.variablesEditor.waitForEditorToLoad();

    await expect(page).toHaveScreenshot();
  });

  test('JSON viewer modal - running instance - edit mode', async ({
    page,
    processInstancePage,
  }) => {
    const jsonVariable = {
      variableKey: '2251799813687144-payload',
      name: 'payload',
      value: '{"status":"active","count":42}',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '2251799813687144',
      scopeKey: '2251799813687144',
      rootProcessInstanceKey: null,
    };

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: [jsonVariable],
        xml: runningInstance.xml,
        variable: jsonVariable,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: runningInstance.detail.processInstanceKey,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);
    await expect(page.getByTestId(/^state-overlay/)).toHaveText('1');

    await page
      .getByTestId('variable-payload')
      .getByRole('button', {name: /^edit$/i})
      .click();

    await processInstancePage.variablesEditor.waitForEditorToLoad();

    await page
      .getByTestId('variable-payload')
      .getByRole('button', {name: /^open/i})
      .click();

    await expect(page.getByRole('dialog')).toBeVisible();
    await page
      .locator('.monaco-editor .cursor')
      .first()
      .waitFor({state: 'visible'});

    await expect(page).toHaveScreenshot();
  });

  test('JSON viewer modal - completed instance', async ({
    page,
    processInstancePage,
  }) => {
    const jsonVariable = {
      variableKey: '2551799813954282-payload',
      name: 'payload',
      value: '{"result":"success","items":3}',
      isTruncated: false,
      tenantId: '<default>',
      processInstanceKey: '2551799813954282',
      scopeKey: '2551799813954282',
      rootProcessInstanceKey: null,
    };

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: completedInstance.detail,
        callHierarchy: completedInstance.callHierarchy,
        elementInstances: completedInstance.elementInstances,
        statistics: completedInstance.statistics,
        sequenceFlows: completedInstance.sequenceFlows,
        variables: [jsonVariable],
        xml: completedInstance.xml,
        variable: jsonVariable,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: completedInstance.detail.processInstanceKey,
    });
    await processInstancePage.resetZoomButton.click();
    await page.waitForTimeout(500);

    await page
      .getByTestId('variable-payload')
      .getByRole('button', {name: /^open/i})
      .click();

    await expect(page.getByRole('dialog')).toBeVisible();
    await processInstancePage.variablesEditor.waitForEditorToLoad();

    await expect(page).toHaveScreenshot();
  });
});
