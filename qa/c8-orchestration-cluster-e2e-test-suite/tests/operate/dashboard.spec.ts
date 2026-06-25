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
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {waitForAssertion} from '../../utils/waitForAssertion';
import {defaultAssertionOptions} from '../../utils/constants';

let instanceIds: string[] = [];

// Unique per beforeAll execution so multiple projects (e.g. `chromium` and
// `operate-e2e`) running this spec against the same cluster produce
// distinct error-message hashes — keeping the "incidents by error" rows
// and Process Instances counts separate for each project's worker.
const runTag = `dashboard-spec-${Math.random().toString(36).slice(2, 10)}`;

const DASHBOARD_INCIDENT_BASE_MESSAGE = `[${runTag}] This is an error message for testing purposes. This error message is very long to ensure it is truncated in the UI.`;

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
    if (job.variables.incidentType === 'Incident Type A') {
      return job.fail(`${DASHBOARD_INCIDENT_BASE_MESSAGE} Type A`);
    } else {
      return job.fail(`${DASHBOARD_INCIDENT_BASE_MESSAGE} Type B`);
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

test.beforeEach(async ({page, operateHomePage}) => {
  await navigateToAppHome(page, 'operate');
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
        page
          .getByTestId('frame-container')
          .getByRole('heading', {name: /process instances/i}),
      ).toBeVisible();
    });

    await test.step('Navigate to incident instances (view opens)', async () => {
      await ensureDashboardReady();

      await operateDashboardPage.clickIncidentInstancesLink();
      await expect(
        page
          .getByTestId('frame-container')
          .getByRole('heading', {name: /process instances/i}),
      ).toBeVisible();
    });
  });

  test('Navigate to processes view (same truncated error message)', async ({
    operateDashboardPage,
    operateProcessInstancePage,
  }) => {
    // Scope the link selectors to this beforeAll execution's runTag so
    // they don't match incidents created by parallel project workers
    // (e.g. `chromium` and `operate-e2e`) running this same spec, or by
    // any other spec emitting incidents with similar text.
    const typeALink = new RegExp(`${runTag}.*type a`, 'i');
    const typeBLink = new RegExp(`${runTag}.*type b`, 'i');

    await test.step('Select incident type A and verify details', async () => {
      await operateDashboardPage.clickIncidentByType(typeALink);

      await expect(
        operateDashboardPage.processInstancesHeading(1, false),
      ).toBeVisible();

      await operateDashboardPage.clickViewInstanceLink();
      // Instances with an incident open on the Incidents tab by default;
      // switch to Variables before asserting on the variable value.
      await operateProcessInstancePage.clickVariablesTab();
      await expect(
        operateProcessInstancePage.variablesList.getByText('"Incident Type A"'),
      ).toBeVisible();
    });

    await test.step('Select incident type B and verify details', async () => {
      await operateDashboardPage.gotoDashboardPage();
      await operateDashboardPage.clickIncidentByType(typeBLink);
      await expect(
        operateDashboardPage.processInstancesHeading(1, false),
      ).toBeVisible();

      await operateDashboardPage.clickViewInstanceLink();
      await operateProcessInstancePage.clickVariablesTab();
      await expect(
        operateProcessInstancePage.variablesList.getByText('"Incident Type B"'),
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
    await test.step('Select dashboard-spec error and verify incident count', async () => {
      await expect(operateDashboardPage.incidentsByError).toBeVisible();

      // Use the row matching this beforeAll execution's runTag so the
      // selected error population is not mutated by other specs running
      // in parallel against the same cluster.
      const dashboardErrorRow =
        operateDashboardPage.incidentsByErrorItemByMessage(
          new RegExp(runTag, 'i'),
        );

      await expect(dashboardErrorRow.first()).toBeVisible();

      const incidentCount = Number(
        await operateDashboardPage
          .incidentBadgeFromItem(dashboardErrorRow.first())
          .innerText(),
      );

      await operateDashboardPage.clickItem(dashboardErrorRow.first());

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
