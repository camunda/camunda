/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import {expect} from '@playwright/test';

import {
  mockResponses as mockProcessDetailResponses,
  eventBasedGatewayProcessInstance,
} from '../mocks/processInstance';
import {URL_API_PATTERN} from '../constants';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('process instance modification', () => {
  test('add and cancel modification', async ({
    page,
    commonPage,
    processInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: eventBasedGatewayProcessInstance.detail,
        processInstanceDetailV2: eventBasedGatewayProcessInstance.detailV2,
        callHierarchy: eventBasedGatewayProcessInstance.callHierarchy,
        elementInstances: eventBasedGatewayProcessInstance.elementInstances,
        statistics: eventBasedGatewayProcessInstance.statistics,
        sequenceFlows: eventBasedGatewayProcessInstance.sequenceFlows,
        sequenceFlowsV2: eventBasedGatewayProcessInstance.sequenceFlowsV2,
        variables: [],
        incidents: eventBasedGatewayProcessInstance.incidents,
        incidentsV2: eventBasedGatewayProcessInstance.incidentsV2,
        xml: eventBasedGatewayProcessInstance.xml,
        metaData: eventBasedGatewayProcessInstance.metaData,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '2251799813888430',
    });

    const modifyInstanceButton = await page.getByRole('button', {
      name: /modify instance/i,
    });

    await commonPage.addUpArrow(modifyInstanceButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/enter-modification-mode.png',
    });

    await commonPage.deleteArrows();
    await modifyInstanceButton.click();

    await page.getByRole('button', {name: 'Continue'}).click();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/modification-mode.png',
    });

    await page.getByTestId('diagram').getByText('Message task').nth(0).click();

    await expect(
      page.getByRole('button', {
        name: /cancel selected instance in this flow node/i,
      }),
    ).toBeVisible();

    const cancelTokenButton = await page.getByRole('button', {
      name: /cancel selected instance in this flow node/i,
    });

    await commonPage.addRightArrow(cancelTokenButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/cancel-token.png',
    });

    await commonPage.deleteArrows();
    await cancelTokenButton.click();

    const messageTaskTreeItem = await page.getByRole('treeitem', {
      name: /Message task/i,
    });

    await commonPage.addLeftArrow(messageTaskTreeItem);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/cancel-token-result.png',
    });

    await commonPage.deleteArrows();

    await page.getByText('Last task').click();

    await expect(
      page.getByRole('button', {name: /add single flow node instance/i}),
    ).toBeVisible();

    const addTokenButton = await page.getByRole('button', {
      name: /add single flow node instance/i,
    });

    await commonPage.addLeftArrow(addTokenButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/add-token.png',
    });
    await commonPage.deleteArrows();
    await addTokenButton.click();

    const lastTaskTreeItem = await page.getByRole('treeitem', {
      name: /Last task/i,
    });

    await commonPage.addLeftArrow(lastTaskTreeItem);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/add-token-result.png',
    });
  });

  test('move modification', async ({page, commonPage, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: eventBasedGatewayProcessInstance.detail,
        processInstanceDetailV2: eventBasedGatewayProcessInstance.detailV2,
        callHierarchy: eventBasedGatewayProcessInstance.callHierarchy,
        elementInstances: eventBasedGatewayProcessInstance.elementInstances,
        statistics: eventBasedGatewayProcessInstance.statistics,
        sequenceFlows: eventBasedGatewayProcessInstance.sequenceFlows,
        sequenceFlowsV2: eventBasedGatewayProcessInstance.sequenceFlowsV2,
        variables: [],
        incidents: eventBasedGatewayProcessInstance.incidents,
        incidentsV2: eventBasedGatewayProcessInstance.incidentsV2,
        xml: eventBasedGatewayProcessInstance.xml,
        metaData: eventBasedGatewayProcessInstance.metaData,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '2251799813888430',
    });

    await page
      .getByRole('button', {
        name: /modify instance/i,
      })
      .click();

    await page.getByRole('button', {name: 'Continue'}).click();

    await page.getByTestId('diagram').getByText('Message task').nth(0).click();

    await expect(
      page.getByRole('button', {
        name: /move selected instance/i,
      }),
    ).toBeVisible();

    const moveTokenButton = await page.getByRole('button', {
      name: /move selected instance/i,
    });

    await commonPage.addLeftArrow(moveTokenButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/move-token.png',
    });

    await commonPage.deleteArrows();

    await moveTokenButton.click();

    const lastTaskFlowNode = await page
      .getByTestId('diagram')
      .getByText('Last task');

    await commonPage.addDownArrow(lastTaskFlowNode);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/move-token-select-target.png',
    });

    await commonPage.deleteArrows();

    await lastTaskFlowNode.click();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/move-token-result.png',
    });

    const lastTaskTreeItem = await page
      .getByTestId('instance-history')
      .getByRole('treeitem', {name: 'Last task'});

    await lastTaskTreeItem.click();

    await commonPage.addLeftArrow(lastTaskTreeItem);

    await expect(page.getByText('No modifications available')).toBeVisible();

    //'No modifications available' text was not visible in the screenshot without randomly waiting a while
    await page.waitForTimeout(1000);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/select-new-scope.png',
    });

    await commonPage.deleteArrows();

    const addVariableButton = await page.getByRole('button', {
      name: /add variable/i,
    });

    await commonPage.addRightArrow(addVariableButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/add-variable-to-new-scope.png',
    });

    await commonPage.deleteArrows();

    await addVariableButton.click();

    await processInstancePage.newVariableNameField.fill('test');
    await processInstancePage.newVariableValueField.fill('"some value"');

    await page.getByTestId('variables-list').click();

    const undoButton = await page.getByRole('button', {name: /undo/i});

    await commonPage.addLeftArrow(undoButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/add-variable-result.png',
    });

    await commonPage.deleteArrows();

    const nodeDetails = await page.getByTestId('node-details-2251799813888430');

    await page.route(
      URL_API_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: eventBasedGatewayProcessInstance.detail,
        processInstanceDetailV2: eventBasedGatewayProcessInstance.detailV2,
        callHierarchy: eventBasedGatewayProcessInstance.callHierarchy,
        elementInstances: eventBasedGatewayProcessInstance.elementInstances,
        statistics: eventBasedGatewayProcessInstance.statistics,
        sequenceFlows: eventBasedGatewayProcessInstance.sequenceFlows,
        sequenceFlowsV2: eventBasedGatewayProcessInstance.sequenceFlowsV2,
        variables: [
          {
            variableKey: '2251799813888430-test',
            name: 'test',
            value: '123',
            isTruncated: false,
            tenantId: '',
            processInstanceKey: '2251799813888430',
            scopeKey: '2251799813888430',
          },
        ],
        incidents: eventBasedGatewayProcessInstance.incidents,
        incidentsV2: eventBasedGatewayProcessInstance.incidentsV2,
        xml: eventBasedGatewayProcessInstance.xml,
        metaData: eventBasedGatewayProcessInstance.metaData,
      }),
    );

    await nodeDetails.click();

    await commonPage.addLeftArrow(nodeDetails);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/edit-variable-on-existing-scope.png',
    });

    await commonPage.deleteArrows();

    const editVariableValueField =
      await processInstancePage.editVariableValueField;

    await commonPage.addRightArrow(editVariableValueField);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/edit-variable-value.png',
    });

    await commonPage.deleteArrows();

    editVariableValueField.fill('1234');

    await page.getByTestId('variables-list').click();

    await commonPage.addLeftArrow(undoButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/edit-variable-result.png',
    });
    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/undo-modification.png',
    });

    await commonPage.deleteArrows();

    const applyModificationsButton = await page.getByRole('button', {
      name: /apply modifications/i,
    });

    await commonPage.addDownArrow(applyModificationsButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/apply-modifications-button.png',
    });

    await commonPage.deleteArrows();
    await page.getByRole('button', {name: /apply modifications/i}).click();

    await expect(
      page.getByText(/Planned modifications for Process Instance/i),
    ).toBeVisible();
    await expect(page.getByRole('cell', {name: /^move$/i})).toBeVisible();
    await expect(page.getByRole('cell', {name: /^add$/i})).toBeVisible();
    await expect(
      page.getByRole('cell', {name: /^edit$/i, exact: true}),
    ).toBeVisible();

    // text content inside the modal was not visible in the screenshot without randomly waiting a while
    await page.waitForTimeout(1000);

    const deleteFlowNodeModificationButton = await page.getByRole('button', {
      name: /delete flow node modification/i,
    });

    await commonPage.addDownArrow(deleteFlowNodeModificationButton, '(1)');

    const expandCurrentRowButton = await page
      .getByRole('button', {
        name: /Expand current row/i,
      })
      .nth(0);

    await commonPage.addDownArrow(expandCurrentRowButton, '(2)');

    const closeButton = await page.getByRole('button', {
      name: /close/i,
    });

    await commonPage.addRightArrow(closeButton, '(3)');

    const cancelButton = await page.getByRole('button', {
      name: /cancel/i,
    });

    await commonPage.addDownArrow(cancelButton, '(3)');

    const applyButton = await page.getByRole('button', {
      name: 'Apply',
      exact: true,
    });

    await commonPage.addDownArrow(applyButton, '(4)');

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/modification-summary-modal.png',
    });

    await page.getByRole('button', {name: 'Apply', exact: true}).click();

    await commonPage.deleteArrows();

    await expect(page.getByText(/Modifications applied/)).toBeVisible();

    // waiting for notification to be visible
    await page.waitForTimeout(1000);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/applied-modifications.png',
    });
  });

  test('non supported modifications', async ({
    page,
    commonPage,
    processInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessDetailResponses({
        processInstanceDetail: eventBasedGatewayProcessInstance.detail,
        processInstanceDetailV2: eventBasedGatewayProcessInstance.detailV2,
        callHierarchy: eventBasedGatewayProcessInstance.callHierarchy,
        elementInstances: eventBasedGatewayProcessInstance.elementInstances,
        statistics: eventBasedGatewayProcessInstance.statistics,
        sequenceFlows: eventBasedGatewayProcessInstance.sequenceFlows,
        sequenceFlowsV2: eventBasedGatewayProcessInstance.sequenceFlowsV2,
        variables: [],
        incidents: eventBasedGatewayProcessInstance.incidents,
        incidentsV2: eventBasedGatewayProcessInstance.incidentsV2,
        xml: eventBasedGatewayProcessInstance.xml,
        metaData: eventBasedGatewayProcessInstance.metaData,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '2251799813888430',
    });

    await page
      .getByRole('button', {
        name: /modify instance/i,
      })
      .click();

    await page.getByRole('button', {name: 'Continue'}).click();

    const timerEvent = await page.locator('[data-element-id="timerEvent"]');

    await commonPage.addDownArrow(timerEvent);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/process-instance-modification/not-supported-flow-nodes.png',
    });
  });
});
