/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import {
  instanceWithIncident,
  mockResponses,
  runningInstance,
} from '../mocks/processInstance';
import {validateResults} from './validateResults';
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

test.describe('process detail', () => {
  test('have no violations for running instance', async ({
    page,
    processInstancePage,
    makeAxeBuilder,
  }) => {
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
        xml: runningInstance.xml,
        metaData: runningInstance.metaData,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '1',
    });

    // TODO: Enable 'aria-required-parent' and 'list' rules when https://github.com/carbon-design-system/carbon/issues/14944 is implemented and necessary changes are made in our code base.
    const results = await makeAxeBuilder()
      .disableRules(['aria-required-parent', 'list'])
      .analyze();

    validateResults(results);

    await processInstancePage.diagram.clickFlowNode('signal user task');

    const resultsWithMetadataPopover = await makeAxeBuilder()
      .disableRules(['aria-required-parent', 'list'])
      .analyze();

    validateResults(resultsWithMetadataPopover);
  });

  test('have no violations for instance with incident', async ({
    page,
    processInstancePage,
    makeAxeBuilder,
  }) => {
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
        metaData: instanceWithIncident.metaData,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '1',
    });

    // TODO: Enable 'aria-required-parent' and 'list' rules when https://github.com/carbon-design-system/carbon/issues/14944 is implemented and necessary changes are made in our code base.
    const results = await makeAxeBuilder()
      .disableRules(['aria-required-parent', 'list'])
      .analyze();

    validateResults(results);

    // edit variable state
    const resultsWithEditVariableState = await makeAxeBuilder()
      .disableRules(['aria-required-parent', 'list'])
      .analyze();

    await page
      .getByRole('button', {name: /edit variable loopCounter/i})
      .click();

    validateResults(resultsWithEditVariableState);
    await page.getByRole('button', {name: /exit edit mode/i}).click();

    // add variable state
    const resultsWithAddVariableState = await makeAxeBuilder()
      .disableRules(['aria-required-parent', 'list'])
      .analyze();

    await processInstancePage.addVariableButton.click();
    validateResults(resultsWithAddVariableState);

    // meta data popover visible
    await processInstancePage.diagram.clickFlowNode('check payment');

    const resultsWithMetadataPopover = await makeAxeBuilder()
      .disableRules(['aria-required-parent', 'list'])
      .analyze();

    validateResults(resultsWithMetadataPopover);
  });

  test('have no violations in modification mode', async ({
    page,
    processInstancePage,
    makeAxeBuilder,
  }) => {
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
        metaData: runningInstance.metaData,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '1',
    });

    await page.getByRole('button', {name: /modify instance/i}).click();
    const results = await makeAxeBuilder()
      .disableRules(['aria-required-parent', 'list'])
      .analyze();

    validateResults(results);

    await page.getByRole('button', {name: /continue/i}).click();
    await page.getByTestId('diagram').getByText('Signal user task').click();
    await page
      .getByRole('button', {name: 'Add single flow node instance'})
      .click();

    const modificationModeResults = await makeAxeBuilder()
      .disableRules(['aria-required-parent', 'list'])
      .analyze();

    await processInstancePage.addVariableButton.click();

    validateResults(modificationModeResults);
  });
});
