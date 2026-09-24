/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {
  activateSingleJob,
  deployCallActivityPair,
  createInstanceOnceDeployed,
  deployServiceTaskProcess,
  expectProcessState,
  expectSuspendedDate,
  failJob,
  resumeProcessInstance,
  searchIncidentByPIK,
  searchProcessInstances,
  suspendAndExpectSuspended,
  suspendProcessInstance,
} from '@requestHelpers';
import {cancelProcessInstance} from 'utils/zeebeClient';
import {assertStatusCode} from 'utils/http';
import {uniquePrefixedId, extendedAssertionOptions} from 'utils/constants';

/**
 * The UI half of suspend/resume. State changes the UI only has to reflect are
 * driven over REST; the UI controls are exercised only where the control itself
 * is what is under test.
 */

const UI_REFRESH_TIMEOUT = 15_000;
const instancesToCancel: string[] = [];

async function startServiceTaskInstance(prefix: string) {
  const processDefinitionId = uniquePrefixedId(prefix);
  const jobType = uniquePrefixedId(`${prefix}-job`);
  await deployServiceTaskProcess(processDefinitionId, jobType);
  const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
  instancesToCancel.push(instance.processInstanceKey);
  return {
    processDefinitionId,
    jobType,
    processInstanceKey: instance.processInstanceKey,
  };
}

