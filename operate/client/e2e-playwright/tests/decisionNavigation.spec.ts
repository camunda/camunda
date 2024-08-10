/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './decisionNavigation.mocks';
import {test} from '../test-fixtures';
import {SETUP_WAITING_TIME} from './constants';
import {expect} from '@playwright/test';
import {config} from '../config';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  test.setTimeout(SETUP_WAITING_TIME);
  initialData = await setup();

  await Promise.all([
    ...initialData.decisionKeys.map(
      async (decisionKey) =>
        await expect
          .poll(
            async () => {
              const response = await request.get(
                `${config.endpoint}/v1/decision-definitions/${decisionKey}`,
              );

              return response.status();
            },
            {timeout: SETUP_WAITING_TIME},
          )
          .toBe(200),
    ),
    expect
      .poll(
        async () => {
          const response = await request.get(
            `${config.endpoint}/v1/process-instances/${initialData.processInstanceWithFailedDecision.processInstanceKey}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200),
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/api/process-instances/${initialData.processInstanceWithFailedDecision.processInstanceKey}/flow-node-metadata`,
            {
              data: {
                flowNodeId: 'Activity_1tjwahx',
              },
            },
          );
          const metaData = await response.json();
          return metaData.incidentCount;
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(1),
  ]);
});

test.beforeEach(async ({dashboardPage}) => {
  dashboardPage.navigateToDashboard();
});

test.describe('Decision Navigation', () => {
  test('Navigation between process and decision', async ({page}) => {
    const processInstanceKey =
      initialData.processInstanceWithFailedDecision.processInstanceKey;

    await page
      .getByRole('link', {
        name: /processes/i,
      })
      .click();

    await page
      .getByRole('link', {
        name: processInstanceKey,
      })
      .click();

    await expect(page.getByTestId('diagram')).toBeInViewport();
    await expect(
      page.getByTestId('diagram').getByText(/define approver/i),
    ).toBeVisible();

    await page.getByTestId('diagram').getByText('Define approver').click();

    await expect(page.getByTestId('popover')).toBeVisible();
    await page
      .getByRole('link', {
        name: /view root cause decision invoice classification/i,
      })
      .click();

    await expect(page.getByTestId('decision-panel')).toBeVisible();
    await expect(
      page.getByTestId('decision-panel').getByText('Invoice Amount'),
    ).toBeVisible();

    const calledDecisionInstanceId = await page
      .getByTestId('instance-header')
      .getByRole('cell')
      .nth(6)
      .innerText();

    await page.getByRole('button', {name: /close drd panel/i}).click();
    await page
      .getByRole('link', {
        name: `View process instance ${processInstanceKey}`,
      })
      .click();

    await expect(page.getByTestId('instance-header')).toBeVisible();

    await expect(
      page
        .getByTestId('instance-header')
        .getByText(processInstanceKey, {exact: true}),
    ).toBeVisible();

    await expect(page.getByTestId('diagram')).toBeInViewport();

    await expect(
      page.getByTestId('diagram').getByText(/define approver/i),
    ).toBeVisible();

    await page
      .getByRole('link', {
        name: /decisions/i,
      })
      .click();

    await page
      .getByRole('link', {
        name: `View decision instance ${calledDecisionInstanceId}`,
      })
      .click();
    await expect(page.getByTestId('decision-panel')).toBeVisible();
    await expect(
      page.getByTestId('decision-panel').getByText('Invoice Amount'),
    ).toBeVisible();
  });
});
