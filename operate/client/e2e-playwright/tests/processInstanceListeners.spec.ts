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
});