test.describe('Operate Process Instance Suspend and Resume', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test.afterAll(async () => {
    for (const processInstanceKey of instancesToCancel) {
      try {
        await cancelProcessInstance(processInstanceKey);
      } catch {
        // Already terminal.
      }
    }
    instancesToCancel.length = 0;
  });

  test('Suspending and resuming from the instance header updates the state', async ({
    request,
    operateProcessInstancePage,
  }) => {
    const subject = await startServiceTaskInstance('sr-ui-header');
    const control = await startServiceTaskInstance('sr-ui-header-control');
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: subject.processInstanceKey,
    });
    await expect(operateProcessInstancePage.instanceHeader).toBeVisible();

    await operateProcessInstancePage.suspendInstance(
      subject.processInstanceKey,
    );
    await expect(operateProcessInstancePage.suspendedStateIcon).toBeVisible({
      timeout: UI_REFRESH_TIMEOUT,
    });
    await expectProcessState(
      request,
      subject.processInstanceKey,
      'SUSPENDED',
      extendedAssertionOptions,
    );
    await expectSuspendedDate(
      request,
      subject.processInstanceKey,
      true,
      extendedAssertionOptions,
    );
    // The action targets one instance, not the view.
    await expectProcessState(
      request,
      control.processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );

    await operateProcessInstancePage.resumeInstance(subject.processInstanceKey);
    await expect(operateProcessInstancePage.suspendedStateIcon).toBeHidden({
      timeout: UI_REFRESH_TIMEOUT,
    });
    await expectProcessState(
      request,
      subject.processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
    await expectSuspendedDate(
      request,
      subject.processInstanceKey,
      false,
      extendedAssertionOptions,
    );
  });

  test('A suspended instance offers Resume and Cancel but not Suspend', async ({
    request,
    page,
    operateProcessInstancePage,
  }) => {
    const {processInstanceKey} =
      await startServiceTaskInstance('sr-ui-actions');
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: processInstanceKey,
    });
    await expect(operateProcessInstancePage.instanceHeader).toBeVisible();

    const activeActions =
      await operateProcessInstancePage.instanceHeaderActionNames();
    expect(activeActions.join(' ')).toMatch(/suspend/i);

    await suspendAndExpectSuspended(request, processInstanceKey);
    await page.reload();
    await expect(operateProcessInstancePage.suspendedStateIcon).toBeVisible({
      timeout: UI_REFRESH_TIMEOUT,
    });

    const suspendedActions =
      await operateProcessInstancePage.instanceHeaderActionNames();
    expect(suspendedActions.join(' ')).toMatch(/resume/i);
    expect(suspendedActions.join(' ')).toMatch(/cancel/i);
    expect(suspendedActions.join(' ')).not.toMatch(/suspend[^e]/i);
  });

  test('The Suspended filter returns the suspended instance', async ({
    request,
    page,
    operateHomePage,
    operateFiltersPanelPage,
  }) => {
    const suspended = await startServiceTaskInstance('sr-ui-filter');
    await suspendAndExpectSuspended(request, suspended.processInstanceKey);

    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
    await operateFiltersPanelPage.clickSuspendedInstancesCheckbox();
    await operateFiltersPanelPage.displayOptionalFilter(
      'Process Instance Key(s)',
    );
    await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
      suspended.processInstanceKey,
    );

    await expect(
      page.getByText(suspended.processInstanceKey, {exact: false}).first(),
    ).toBeVisible({timeout: UI_REFRESH_TIMEOUT});
  });

  test('Retry Incident stays disabled while the instance is suspended', async ({
    request,
    page,
    operateProcessInstancePage,
  }) => {
    const processDefinitionId = uniquePrefixedId('sr-ui-incident');
    const jobType = uniquePrefixedId('sr-ui-incident-job');
    await deployServiceTaskProcess(processDefinitionId, jobType);
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);
    const jobKey = await activateSingleJob(
      request,
      jobType,
      instance.processInstanceKey,
    );
    await failJob(request, String(jobKey), 0);
    await searchIncidentByPIK(request, {
      processInstanceKey: instance.processInstanceKey,
    });

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instance.processInstanceKey,
    });
    await waitForAssertion({
      assertion: async () => {
        await expect(operateProcessInstancePage.incidentsTable).toBeVisible({
          timeout: UI_REFRESH_TIMEOUT,
        });
      },
      onFailure: async () => {
        await page.reload();
      },
    });
    const retryButton = operateProcessInstancePage.incidentsTableRows
      .getByRole('button', {name: 'Retry Incident'})
      .first();
    // Control: without it, a globally broken button would pass as gating.
    await expect(retryButton).toBeEnabled({timeout: UI_REFRESH_TIMEOUT});

    await suspendAndExpectSuspended(request, instance.processInstanceKey);
    await page.reload();
    await expect(operateProcessInstancePage.suspendedStateIcon).toBeVisible({
      timeout: UI_REFRESH_TIMEOUT,
    });
    await expect(retryButton).toBeDisabled({timeout: UI_REFRESH_TIMEOUT});
  });

  test('Variable editing stays available on a suspended instance', async ({
    request,
    operateProcessInstancePage,
  }) => {
    const serviceTask = await startServiceTaskInstance('sr-ui-vars');
    await suspendAndExpectSuspended(request, serviceTask.processInstanceKey);
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: serviceTask.processInstanceKey,
    });
    await expect(operateProcessInstancePage.suspendedStateIcon).toBeVisible({
      timeout: UI_REFRESH_TIMEOUT,
    });

    // The visible effect of #62745: editing used to be disabled on every scope
    // of a suspended instance, though the engine only refuses a user task
    // scope. That per-scope split is asserted over the API instead -- the UI
    // offers no variable controls on a user task scope either way, suspended or
    // not, so there is nothing here that suspension changes.
    await expect(operateProcessInstancePage.addVariableButton).toBeEnabled({
      timeout: UI_REFRESH_TIMEOUT,
    });
  });

  test('The operations log records the suspend and the resume', async ({
    request,
    operateProcessInstancePage,
  }) => {
    const {processInstanceKey} = await startServiceTaskInstance('sr-ui-log');
    await assertStatusCode(
      await suspendProcessInstance(request, processInstanceKey),
      204,
    );
    await expectProcessState(
      request,
      processInstanceKey,
      'SUSPENDED',
      extendedAssertionOptions,
    );
    await assertStatusCode(
      await resumeProcessInstance(request, processInstanceKey),
      204,
    );
    await expectProcessState(
      request,
      processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: processInstanceKey,
    });
    await operateProcessInstancePage.operationsLogTabButton.click();
    await expect(operateProcessInstancePage.operationsLogTable).toBeVisible({
      timeout: UI_REFRESH_TIMEOUT,
    });
    await expect(
      operateProcessInstancePage.operationsLogTable
        .getByText('SUSPEND', {
          exact: false,
        })
        .first(),
    ).toBeVisible({timeout: UI_REFRESH_TIMEOUT});
    await expect(
      operateProcessInstancePage.operationsLogTable
        .getByText('RESUME', {
          exact: false,
        })
        .first(),
    ).toBeVisible({timeout: UI_REFRESH_TIMEOUT});
  });

  test('Suspending and resuming from an instances-table row targets that row only', async ({
    request,
    page,
    operateHomePage,
    operateFiltersPanelPage,
  }) => {
    const subject = await startServiceTaskInstance('sr-ui-row');
    const control = await startServiceTaskInstance('sr-ui-row-control');

    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
    await operateFiltersPanelPage.displayOptionalFilter(
      'Process Instance Key(s)',
    );
    await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
      subject.processInstanceKey,
    );

    // Row actions carry no instance key in their accessible name, so they are
    // located by test id inside the row the filter left listed.
    const subjectRow = page
      .getByTestId('data-list')
      .getByRole('row')
      .filter({hasText: subject.processInstanceKey});
    const suspendRowAction = subjectRow.getByTestId('suspend-operation');
    // The row appears only once the instance reaches secondary storage, so the
    // action cannot be waited for directly — reload until the row is listed.
    await waitForAssertion({
      assertion: async () => {
        await expect(suspendRowAction).toBeVisible({
          timeout: UI_REFRESH_TIMEOUT,
        });
      },
      onFailure: async () => {
        await page.reload();
      },
    });
    await suspendRowAction.click();

    await expectProcessState(
      request,
      subject.processInstanceKey,
      'SUSPENDED',
      extendedAssertionOptions,
    );
    await expectProcessState(
      request,
      control.processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );

    await operateFiltersPanelPage.clickSuspendedInstancesCheckbox();
    const resumeRowAction = subjectRow.getByTestId('resume-operation');
    await waitForAssertion({
      assertion: async () => {
        await expect(resumeRowAction).toBeVisible({
          timeout: UI_REFRESH_TIMEOUT,
        });
      },
      onFailure: async () => {
        await page.reload();
      },
    });
    await resumeRowAction.click();

    await expectProcessState(
      request,
      subject.processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
  });

  test('A call activity child instance offers Suspend in its own header', async ({
    request,
    page,
    operateProcessInstancePage,
  }) => {
    const prefix = uniquePrefixedId('sr-ui-child');
    const {parentId, childId} = await deployCallActivityPair(prefix);
    const parent = await createInstanceOnceDeployed(parentId, 1);
    instancesToCancel.push(parent.processInstanceKey);

    let childKey = '';
    await expect(async () => {
      const instances = await searchProcessInstances(request, {
        processDefinitionId: childId,
      });
      expect(instances).toHaveLength(1);
      childKey = instances[0].processInstanceKey;
    }).toPass(extendedAssertionOptions);
    instancesToCancel.push(childKey);

    await operateProcessInstancePage.gotoProcessInstancePage({id: childKey});
    await expect(operateProcessInstancePage.instanceHeader).toBeVisible();

    // #60657 removed the root-only guard from these buttons, so a child offers
    // them like any other instance.
    await operateProcessInstancePage.suspendInstance(childKey);
    await expect(operateProcessInstancePage.suspendedStateIcon).toBeVisible({
      timeout: UI_REFRESH_TIMEOUT,
    });
    await expectProcessState(
      request,
      childKey,
      'SUSPENDED',
      extendedAssertionOptions,
    );
    // No cascade: the parent is untouched by its child's suspension.
    await expectProcessState(
      request,
      parent.processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );

    await operateProcessInstancePage.resumeInstance(childKey);
    await expectProcessState(
      request,
      childKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
    void page;
  });
});
