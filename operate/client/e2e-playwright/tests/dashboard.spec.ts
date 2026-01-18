/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {config} from '../config';
import {setup} from './dashboard.mocks';
import {test} from '../e2e-fixtures';
import {expect} from '@playwright/test';
import {SETUP_WAITING_TIME} from './constants';

test.beforeAll(async ({request}) => {
  test.setTimeout(SETUP_WAITING_TIME);
  const {instanceIds, instanceWithAnIncident = ''} = await setup();
  // wait for instances and incident to be created
  await Promise.all([
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/api/process-instances`,
            {
              data: {
                query: {
                  active: true,
                  running: true,
                  incidents: true,
                  completed: true,
                  finished: true,
                  canceled: true,
                  ids: instanceIds,
                },
                pageSize: 50,
              },
            },
          );
          const instances = await response.json();
          return instances.totalCount;
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toEqual(40),
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/v1/incidents/search`,
            {
              data: {
                filter: {
                  processInstanceKey: parseInt(instanceWithAnIncident),
                },
              },
            },
          );
          const incidents: {items: [{state: string}]; total: number} =
            await response.json();
          return (
            incidents.total > 0 &&
            incidents.items.filter(({state}) => state === 'PENDING').length ===
              0
          );
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBeTruthy(),
  ]);
});

test.beforeEach(({dashboardPage}) => {
  dashboardPage.gotoDashboardPage();
});

test.describe('Dashboard', () => {
  test('Statistics', async ({dashboardPage}) => {
    const incidentInstancesCount = Number(
      await dashboardPage.metricPanel
        .getByTestId('incident-instances-badge')
        .innerText(),
    );

    const activeProcessInstancesCount = Number(
      await dashboardPage.metricPanel
        .getByTestId('active-instances-badge')
        .innerText(),
    );

    const totalInstancesCount = await dashboardPage.metricPanel
      .getByTestId('total-instances-link')
      .innerText();

    await expect(totalInstancesCount).toBe(
      `${
        incidentInstancesCount + activeProcessInstancesCount
      } Running Process Instances in total`,
    );
  });

  test('Navigation to Processes View', async ({dashboardPage, page}) => {
    const activeProcessInstancesCount = await page
      .getByTestId('active-instances-badge')
      .nth(0)
      .innerText();

    const instancesWithIncidentCount = await page
      .getByTestId('incident-instances-badge')
      .nth(0)
      .innerText();

    await page.getByTestId('active-instances-link').click();

    await expect(
      page.getByRole('heading', {
        name: `Process Instances - ${activeProcessInstancesCount} result${
          Number(activeProcessInstancesCount) > 1 ? 's' : ''
        }`,
      }),
    ).toBeVisible();

    await dashboardPage.gotoDashboardPage();

    await page.getByTestId('incident-instances-link').click();

    await expect(
      page.getByRole('heading', {
        name: `Process Instances - ${instancesWithIncidentCount} result${
          Number(instancesWithIncidentCount) > 1 ? 's' : ''
        }`,
      }),
    ).toBeVisible();
  });

  test('Navigate to processes view (same truncated error message)', async ({
    page,
    dashboardPage,
    processInstancePage,
  }) => {
    // select incident type a from the incidents list
    await page
      .getByRole('link', {
        name: /type a/i,
      })
      .click();

    await expect(
      page.getByRole('heading', {
        name: 'Process Instances - 1 result',
      }),
    ).toBeVisible();

    await page
      .getByRole('link', {
        name: /view instance/i,
      })
      .click();

    await expect(
      processInstancePage.variablesList.getByRole('cell', {
        name: /incident type a/i,
      }),
    ).toBeVisible();

    await dashboardPage.gotoDashboardPage();

    // select incident type b from the incidents list
    await page
      .getByRole('link', {
        name: /type b/i,
      })
      .click();

    await expect(
      page.getByRole('heading', {
        name: 'Process Instances - 1 result',
      }),
    ).toBeVisible();

    await page
      .getByRole('link', {
        name: /view instance/i,
      })
      .click();

    await expect(
      processInstancePage.variablesList.getByRole('cell', {
        name: /incident type b/i,
      }),
    ).toBeVisible();
  });

  test('Select process instances by name', async ({page}) => {
    await expect(
      page.getByTestId('instances-by-process-definition'),
    ).toBeVisible();

    const firstInstanceByProcess = page.getByTestId(
      'instances-by-process-definition-0',
    );

    const incidentCount = Number(
      await firstInstanceByProcess
        .getByTestId('incident-instances-badge')
        .innerText(),
    );
    const runningInstanceCount = Number(
      await firstInstanceByProcess
        .getByTestId('active-instances-badge')
        .innerText(),
    );

    const totalInstanceCount = incidentCount + runningInstanceCount;

    await firstInstanceByProcess.click();

    await expect(
      page.getByRole('heading', {
        name: `Process Instances - ${totalInstanceCount} result${
          Number(totalInstanceCount) > 1 ? 's' : ''
        }`,
      }),
    ).toBeVisible();
  });

  test('Select process instances by error message', async ({page}) => {
    await expect(page.getByTestId('incident-byError')).toBeVisible();

    const firstInstanceByError = page.getByTestId('incident-byError-0');

    const incidentCount = await Number(
      await firstInstanceByError
        .getByTestId('incident-instances-badge')
        .innerText(),
    );

    await firstInstanceByError.click();

    await expect(
      page.getByRole('heading', {
        name: `Process Instances - ${incidentCount} result${
          Number(incidentCount) > 1 ? 's' : ''
        }`,
      }),
    ).toBeVisible();
  });

  test('Select process instances by error message (expanded)', async ({
    page,
  }) => {
    await expect(page.getByTestId('incident-byError')).toBeVisible();

    const firstInstanceByError = page.getByTestId('incident-byError-0');

    const incidentCount = await Number(
      await firstInstanceByError
        .getByTestId('incident-instances-badge')
        .innerText(),
    );

    await firstInstanceByError
      .getByRole('button', {
        name: 'Expand current row',
      })
      .click();

    await firstInstanceByError.getByRole('link').nth(0).click();

    await expect(
      page.getByRole('heading', {
        name: `Process Instances - ${incidentCount} result${
          Number(incidentCount) > 1 ? 's' : ''
        }`,
      }),
    ).toBeVisible();
  });
});
