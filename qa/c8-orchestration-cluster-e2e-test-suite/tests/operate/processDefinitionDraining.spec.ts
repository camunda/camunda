/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect, type Locator, type Page} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp, tooltipWithText} from '@pages/UtilitiesPage';
import {
  activateSingleJob,
  createInstanceOnceDeployed,
  deployUserTaskProcess,
  drainProcessDefinition,
  expectProcessDefinitionDeleted,
  findUserTask,
} from '@requestHelpers';
import {
  cancelProcessInstance,
  createInstances,
  deployWithSubstitutions,
} from 'utils/zeebeClient';
import {assertStatusCode, buildUrl, jsonHeaders} from 'utils/http';
import {waitForAssertion} from 'utils/waitForAssertion';
import {uniquePrefixedId} from 'utils/constants';

const DRAINING_TOOLTIP_ALL_VERSIONS =
  'One or more versions of this process definition are scheduled for deletion. They stay until their running instances finish, then are removed automatically.';
const DRAINING_TOOLTIP_VERSION =
  'This process definition version is scheduled for deletion and will be removed automatically once all of its running instances finish.';
const DRAINING_TAG_LABEL = 'Draining';

const UI_REFRESH_TIMEOUT = 15_000;

const processDefinitionId = uniquePrefixedId('draining-ui');

let processInstanceKey: string;

// Operate is unauthenticated per test on this branch, so the login has to come
// before any app chrome exists.
async function openOperateHome(
  page: Page,
  loginPage: {login: (user: string, password: string) => Promise<void>},
  operateHomePage: {operateBanner: Locator},
) {
  await navigateToApp(page, 'operate');
  await loginPage.login('demo', 'demo');
  await waitForAssertion({
    assertion: async () => {
      await expect(operateHomePage.operateBanner).toBeVisible({
        timeout: UI_REFRESH_TIMEOUT,
      });
    },
    onFailure: async () => {
      await page.reload();
    },
  });
}

test.beforeAll(async ({request}) => {
  const {processDefinitionKey} =
    await deployUserTaskProcess(processDefinitionId);

  const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
  processInstanceKey = instance.processInstanceKey;
  await findUserTask(request, processInstanceKey, 'CREATED');

  // Operate's own Delete button is gated for definitions with running instances
  // (camunda#60910), so the drain is triggered over REST.
  await drainProcessDefinition(request, processDefinitionKey);
});

test.afterAll(async () => {
  // Teardown stays best-effort: a beforeAll that fails before the instance
  // exists would otherwise throw here and mask the original failure.
  if (processInstanceKey) {
    await cancelProcessInstance(processInstanceKey, {ignoreNotFound: true});
  }
});

