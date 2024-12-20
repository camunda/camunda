/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './processInstanceListeners.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {SETUP_WAITING_TIME} from './constants';
import {config} from '../config';
import {zeebeRestApi} from '../api/zeebe-rest';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  const {processWithListenerInstance} = initialData;

  await expect
    .poll(
      async () => {
        const response = await request.get(
          `${config.endpoint}/v1/process-instances/${processWithListenerInstance.processInstanceKey}`,
        );

        return response.status();
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBe(200);
});

test.describe('Process Instance Listeners', () => {
  test('Listeners tab should always be visible', async ({
    page,
    processInstancePage,
  }) => {
    const processInstanceKey =
      initialData.processWithListenerInstance.processInstanceKey;
    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    await expect(processInstancePage.listenersTabButton).toBeVisible();

    // Start flow node should NOT enable listeners tab
    await processInstancePage.instanceHistory.getByText('StartEvent_1').click();
    await expect(processInstancePage.listenersTabButton).toBeVisible();

    // Service task flow node should enable listeners tab
    await processInstancePage.instanceHistory
      .getByText(/service task b/i)
      .click();
    await expect(processInstancePage.listenersTabButton).toBeVisible();
  });

  test('Listeners data displayed', async ({page, processInstancePage}) => {
    const processInstanceKey =
      initialData.processWithListenerInstance.processInstanceKey;
    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    await processInstancePage.instanceHistory
      .getByText(/service task b/i)
      .click();
    await processInstancePage.listenersTabButton.click();

    await expect(page.getByText('Execution listener')).toBeVisible();
  });

  test('Listeners list filtered by flow node instance', async ({
    page,
    processInstancePage,
  }) => {
    const processInstanceKey =
      initialData.processWithListenerInstance.processInstanceKey;
    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    // select flow node in diagram, check amount of listeners and add a token to it
    await processInstancePage.diagram.clickFlowNode('Service Task B');
    await processInstancePage.listenersTabButton.click();
    expect(
      await page
        .getByRole('row')
        .filter({hasText: /execution listener/i})
        .count(),
    ).toBe(1);
    await processInstancePage.modifyInstanceButton.click();
    await page.getByRole('button', {name: 'Continue'}).click();
    await processInstancePage.diagram.clickFlowNode('Service Task B');
    await page
      .getByRole('button', {name: 'Add single flow node instance'})
      .click();
    await page.getByRole('button', {name: 'Apply Modifications'}).click();
    //confirm modal
    await page.getByRole('button', {name: 'Apply', exact: true}).click();

    // wait for new instance to appear on instance history
    const instanceHistoryPanel = page.getByTestId('instance-history');
    await expect
      .poll(async () => {
        return instanceHistoryPanel.getByText('Service Task B').count();
      })
      .toBe(2);

    // select flow node again, it should have 2 listeners (1 for each instance)
    await processInstancePage.diagram.clickFlowNode('Service Task B');
    await processInstancePage.listenersTabButton.click();
    await expect(
      page.getByRole('row').filter({hasText: /execution listener/i}),
    ).toHaveCount(2);

    // select a flow node instance, check it has only 1 corresponding listener
    await instanceHistoryPanel.getByText('Service Task B').first().click();
    await processInstancePage.listenersTabButton.click();
    await expect(
      page.getByRole('row').filter({hasText: /execution listener/i}),
    ).toHaveCount(1);
  });

  test('Listeners list filtered by listener type @roundtrip', async ({
    page,
    processInstancePage,
  }) => {
    const processInstanceKey =
      initialData.userTaskProcessInstance.processInstanceKey;
    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    const userTaskKeyRegex = new RegExp('\\d{16}');
    let userTaskKey = '';

    const responsePromise = page.waitForResponse('**/flow-node-metadata');

    await expect(
      processInstancePage.diagram.getFlowNode('Service Task B'),
    ).toBeVisible();
    await processInstancePage.diagram.clickFlowNode('Service Task B');

    const response = await responsePromise;
    const data = await response.json();
    userTaskKey = String(data.instanceMetadata.userTaskKey);

    expect(userTaskKey).toMatch(userTaskKeyRegex);

    await expect(page.getByTestId('state-overlay-active')).toBeVisible();

    const {statusCode} = await zeebeRestApi.completeUserTask({userTaskKey});
    expect(statusCode).toBe(204);

    // check if both types of listeners are visible (default filter)
    await processInstancePage.listenersTabButton.click();
    await expect(page.getByText('Execution listener')).toBeVisible();
    await expect(page.getByText('Task listener')).toBeVisible();

    // select only execution listeners on filter
    await processInstancePage.listenerTypeFilter.click();
    await processInstancePage
      .getListenerTypeFilterOption('Execution listeners')
      .click();
    await expect(
      page.getByText('Execution listener', {exact: true}),
    ).toBeVisible();
    await expect(
      page.getByText('Task listener', {exact: true}),
    ).not.toBeVisible();

    // select only task listeners on filter
    await processInstancePage.listenerTypeFilter.click();
    await processInstancePage
      .getListenerTypeFilterOption('User task listeners')
      .click();
    await expect(page.getByText('Task listener', {exact: true})).toBeVisible();
    await expect(
      page.getByText('Execution listener', {exact: true}),
    ).not.toBeVisible();

    // select all listeners on filter
    await processInstancePage.listenerTypeFilter.click();
    await processInstancePage
      .getListenerTypeFilterOption('All listeners')
      .click();
    await expect(
      page.getByText('Execution listener', {exact: true}),
    ).toBeVisible();
    await expect(page.getByText('Task listener', {exact: true})).toBeVisible();
  });

  test('Listeners on process instance or participant (root flow node)', async ({
    page,
    processInstancePage,
  }) => {
    const processInstanceKey =
      initialData.processWithListenerOnRootInstance.processInstanceKey;
    await processInstancePage.navigateToProcessInstance({
      id: processInstanceKey,
    });

    await processInstancePage.modifyInstanceButton.click();
    await page.getByRole('button', {name: 'Continue'}).click();
    await processInstancePage.diagram.clickFlowNode('Service Task B');
    await page
      .getByRole('button', {
        name: 'Move selected instance in this flow node to another target',
      })
      .click();
    await processInstancePage.diagram.clickEvent('End event');
    await page.getByRole('button', {name: 'Apply Modifications'}).click();
    await page.getByRole('button', {name: 'Apply', exact: true}).click();

    await expect
      .poll(async () => {
        await processInstancePage.instanceHistory
          .getByText(/Start event/i)
          .click();
        await processInstancePage.instanceHistory
          .getByText(/processWithListenerOnRoot/i)
          .click();

        try {
          await expect(processInstancePage.listenersTabButton).toBeVisible({
            timeout: 500,
          });
        } catch {
          return false;
        }
        return true;
      })
      .toBe(true);

    await processInstancePage.listenersTabButton.click();

    await expect(page.getByText('Execution listener')).toBeVisible();
  });
});
