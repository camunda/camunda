/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {
  deploy,
  createInstances,
  createSingleInstance,
  createWorker,
} from 'utils/zeebeClient';
import {waitForProcessInstances} from 'utils/incidentsHelper';
import {navigateToApp} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {sleep} from 'utils/sleep';

// Set secure connection to false for this test only

let instanceIds: string[] = [];

test.beforeAll(async ({request}) => {
  test.setTimeout(180000); // 3 minutes timeout

  // Deploy processes
  await deploy([
    './resources/withoutInstancesProcess_v_1.bpmn',
    './resources/withoutIncidentsProcess_v_1.bpmn',
    './resources/onlyIncidentsProcess_v_1.bpmn',
    './resources/orderProcess_v_1.bpmn',
    './resources/processWithAnIncident.bpmn',
    './resources/incidentGeneratorProcess.bpmn',
  ]);

  await deploy([
    './resources/withoutInstancesProcess_v_2.bpmn',
    './resources/withoutIncidentsProcess_v_2.bpmn',
    './resources/onlyIncidentsProcess_v_2.bpmn',
  ]);

  // Create worker for incident generation (replicating dashboard.mocks.ts)
  createWorker('incidentGenerator', true, {}, (job) => {
    const BASE_ERROR_MESSAGE =
      'This is an error message for testing purposes. This error message is very long to ensure it is truncated in the UI.';

    if (job.variables.incidentType === 'Incident Type A') {
      return job.fail(`${BASE_ERROR_MESSAGE} Type A`);
    } else {
      return job.fail(`${BASE_ERROR_MESSAGE} Type B`);
    }
  });

  // Create instances exactly like dashboard.mocks.ts
  const instancesList = await Promise.all([
    createInstances('withoutIncidentsProcess', 1, 4),
    createInstances('withoutIncidentsProcess', 2, 8),
    createInstances('onlyIncidentsProcess', 1, 10),
    createInstances('onlyIncidentsProcess', 2, 5),
    createInstances('orderProcess', 1, 10),
    createSingleInstance('processWithAnIncident', 1),
    createSingleInstance('incidentGeneratorProcess', 1, {
      incidentType: 'Incident Type A',
    }),
    createSingleInstance('incidentGeneratorProcess', 1, {
      incidentType: 'Incident Type B',
    }),
  ]);

  const allInstances = instancesList.flatMap((instances) => instances);
  instanceIds = allInstances.map((instance) => instance.processInstanceKey);

  // Wait for instances to be created (40 total: 4+8+10+5+10+1+1+1 = 40)
  await waitForProcessInstances(request, instanceIds, 40);

  // Additional sleep to ensure incidents are generated and UI is updated
  console.log('Waiting for incidents to be created...');
  await sleep(10000);

  // Log to see if incidents were created
  console.log('Total instances created:', instanceIds.length);
});

test.beforeEach(async ({page, loginPage, operateHomePage}) => {
  await navigateToApp(page, 'operate');
  await loginPage.login('demo', 'demo');
  await expect(operateHomePage.operateBanner).toBeVisible();
});

test.afterEach(async ({page}, testInfo) => {
  await captureScreenshot(page, testInfo);
  await captureFailureVideo(page, testInfo);
});

test.describe('Dashboard', () => {
  test('Statistics', async ({operateDashboardPage}) => {
    const incidentInstancesCount = Number(
      await operateDashboardPage.incidentInstancesBadge.innerText(),
    );

    const activeProcessInstancesCount = Number(
      await operateDashboardPage.activeInstancesBadge.innerText(),
    );

    const totalInstancesCount = operateDashboardPage.totalInstancesLink;

    await expect(totalInstancesCount).toHaveText(
      `${
        incidentInstancesCount + activeProcessInstancesCount
      } Running Process Instances in total`,
    );
  });

  test('Navigation to Processes View', async ({operateDashboardPage, page}) => {
    const activeProcessInstancesCount = await page
      .getByTestId('active-instances-badge')
      .nth(0)
      .innerText();

    const instancesWithIncidentCount = await page
      .getByTestId('incident-instances-badge')
      .nth(0)
      .innerText();

    await operateDashboardPage.activeInstancesLink.click();

    await expect(
      page.getByRole('heading', {
        name: `Process Instances - ${activeProcessInstancesCount} result${
          Number(activeProcessInstancesCount) > 1 ? 's' : ''
        }`,
      }),
    ).toBeVisible();

    await operateDashboardPage.gotoDashboardPage();

    await operateDashboardPage.incidentInstancesLink.click();

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
    operateDashboardPage,
    operateProcessInstancePage,
  }) => {
    // Select incident type a from the incidents list
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
      operateProcessInstancePage.variablesList.getByRole('cell', {
        name: /incident type a/i,
      }),
    ).toBeVisible();

    await operateDashboardPage.gotoDashboardPage();

    // Select incident type b from the incidents list
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
      operateProcessInstancePage.variablesList.getByRole('cell', {
        name: /incident type b/i,
      }),
    ).toBeVisible();
  });

  test('Select process instances by name', async ({
    page,
    operateDashboardPage,
  }) => {
    await expect(operateDashboardPage.instancesByProcess).toBeVisible();

    const firstInstanceByProcess =
      operateDashboardPage.getInstancesByProcessItem(0);

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

  test('Select process instances by error message', async ({
    page,
    operateDashboardPage,
  }) => {
    await expect(operateDashboardPage.incidentsByError).toBeVisible();

    const firstInstanceByError =
      operateDashboardPage.getIncidentsByErrorItem(0);

    const incidentCount = Number(
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
    operateDashboardPage,
  }) => {
    await expect(operateDashboardPage.incidentsByError).toBeVisible();

    const firstInstanceByError =
      operateDashboardPage.getIncidentsByErrorItem(0);

    const incidentCount = Number(
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
