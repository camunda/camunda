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
import {
  activateJobsByType,
  activateSingleJob,
  completeJob,
  deployCallActivityPair,
  createInstanceOnceDeployed,
  deployServiceTaskProcess,
  deployUserTaskProcess,
  startServiceTaskInstance,
  resumeAndCompleteServiceTask,
  batchOperationKeyForItem,
  expectVariableValue,
  updateJobRetries,
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

const UI_REFRESH_TIMEOUT = 15_000;
const instancesToCancel: string[] = [];

async function startInstance(prefix: string) {
  const instance = await startServiceTaskInstance(prefix);
  instancesToCancel.push(instance.processInstanceKey);
  return instance;
}

/** A user task is the one kind of work an operator can finish without a worker. */
async function startUserTaskInstance(prefix: string) {
  const processDefinitionId = uniquePrefixedId(prefix);
  await deployUserTaskProcess(processDefinitionId);
  const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
  instancesToCancel.push(instance.processInstanceKey);
  return {processDefinitionId, processInstanceKey: instance.processInstanceKey};
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

  test('An instance suspended and resumed from its header can be completed in the UI', async ({
    request,
    page,
    operateProcessInstancePage,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    const subject = await startUserTaskInstance('sr-ui-header');
    const control = await startInstance('sr-ui-header-control');
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
    // Finished where an operator would finish it: the resume is only proven by
    // work that runs after it, and completing the task needs a live instance.
    await navigateToAppHome(page, 'tasklist');
    await taskPanelPage.openTask(subject.processDefinitionId, {
      timeout: 60_000,
    });
    await taskDetailsPage.clickAssignToMeButton();
    await taskDetailsPage.clickCompleteTaskButton();

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: subject.processInstanceKey,
    });
    await operateProcessInstancePage.completedIconAssertion();
  });

  test('A suspended instance offers Resume and Cancel but not Suspend', async ({
    request,
    operateProcessInstancePage,
  }) => {
    const {jobType, processInstanceKey} = await startInstance('sr-ui-actions');
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: processInstanceKey,
    });
    await expect(operateProcessInstancePage.instanceHeader).toBeVisible();

    expect(await operateProcessInstancePage.instanceHeaderActions()).toContain(
      'suspend',
    );

    await suspendAndExpectSuspended(request, processInstanceKey);
    await operateProcessInstancePage.reloadUntilSuspended(UI_REFRESH_TIMEOUT);

    const suspendedActions =
      await operateProcessInstancePage.instanceHeaderActions();
    expect(suspendedActions).toContain('resume');
    expect(suspendedActions).toContain('cancel');
    expect(suspendedActions).not.toContain('suspend');
    await resumeAndCompleteServiceTask(request, jobType, processInstanceKey);
    // Re-read the instance from Operate: the detail view does not refresh
    // itself (#52021), so the API completion is only visible on a fresh load.
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: processInstanceKey,
    });
    await operateProcessInstancePage.completedIconAssertion();
  });

  test('The Suspended filter returns the suspended instance', async ({
    request,
    page,
    operateHomePage,
    operateFiltersPanelPage,
    operateProcessesPage,
  }) => {
    const suspended = await startInstance('sr-ui-filter');
    await suspendAndExpectSuspended(request, suspended.processInstanceKey);

    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
    await operateFiltersPanelPage.displayOptionalFilter(
      'Process Instance Key(s)',
    );
    await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
      suspended.processInstanceKey,
    );
    // After the keys, not before: writing them rewrites the search params and
    // drops the filter.
    await operateFiltersPanelPage.applySuspendedFilter();

    await expect(
      operateProcessesPage.processInstanceLinkByKey(
        suspended.processInstanceKey,
      ),
    ).toBeVisible({timeout: UI_REFRESH_TIMEOUT});
    await resumeAndCompleteServiceTask(
      request,
      suspended.jobType,
      suspended.processInstanceKey,
    );
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
    await operateProcessInstancePage.openIncidentsTab();
    const retryButton = operateProcessInstancePage.firstIncidentRetryButton;
    // Control: without it, a globally broken button would pass as gating.
    await expect(retryButton).toBeEnabled({timeout: UI_REFRESH_TIMEOUT});

    await suspendAndExpectSuspended(request, instance.processInstanceKey);
    await operateProcessInstancePage.reloadUntilSuspended(UI_REFRESH_TIMEOUT);
    await expect(retryButton).toBeDisabled({timeout: UI_REFRESH_TIMEOUT});

    // The mirror of the assertion above; the job needs its retries back first.
    await resumeProcessInstance(request, instance.processInstanceKey);
    await updateJobRetries(request, String(jobKey), 2);
    await page.reload();
    await expect(retryButton).toBeEnabled({timeout: UI_REFRESH_TIMEOUT});
    await retryButton.click();

    await resumeAndCompleteServiceTask(
      request,
      jobType,
      instance.processInstanceKey,
    );
    // Re-read the instance from Operate: the detail view does not refresh
    // itself (#52021), so the API completion is only visible on a fresh load.
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instance.processInstanceKey,
    });
    await operateProcessInstancePage.completedIconAssertion();
  });

  test('An existing variable can be edited on a suspended instance, and the edit is applied after the resume', async ({
    request,
    operateProcessInstancePage,
  }) => {
    const processDefinitionId = uniquePrefixedId('sr-ui-edit');
    const jobType = uniquePrefixedId('sr-ui-edit-job');
    await deployServiceTaskProcess(processDefinitionId, jobType);
    const instance = await createInstanceOnceDeployed(processDefinitionId, 1, {
      approved: 'no',
    });
    instancesToCancel.push(instance.processInstanceKey);

    await suspendAndExpectSuspended(request, instance.processInstanceKey);
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instance.processInstanceKey,
    });
    await expect(operateProcessInstancePage.suspendedStateIcon).toBeVisible({
      timeout: UI_REFRESH_TIMEOUT,
    });

    // #60873 blocked this outright (fixed by #62745), so the edit is the
    // assertion, not merely an enabled control.
    await operateProcessInstancePage.clickEditVariableButton('approved');
    await operateProcessInstancePage.clickVariableValueInput();
    await operateProcessInstancePage.clearVariableValueInput();
    await operateProcessInstancePage.fillVariableValueInput('"yes"');
    await expect(operateProcessInstancePage.saveVariableButton).toBeEnabled({
      timeout: UI_REFRESH_TIMEOUT,
    });
    await operateProcessInstancePage.saveVariableButton.click();
    await expect(operateProcessInstancePage.operationSpinner).toBeHidden({
      timeout: 60_000,
    });

    // Stored while still suspended, before anything resumes.
    await expectVariableValue(
      request,
      {processInstanceKey: instance.processInstanceKey, name: 'approved'},
      '"yes"',
      extendedAssertionOptions,
    );
    await expectProcessState(
      request,
      instance.processInstanceKey,
      'SUSPENDED',
      extendedAssertionOptions,
    );

    await assertStatusCode(
      await resumeProcessInstance(request, instance.processInstanceKey),
      204,
    );

    // Applied, not just stored: the job carries the edited value.
    const jobs = await activateJobsByType(
      request,
      jobType,
      instance.processInstanceKey,
      ['approved'],
    );
    expect(jobs).toHaveLength(1);
    expect(jobs[0].variables['approved']).toBe('yes');

    await completeJob(request, jobs[0].jobKey);
    await expectProcessState(
      request,
      instance.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    // Re-read the instance from Operate: the detail view does not refresh
    // itself (#52021), so the API completion is only visible on a fresh load.
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instance.processInstanceKey,
    });
    await operateProcessInstancePage.completedIconAssertion();
  });

  test('The operations log records the suspend and the resume', async ({
    request,
    operateProcessInstancePage,
  }) => {
    const {jobType, processInstanceKey} = await startInstance('sr-ui-log');
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
    for (const operationType of ['SUSPEND', 'RESUME']) {
      await expect(
        operateProcessInstancePage.operationsLogEntry(operationType),
      ).toBeVisible({timeout: UI_REFRESH_TIMEOUT});
    }
    await resumeAndCompleteServiceTask(request, jobType, processInstanceKey);
    // Re-read the instance from Operate: the detail view does not refresh
    // itself (#52021), so the API completion is only visible on a fresh load.
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: processInstanceKey,
    });
    await operateProcessInstancePage.completedIconAssertion();
  });

  test('Suspending and resuming from an instances-table row targets that row only', async ({
    request,
    page,
    operateHomePage,
    operateFiltersPanelPage,
    operateProcessesPage,
  }) => {
    const subject = await startInstance('sr-ui-row');
    const control = await startInstance('sr-ui-row-control');

    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
    await operateFiltersPanelPage.displayOptionalFilter(
      'Process Instance Key(s)',
    );
    await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
      subject.processInstanceKey,
    );

    await operateProcessesPage.clickSuspendRowAction(
      subject.processInstanceKey,
    );

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

    // Suspended must be on before the row offers Resume.
    await operateFiltersPanelPage.applySuspendedFilter();
    await operateProcessesPage.clickResumeRowAction(subject.processInstanceKey);

    await expectProcessState(
      request,
      subject.processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
    await resumeAndCompleteServiceTask(
      request,
      subject.jobType,
      subject.processInstanceKey,
    );
  });

  test('Cancelling suspended instances from the toolbar terminates every selected one', async ({
    request,
    page,
    operateHomePage,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateOperationsDetailsPage,
  }) => {
    // Two subjects, because one cannot tell "cancelled the selection" from
    // "cancelled something", and a control the filter leaves out, because a
    // batch that over-reaches looks identical from the subjects alone.
    const first = await startInstance('sr-ui-batch-cancel-a');
    const second = await startInstance('sr-ui-batch-cancel-b');
    const control = await startInstance('sr-ui-batch-cancel-control');
    for (const {processInstanceKey} of [first, second, control]) {
      await suspendAndExpectSuspended(request, processInstanceKey);
    }

    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
    // Active and Incidents stay on: that combination builds the filter that
    // loses suspended instances. Suspended alone would not reproduce it.
    await operateFiltersPanelPage.displayOptionalFilter(
      'Process Instance Key(s)',
    );
    await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
      `${first.processInstanceKey} ${second.processInstanceKey}`,
    );
    // After the keys, not before: writing them rewrites the search params and
    // drops a Suspended filter applied beforehand.
    await operateFiltersPanelPage.applySuspendedFilter();
    await operateProcessesPage.expectInstancesTableToHoldExactly([
      first.processInstanceKey,
      second.processInstanceKey,
    ]);

    await operateProcessesPage.cancelAllProcessInstancesInBatch();

    for (const {processInstanceKey} of [first, second]) {
      await expectProcessState(
        request,
        processInstanceKey,
        'TERMINATED',
        extendedAssertionOptions,
      );
    }
    await expectProcessState(
      request,
      control.processInstanceKey,
      'SUSPENDED',
      extendedAssertionOptions,
    );

    // Found by the item it acted on rather than by watching for the request:
    // the toolbar retries its interaction, so the first POST is not reliably
    // the one that submitted the batch.
    const batchOperationKey = await batchOperationKeyForItem(
      request,
      first.processInstanceKey,
    );

    // A batch that matched nothing also reports Completed.
    await operateOperationsDetailsPage.goto(batchOperationKey);
    await operateOperationsDetailsPage.expectState(/completed/i);
    // What the batch acted on, read from the items it lists rather than the
    // summary tile, whose badges come and go while the batch runs.
    for (const {processInstanceKey} of [first, second]) {
      await expect(
        operateOperationsDetailsPage.getProcessInstanceLink(processInstanceKey),
      ).toBeVisible({timeout: UI_REFRESH_TIMEOUT});
    }
    await expect(
      operateOperationsDetailsPage.getProcessInstanceLink(
        control.processInstanceKey,
      ),
    ).toBeHidden();

    // SUSPENDED alone passes on a control the batch reached and damaged.
    await resumeAndCompleteServiceTask(
      request,
      control.jobType,
      control.processInstanceKey,
    );
  });

  test('Suspending and resuming selected instances from the toolbar leaves the rest alone', async ({
    request,
    page,
    operateHomePage,
    operateFiltersPanelPage,
    operateProcessesPage,
  }) => {
    const first = await startInstance('sr-ui-batch-suspend-a');
    const second = await startInstance('sr-ui-batch-suspend-b');
    const control = await startInstance('sr-ui-batch-suspend-control');

    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
    await operateFiltersPanelPage.displayOptionalFilter(
      'Process Instance Key(s)',
    );
    await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
      `${first.processInstanceKey} ${second.processInstanceKey}`,
    );
    await operateProcessesPage.expectInstancesTableToHoldExactly([
      first.processInstanceKey,
      second.processInstanceKey,
    ]);

    await operateProcessesPage.suspendAllProcessInstancesInBatch();

    for (const {processInstanceKey} of [first, second]) {
      await expectProcessState(
        request,
        processInstanceKey,
        'SUSPENDED',
        extendedAssertionOptions,
      );
    }
    await expectProcessState(
      request,
      control.processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );

    await operateFiltersPanelPage.applySuspendedFilter();
    await operateProcessesPage.expectInstancesTableToHoldExactly([
      first.processInstanceKey,
      second.processInstanceKey,
    ]);

    await operateProcessesPage.resumeAllProcessInstancesInBatch();

    for (const {processInstanceKey} of [first, second]) {
      await expectProcessState(
        request,
        processInstanceKey,
        'ACTIVE',
        extendedAssertionOptions,
      );
      await expectSuspendedDate(
        request,
        processInstanceKey,
        false,
        extendedAssertionOptions,
      );
    }

    for (const {jobType, processInstanceKey} of [first, second, control]) {
      await resumeAndCompleteServiceTask(request, jobType, processInstanceKey);
    }
  });

  test('A call activity child instance offers Suspend in its own header', async ({
    request,
    operateProcessInstancePage,
  }) => {
    const prefix = uniquePrefixedId('sr-ui-child');
    const {parentId, childId, childJobType} =
      await deployCallActivityPair(prefix);
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

    // #60657 removed the root-only guard, so a child offers them too.
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
    // No cascade: the parent is untouched by its child's suspension, and an
    // operator looking at the parent sees it still running.
    await expectProcessState(
      request,
      parent.processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: parent.processInstanceKey,
    });
    await operateProcessInstancePage.activeIconAssertion();
    await expect(operateProcessInstancePage.suspendedStateIcon).toBeHidden();
    await operateProcessInstancePage.gotoProcessInstancePage({id: childKey});

    await operateProcessInstancePage.resumeInstance(childKey);
    await expectProcessState(
      request,
      childKey,
      'ACTIVE',
      extendedAssertionOptions,
    );

    // Both instances wait on the child's job.
    await resumeAndCompleteServiceTask(request, childJobType, childKey);
    await expectProcessState(
      request,
      parent.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    // Re-read the instance from Operate: the detail view does not refresh
    // itself (#52021), so the API completion is only visible on a fresh load.
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: parent.processInstanceKey,
    });
    await operateProcessInstancePage.completedIconAssertion();
  });
});
