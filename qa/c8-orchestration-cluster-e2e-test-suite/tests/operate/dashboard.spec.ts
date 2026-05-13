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
import {defaultAssertionOptions} from '../../utils/constants';

let instanceIds: string[] = [];

test.beforeAll(async ({request}) => {
  test.setTimeout(180000);

  await deploy([
    './resources/withoutInstancesProcess_v_1.bpmn',
    './resources/withoutIncidentsProcess_v_1.bpmn',
    './resources/onlyIncidentsProcess_v_1.bpmn',
    './resources/orderProcess_v_1.bpmn',
    './resources/processWithAnIncident.bpmn',
    './resources/dashboardSelectionProcess_v_1.bpmn',
    './resources/dashboardIncidentGenerator.bpmn',
  ]);

  await deploy([
    './resources/withoutInstancesProcess_v_2.bpmn',
    './resources/withoutIncidentsProcess_v_2.bpmn',
    './resources/onlyIncidentsProcess_v_2.bpmn',
  ]);

  createWorker('dashboardIncidentGenerator', true, {}, (job) => {
    // Prefix is unique to this spec so the error-message hash doesn't
    // collide with incidents created by other specs (e.g.
    // job-worker-statistics-test-setup.spec.ts uses the same base text).
    const BASE_ERROR_MESSAGE =
      '[dashboard-spec] This is an error message for testing purposes. This error message is very long to ensure it is truncated in the UI.';

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
    createInstances('dashboardSelectionProcess', 1, 10),
    createSingleInstance('processWithAnIncident', 1),
    createSingleInstance('dashboardIncidentGenerator', 1, {
      incidentType: 'Incident Type A',
    }),
    createSingleInstance('dashboardIncidentGenerator', 1, {
      incidentType: 'Incident Type B',
    }),
  ]);

  const allInstances = instancesList.flatMap((instances) => instances);
  instanceIds = allInstances.map((instance) => instance.processInstanceKey);

  // Wait for instances to be created (50 total: 4+8+10+5+10+10+1+1+1 = 50)
  await waitForProcessInstances(request, instanceIds, 50);
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
      // The three badges (active, incident, total) update on independent
      // polling intervals while other tests in this suite keep creating
      // process instances, so reading the parts and then asserting the
      // sum can fail when the system snapshot shifts between reads.
      // Re-read all three values atomically on each poll iteration and
      // verify the invariant holds for a consistent snapshot.
      await expect
        .poll(async () => {
          const incident = Number(
            await operateDashboardPage.incidentInstancesBadge.innerText(),
          );
          const active = Number(
            await operateDashboardPage.activeInstancesBadge.innerText(),
          );
          const total =
            await operateDashboardPage.totalInstancesLink.innerText();
          return (
            total === `${incident + active} Running Process Instances in total`
          );
        }, defaultAssertionOptions)
        .toBe(true);
    });
  });

  test('Navigation to Processes View', async ({page, operateDashboardPage}) => {
    const ensureDashboardReady = async () => {
      await operateDashboardPage.gotoDashboardPage();

      await expect(operateDashboardPage.activeInstancesBadge).toBeVisible();
      await expect(operateDashboardPage.incidentInstancesBadge).toBeVisible();

      await expect(operateDashboardPage.activeInstancesBadge).toHaveText(/\d+/);
      await expect(operateDashboardPage.incidentInstancesBadge).toHaveText(
        /\d+/,
      );
    };

    await test.step('Navigate to active instances (view opens)', async () => {
      await ensureDashboardReady();

      await operateDashboardPage.clickActiveInstancesLink();
      await expect(
        page.getByRole('heading', {name: /process instances/i}),
      ).toBeVisible();
    });

    await test.step('Navigate to incident instances (view opens)', async () => {
      await ensureDashboardReady();

      await operateDashboardPage.clickIncidentInstancesLink();
      await expect(
        page.getByRole('heading', {name: /process instances/i}),
      ).toBeVisible();
    });
  });

  test('Navigate to processes view (same truncated error message)', async ({
    operateDashboardPage,
    operateProcessInstancePage,
  }) => {
    // Scope the link selectors to this spec's [dashboard-spec] prefix so
    // they don't match identical-suffix incidents created by other specs.
    const typeALink = /\[dashboard-spec].*type a/i;
    const typeBLink = /\[dashboard-spec].*type b/i;

    await test.step('Select incident type A and verify details', async () => {
      await operateDashboardPage.clickIncidentByType(typeALink);

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
      await operateDashboardPage.clickIncidentByType(typeBLink);
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
    // Use a process definition created exclusively by this spec so the
    // badge counts and the filtered Process Instances heading reference
    // a stable population unaffected by other tests running in parallel.
    const PROCESS_NAME = 'Dashboard Selection Process';

    await test.step('Select dashboard selection process and verify total count', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateDashboardPage.instancesByProcess).toBeVisible();

          const processRow =
            operateDashboardPage.instancesByProcessItemByName(PROCESS_NAME);

          await expect(processRow).toBeVisible();

          const incidentCount = Number(
            await operateDashboardPage
              .incidentBadgeFromItem(processRow)
              .innerText(),
          );

          const runningInstanceCount = Number(
            await operateDashboardPage
              .activeBadgeFromItem(processRow)
              .innerText(),
          );

          const totalInstanceCount = incidentCount + runningInstanceCount;

          await operateDashboardPage.clickItem(processRow);

          await expect(
            operateDashboardPage.processInstancesHeading(
              totalInstanceCount,
              Number(totalInstanceCount) > 1,
            ),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.goto(`${process.env.CORE_APPLICATION_URL}/operate`);
        },
      });
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
    page,
    operateDashboardPage,
  }) => {
    await test.step('Expand first error and navigate to verify incident count', async () => {
      // Dashboard badge counts and the filtered Process Instances heading
      // are computed from independent queries, so they can disagree under
      // active load (observed 1395 on the badge vs 1549 on the destination
      // page on shared CI). The expand step makes this race worse by adding
      // latency between reading the badge and clicking through. Retry the
      // read + expand + click + verify cycle until they agree.
      await waitForAssertion({
        assertion: async () => {
          await expect(operateDashboardPage.incidentsByError).toBeVisible();

          const firstInstanceByError =
            operateDashboardPage.incidentsByErrorItem(0);

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
        },
        onFailure: async () => {
          await page.goto(`${process.env.CORE_APPLICATION_URL}/operate`);
        },
      });
    });
  });
});
