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
import {waitForAssertion} from '../../utils/waitForAssertion';

let instanceIds: string[] = [];

test.beforeAll(async ({request}) => {
  test.setTimeout(180000);

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

  createWorker('incidentGenerator', true, {}, (job) => {
    const BASE_ERROR_MESSAGE =
      'This is an error message for testing purposes. This error message is very long to ensure it is truncated in the UI.';

    if (job.variables.incidentType === 'Incident Type A') {
      return job.fail(`${BASE_ERROR_MESSAGE} Type A`);
    } else {
      return job.fail(`${BASE_ERROR_MESSAGE} Type B`);
    }
  });

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
    await test.step('Verify total count equals sum of active and incident instances', async () => {
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
  });

  test('Navigation to Processes View', async ({operateDashboardPage}) => {
    await test.step('Navigate to active instances and verify count', async () => {
      const activeProcessInstancesCount =
        await operateDashboardPage.activeInstancesBadge.innerText();

      await operateDashboardPage.clickActiveInstancesLink();

      await expect(
        operateDashboardPage.processInstancesHeading(
          activeProcessInstancesCount,
          Number(activeProcessInstancesCount) > 1,
        ),
      ).toBeVisible();
    });

    await test.step('Navigate to incident instances and verify count', async () => {
      await operateDashboardPage.gotoDashboardPage();

      const instancesWithIncidentCount =
        await operateDashboardPage.incidentInstancesBadge.innerText();

      await operateDashboardPage.clickIncidentInstancesLink();

      await expect(
        operateDashboardPage.processInstancesHeading(
          instancesWithIncidentCount,
          Number(instancesWithIncidentCount) > 1,
        ),
      ).toBeVisible();
    });
  });

  test('Navigate to processes view (same truncated error message)', async ({
    operateDashboardPage,
    operateProcessInstancePage,
  }) => {
    await test.step('Select incident type A and verify details', async () => {
      await operateDashboardPage.clickIncidentByType(/type a/i);
      await expect(
        operateDashboardPage.processInstancesHeading(1, false),
      ).toBeVisible();

      await operateDashboardPage.clickViewInstanceLink();
      await expect(
        operateProcessInstancePage.variableCellByName(/incident type a/i),
      ).toBeVisible();
    });

    await test.step('Select incident type B and verify details', async () => {
      await operateDashboardPage.gotoDashboardPage();
      await operateDashboardPage.clickIncidentByType(/type b/i);
      await expect(
        operateDashboardPage.processInstancesHeading(1, false),
      ).toBeVisible();

      await operateDashboardPage.clickViewInstanceLink();
      await expect(
        operateProcessInstancePage.variableCellByName(/incident type b/i),
      ).toBeVisible();
    });
  });

  test('Select process instances by name', async ({
    page,
    operateDashboardPage,
  }) => {
    await test.step('Select first process and verify total count', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateDashboardPage.instancesByProcess).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      const firstInstanceByProcess =
        operateDashboardPage.instancesByProcessItem(0);

      const incidentCount = Number(
        await operateDashboardPage
          .incidentBadgeFromItem(firstInstanceByProcess)
          .innerText(),
      );

      const runningInstanceCount = Number(
        await operateDashboardPage
          .activeBadgeFromItem(firstInstanceByProcess)
          .innerText(),
      );

      const totalInstanceCount = incidentCount + runningInstanceCount;

      await operateDashboardPage.clickItem(firstInstanceByProcess);

      await expect(
        operateDashboardPage.processInstancesHeading(
          totalInstanceCount,
          Number(totalInstanceCount) > 1,
        ),
      ).toBeVisible();
    });
  });

  test('Select process instances by error message', async ({
    operateDashboardPage,
  }) => {
    await test.step('Select first error and verify incident count', async () => {
      await expect(operateDashboardPage.incidentsByError).toBeVisible();

      const firstInstanceByError = operateDashboardPage.incidentsByErrorItem(0);

      const incidentCount = Number(
        await operateDashboardPage
          .incidentBadgeFromItem(firstInstanceByError)
          .innerText(),
      );

      await operateDashboardPage.clickItem(firstInstanceByError);

      await expect(
        operateDashboardPage.processInstancesHeading(
          incidentCount,
          Number(incidentCount) > 1,
        ),
      ).toBeVisible();
    });
  });

  test('Select process instances by error message (expanded)', async ({
    operateDashboardPage,
  }) => {
    await test.step('Expand first error and navigate to verify incident count', async () => {
      await expect(operateDashboardPage.incidentsByError).toBeVisible();

      const firstInstanceByError = operateDashboardPage.incidentsByErrorItem(0);

      const incidentCount = Number(
        await operateDashboardPage
          .incidentBadgeFromItem(firstInstanceByError)
          .innerText(),
      );

      await operateDashboardPage.expandItem(firstInstanceByError);
      await operateDashboardPage.clickFirstLinkInItem(firstInstanceByError);

      await expect(
        operateDashboardPage.processInstancesHeading(
          incidentCount,
          Number(incidentCount) > 1,
        ),
      ).toBeVisible();
    });
  });
});
