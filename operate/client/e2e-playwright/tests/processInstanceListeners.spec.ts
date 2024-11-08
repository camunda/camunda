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

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  const {instance} = initialData;

  await expect
    .poll(
      async () => {
        const response = await request.get(
          `${config.endpoint}/v1/process-instances/${instance.processInstanceKey}`,
        );

        return response.status();
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBe(200);
});

test.describe('Process Instance Listeners', () => {
  test('Listeners tab button show/hide', async ({
    page,
    processInstancePage,
  }) => {
    const processInstanceKey = initialData.instance.processInstanceKey;
    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    // Start flow node should NOT enable listeners tab
    await page.getByLabel('StartEvent_1').click();
    await expect(processInstancePage.listenersTabButton).not.toBeVisible();

    // Service task flow node should enable listeners tab
    await page.getByLabel(/service task b/i).click();
    await expect(processInstancePage.listenersTabButton).toBeVisible();
  });

  test('Listeners data displayed', async ({page, processInstancePage}) => {
    const processInstanceKey = initialData.instance.processInstanceKey;
    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    await page.getByLabel(/service task b/i).click();
    await processInstancePage.listenersTabButton.click();

    await expect(page.getByText('Execution listener')).toBeVisible();
  });

  test('Listeners list filtered by flow node instance', async ({
    page,
    processInstancePage,
  }) => {
    const processInstanceKey = initialData.instance.processInstanceKey;
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
    expect(
      await page
        .getByRole('row')
        .filter({hasText: /execution listener/i})
        .count(),
    ).toBe(2);

    // select a flow node instance, check it has only 1 corresponding listener
    await instanceHistoryPanel.getByText('Service Task B').first().click();
    await processInstancePage.listenersTabButton.click();
    expect(
      await page
        .getByRole('row')
        .filter({hasText: /execution listener/i})
        .count(),
    ).toBe(1);
  });
});