test.describe('Operate Process Definition Draining', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Dashboard marks the draining definition with a draining indicator', async ({
    page,
    loginPage,
    operateHomePage,
    operateDashboardPage,
  }) => {
    await openOperateHome(page, loginPage, operateHomePage);

    const item =
      operateDashboardPage.instancesByProcessItemByName(processDefinitionId);

    await waitForAssertion({
      assertion: async () => {
        await expect(
          operateDashboardPage.drainingIndicatorFromItem(item),
        ).toBeVisible({timeout: UI_REFRESH_TIMEOUT});
      },
      onFailure: async () => {
        await page.reload();
      },
    });
  });

  test('Processes page shows a draining tag for the selected draining version', async ({
    page,
    loginPage,
    operateHomePage,
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    await openOperateHome(page, loginPage, operateHomePage);
    await operateHomePage.clickProcessesTab();

    // The tag replaces the delete action in the diagram panel header, which only
    // renders once a single version is selected.
    await operateFiltersPanelPage.selectProcess(processDefinitionId);
    await operateFiltersPanelPage.selectVersion('1');

    await waitForAssertion({
      assertion: async () => {
        await expect(operateProcessesPage.drainingTag).toBeVisible({
          timeout: UI_REFRESH_TIMEOUT,
        });
      },
      onFailure: async () => {
        await page.reload();
      },
    });
  });

  test('Process instance header shows a draining tag for the running instance', async ({
    page,
    loginPage,
    operateHomePage,
    operateProcessInstancePage,
  }) => {
    await openOperateHome(page, loginPage, operateHomePage);
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: processInstanceKey,
    });

    await expect(operateProcessInstancePage.instanceHeader).toBeVisible();

    await waitForAssertion({
      assertion: async () => {
        await expect(operateProcessInstancePage.drainingTag).toBeVisible({
          timeout: UI_REFRESH_TIMEOUT,
        });
      },
      onFailure: async () => {
        await page.reload();
      },
    });
  });

  test('The draining marker carries the same wording on the dashboard, the processes page and the instance view', async ({
    page,
    loginPage,
    operateHomePage,
    operateDashboardPage,
    operateProcessesPage,
    operateFiltersPanelPage,
    operateProcessInstancePage,
  }) => {
    // The per-view tests above only prove the marker renders; this one pins down
    // what it says, so a reworded or half-migrated view is caught.
    await openOperateHome(page, loginPage, operateHomePage);

    const item =
      operateDashboardPage.instancesByProcessItemByName(processDefinitionId);
    const dashboardIndicator =
      operateDashboardPage.drainingIndicatorFromItem(item);

    await waitForAssertion({
      assertion: async () => {
        await expect(dashboardIndicator).toBeVisible({
          timeout: UI_REFRESH_TIMEOUT,
        });
      },
      onFailure: async () => {
        await page.reload();
      },
    });

    // The dashboard row aggregates every version, so it carries the all-versions
    // wording rather than the version-scoped "Draining" tag.
    await dashboardIndicator.hover();
    await expect(
      tooltipWithText(page, DRAINING_TOOLTIP_ALL_VERSIONS),
    ).toBeVisible();

    await operateHomePage.clickProcessesTab();
    await operateFiltersPanelPage.selectProcess(processDefinitionId);
    await operateFiltersPanelPage.selectVersion('1');

    await waitForAssertion({
      assertion: async () => {
        await expect(operateProcessesPage.drainingTag).toBeVisible({
          timeout: UI_REFRESH_TIMEOUT,
        });
      },
      onFailure: async () => {
        await page.reload();
      },
    });
    await expect(operateProcessesPage.drainingTag).toHaveText(
      DRAINING_TAG_LABEL,
    );
    await operateProcessesPage.drainingTag.hover();
    await expect(tooltipWithText(page, DRAINING_TOOLTIP_VERSION)).toBeVisible();

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: processInstanceKey,
    });
    await expect(operateProcessInstancePage.instanceHeader).toBeVisible();

    await waitForAssertion({
      assertion: async () => {
        await expect(operateProcessInstancePage.drainingTag).toBeVisible({
          timeout: UI_REFRESH_TIMEOUT,
        });
      },
      onFailure: async () => {
        await page.reload();
      },
    });
    await expect(operateProcessInstancePage.drainingTag).toHaveText(
      DRAINING_TAG_LABEL,
    );
    await operateProcessInstancePage.drainingTag.hover();
    await expect(tooltipWithText(page, DRAINING_TOOLTIP_VERSION)).toBeVisible();
  });
});

