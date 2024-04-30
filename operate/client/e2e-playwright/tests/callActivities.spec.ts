/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './callActivities.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {config} from '../config';
import {SETUP_WAITING_TIME} from './constants';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  test.setTimeout(SETUP_WAITING_TIME);

  initialData = await setup();
  await expect
    .poll(
      async () => {
        const response = await request.get(
          `${config.endpoint}/v1/process-instances/${initialData.callActivityProcessInstance.processInstanceKey}`,
        );

        return response.status();
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBe(200);
});

test.describe('Call Activities', () => {
  test('Navigate to called and parent process instances', async ({
    processInstancePage,
    page,
  }) => {
    const processInstanceKey =
      initialData.callActivityProcessInstance.processInstanceKey;

    const {instanceHeader, diagram, instanceHistory} = processInstancePage;

    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    await expect(page.getByTestId('instance-header-skeleton')).toBeHidden();

    await expect(instanceHeader).toBeVisible();
    await expect(instanceHeader.getByText(processInstanceKey)).toBeVisible();

    await expect(
      instanceHeader.getByText('Call Activity Process'),
    ).toBeVisible();

    await page.getByRole('link', {name: /view all called instances/i}).click();

    const instancesList = page.getByTestId('data-list');

    await expect(instancesList.getByRole('row')).toHaveCount(1);
    await expect(instancesList.getByText('Called Process')).toBeVisible();

    const calledProcessInstanceId = await instancesList
      .getByRole('row')
      .nth(0)
      .getByRole('cell')
      .nth(2)
      .innerText();

    // Navigate to call activity instance
    await instancesList
      .getByRole('link', {
        name: /view parent instance/i,
      })
      .click();

    // Expect correct header
    await expect(instanceHeader.getByText(processInstanceKey)).toBeVisible();

    await expect(
      instanceHeader.getByText('Call Activity Process'),
    ).toBeVisible();

    // Expect correct instance history
    await expect(
      instanceHistory.getByText('Call Activity Process'),
    ).toBeVisible();
    await expect(instanceHistory.getByText('StartEvent_1')).toBeVisible();
    await expect(
      instanceHistory.getByText('Call Activity', {
        exact: true,
      }),
    ).toBeVisible();
    await expect(instanceHistory.getByText('Event_1p0nsc7')).toBeVisible();

    // Expect correct diagram

    await expect(diagram.getFlowNode('call activity')).toBeVisible();

    // Navigate to called process instance
    await diagram.clickFlowNode('call Activity');

    const popover = page.getByTestId('popover');

    await expect(popover.getByText(/Called Process Instance/)).toBeVisible();

    await popover
      .getByRole('link', {
        name: /view called process instance/i,
      })
      .click();

    // Expect correct header
    await expect(
      instanceHeader.getByText(calledProcessInstanceId),
    ).toBeVisible();
    await expect(
      instanceHeader.getByText('Called Process', {
        exact: true,
      }),
    ).toBeVisible();

    // Expect correct instance history
    await expect(
      instanceHistory.getByText('Called Process', {
        exact: true,
      }),
    ).toBeVisible();
    await expect(instanceHistory.getByText('Process started')).toBeVisible();
    await expect(instanceHistory.getByText('Event_0y6k56d')).toBeVisible();

    // Expect correct diagram
    await expect(diagram.getFlowNode('Process started')).toBeVisible();

    // Navigate to parent instance
    await instanceHeader
      .getByRole('link', {
        name: /view parent instance/i,
      })
      .click();

    // Expect correct header
    await expect(instanceHeader.getByText(processInstanceKey)).toBeVisible();

    await expect(
      instanceHeader.getByText('Call Activity Process'),
    ).toBeVisible();

    // Expect correct instance history
    await expect(
      instanceHistory.getByText('Call Activity Process'),
    ).toBeVisible();
    await expect(instanceHistory.getByText('StartEvent_1')).toBeVisible();
    await expect(
      instanceHistory.getByText('Call Activity', {
        exact: true,
      }),
    ).toBeVisible();
    await expect(instanceHistory.getByText('Event_1p0nsc7')).toBeVisible();

    await expect(diagram.getFlowNode('Call Activity')).toBeVisible();
  });
});