test.describe('Operate Process Definition Draining — lifecycle and incidents', () => {
  let instancesToCancel: string[] = [];

  test.beforeEach(() => {
    instancesToCancel = [];
  });

  // These tests own their definitions. Cancelling here rather than in each test
  // body means a failed assertion cannot leak one as permanently DRAINING.
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
    for (const processInstanceKey of instancesToCancel.filter(Boolean)) {
      await cancelProcessInstance(processInstanceKey, {ignoreNotFound: true});
    }
  });

  test('The definition leaves the dashboard once the drain has finished', async ({
    page,
    request,
    loginPage,
    operateHomePage,
    operateDashboardPage,
  }) => {
    const deletedProcessDefinitionId = uniquePrefixedId('draining-ui-gone');
    const {processDefinitionKey} = await deployUserTaskProcess(
      deletedProcessDefinitionId,
    );
    const instance = await createInstanceOnceDeployed(
      deletedProcessDefinitionId,
      1,
    );
    instancesToCancel.push(instance.processInstanceKey);
    await findUserTask(request, instance.processInstanceKey, 'CREATED');

    await drainProcessDefinition(request, processDefinitionKey);

    await openOperateHome(page, loginPage, operateHomePage);

    const item = operateDashboardPage.instancesByProcessItemByName(
      deletedProcessDefinitionId,
    );
    await waitForAssertion({
      assertion: async () => {
        await expect(
          operateDashboardPage.drainingIndicatorFromItem(item),
        ).toBeVisible({timeout: UI_REFRESH_TIMEOUT});
      },
      onFailure: async () => {
        await page.reload();
      },
    });

    // The dashboard panel counts running instances, so the whole row goes, not
    // just the marker.
    await cancelProcessInstance(instance.processInstanceKey);
    await expectProcessDefinitionDeleted(request, processDefinitionKey);

    await waitForAssertion({
      assertion: async () => {
        await expect(item).toHaveCount(0, {timeout: UI_REFRESH_TIMEOUT});
      },
      onFailure: async () => {
        await page.reload();
      },
    });
  });

  test('A draining definition still reports its incident and active instance counts', async ({
    page,
    request,
    loginPage,
    operateHomePage,
    operateDashboardPage,
  }) => {
    const incidentProcessDefinitionId = uniquePrefixedId(
      'draining-ui-incident',
    );
    const jobType = uniquePrefixedId('draining-ui-jobtype');
    const deployment = await deployWithSubstitutions(
      './resources/incidentGeneratorProcess.bpmn',
      {
        incidentGeneratorProcess: incidentProcessDefinitionId,
        'type="incidentGenerator"': `type="${jobType}"`,
        // The dashboard row is found by the rendered BPMN name, which in this model
        // differs from the process id.
        'name="Incident Generator Process"': `name="${incidentProcessDefinitionId}"`,
      },
    );
    const {processDefinitionKey} = deployment.processes[0];

    const instances = await createInstances(incidentProcessDefinitionId, 1, 3);
    instancesToCancel.push(
      ...instances.map((instance) => instance.processInstanceKey),
    );

    // One instance goes into an incident, two stay active, so the dashboard has
    // to split the counts rather than lump them together.
    const failedInstanceKey = instances[0]!.processInstanceKey;
    const jobKey = await activateSingleJob(request, jobType, failedInstanceKey);

    await assertStatusCode(
      await request.post(buildUrl('/jobs/{jobKey}/failure', {jobKey}), {
        headers: jsonHeaders(),
        data: {retries: 0, errorMessage: 'draining-ui incident'},
      }),
      204,
    );

    await drainProcessDefinition(request, processDefinitionKey);

    await openOperateHome(page, loginPage, operateHomePage);

    const item = operateDashboardPage.instancesByProcessItemByName(
      incidentProcessDefinitionId,
    );

    await waitForAssertion({
      assertion: async () => {
        await expect(
          operateDashboardPage.drainingIndicatorFromItem(item),
        ).toBeVisible({timeout: UI_REFRESH_TIMEOUT});
        await expect(
          operateDashboardPage.incidentBadgeFromItem(item),
        ).toHaveText('1', {timeout: UI_REFRESH_TIMEOUT});
        await expect(operateDashboardPage.activeBadgeFromItem(item)).toHaveText(
          '2',
          {timeout: UI_REFRESH_TIMEOUT},
        );
      },
      onFailure: async () => {
        await page.reload();
      },
    });
  });
});
