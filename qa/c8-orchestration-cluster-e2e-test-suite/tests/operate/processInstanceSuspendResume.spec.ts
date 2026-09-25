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
  activateJobsByType,
  activateSingleJob,
  completeJob,
  deployCallActivityPair,
  createInstanceOnceDeployed,
  deployServiceTaskProcess,
  startServiceTaskInstance,
  resumeAndCompleteServiceTask,
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
import {assertStatusCode, buildUrl, jsonHeaders} from 'utils/http';
import {uniquePrefixedId, extendedAssertionOptions} from 'utils/constants';

/**
 * The UI half of suspend/resume. State changes the UI only has to reflect are
 * driven over REST; the UI controls are exercised only where the control itself
 * is what is under test.
 */

const UI_REFRESH_TIMEOUT = 15_000;
const instancesToCancel: string[] = [];

async function startInstance(prefix: string) {
  const instance = await startServiceTaskInstance(prefix);
  instancesToCancel.push(instance.processInstanceKey);
  return instance;
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
    const subject = await startInstance('sr-ui-header');
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
    await resumeAndCompleteServiceTask(
      request,
      subject.jobType,
      subject.processInstanceKey,
    );
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

    const activeActions =
      await operateProcessInstancePage.instanceHeaderActionNames();
    expect(
      activeActions.some((action) =>
        /^(suspend|suspend-operation)$/i.test(action.trim()),
      ),
    ).toBe(true);

    await suspendAndExpectSuspended(request, processInstanceKey);
    await operateProcessInstancePage.reloadUntilSuspended(UI_REFRESH_TIMEOUT);

    const suspendedActions =
      await operateProcessInstancePage.instanceHeaderActionNames();
    const offers = (pattern: RegExp) =>
      suspendedActions.some((action) => pattern.test(action.trim()));
    expect(offers(/^(resume|resume-operation)$/i)).toBe(true);
    expect(offers(/^(cancel|cancel-operation)$/i)).toBe(true);
    // Checked per action: joining them and matching a substring would accept a
    // menu that still ends with "Suspend".
    expect(offers(/^(suspend|suspend-operation)$/i)).toBe(false);
    await resumeAndCompleteServiceTask(request, jobType, processInstanceKey);
  });

  test('The Suspended filter returns the suspended instance', async ({
    request,
    page,
    operateHomePage,
    operateFiltersPanelPage,
  }) => {
    const suspended = await startInstance('sr-ui-filter');
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
    // The tab appears only once the incident has reached Operate's view, and
    // the table renders only once the tab is open, so both waits share one
    // retry budget. Waiting for the tab rather than probing it is what makes
    // the budget usable: a probe that finds no tab yet leaves the table wait
    // to time out against a table that cannot appear.
    await waitForAssertion({
      assertion: async () => {
        await expect(operateProcessInstancePage.incidentsTab).toBeVisible({
          timeout: UI_REFRESH_TIMEOUT,
        });
        await operateProcessInstancePage.incidentsTab.click();
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
    await operateProcessInstancePage.reloadUntilSuspended(UI_REFRESH_TIMEOUT);
    await expect(retryButton).toBeDisabled({timeout: UI_REFRESH_TIMEOUT});

    // The mirror of the assertion above: once resumed, the button works. The
    // job needs its retries back first, or the retry only fails again.
    await resumeProcessInstance(request, instance.processInstanceKey);
    await assertStatusCode(
      await request.patch(
        buildUrl('/jobs/{jobKey}', {jobKey: String(jobKey)}),
        {
          headers: jsonHeaders(),
          data: {changeset: {retries: 2}},
        },
      ),
      204,
    );
    await page.reload();
    await expect(retryButton).toBeEnabled({timeout: UI_REFRESH_TIMEOUT});
    await retryButton.click();

    await resumeAndCompleteServiceTask(
      request,
      jobType,
      instance.processInstanceKey,
    );
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

    // Editing an existing variable used to be blocked outright on a suspended
    // instance (#60873, fixed by #62745), so the edit itself is the assertion —
    // not merely that a control was enabled.
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
    await expect(async () => {
      const res = await request.post(buildUrl('/variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: instance.processInstanceKey,
            name: 'approved',
          },
        },
      });
      await assertStatusCode(res, 200);
      const items = (await res.json()).items ?? [];
      expect(items).toHaveLength(1);
      expect(items[0].value).toBe('"yes"');
    }).toPass(extendedAssertionOptions);
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

    // Applied, not just stored: the job the resumed instance hands out carries
    // the value the operator typed, not the one it was started with.
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
    await resumeAndCompleteServiceTask(request, jobType, processInstanceKey);
  });

  test('Suspending and resuming from an instances-table row targets that row only', async ({
    request,
    page,
    operateHomePage,
    operateFiltersPanelPage,
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

    const resumeRowAction = subjectRow.getByTestId('resume-operation');
    await waitForAssertion({
      assertion: async () => {
        await operateFiltersPanelPage.applySuspendedFilter();
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
    await resumeAndCompleteServiceTask(
      request,
      subject.jobType,
      subject.processInstanceKey,
    );
  });

  // Skipped due to bug 64086: https://github.com/camunda/camunda/issues/64086
  test.skip('Cancelling suspended instances from the toolbar terminates them', async ({
    request,
    page,
    operateHomePage,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateOperationsDetailsPage,
  }) => {
    const subject = await startInstance('sr-ui-batch-cancel');
    await suspendAndExpectSuspended(request, subject.processInstanceKey);

    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
    // Active and Incidents stay on, as the default view has them: the filter
    // the client builds from that combination is the one that loses the
    // suspended instances, and narrowing to Suspended alone would not
    // reproduce it.
    await operateFiltersPanelPage.applySuspendedFilter();
    await operateFiltersPanelPage.displayOptionalFilter(
      'Process Instance Key(s)',
    );
    await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
      subject.processInstanceKey,
    );
    await expect(
      page.getByText(subject.processInstanceKey, {exact: false}).first(),
    ).toBeVisible({timeout: UI_REFRESH_TIMEOUT});

    // Read the key off the request the toolbar makes: picking the newest batch
    // instead would pull in batches other specs create in parallel.
    const cancellationResponse = page.waitForResponse(
      (response) =>
        response.url().includes('/process-instances/cancellation') &&
        response.request().method() === 'POST',
    );
    await operateProcessesPage.cancelAllProcessInstancesInBatch();
    const batchOperationKey = String(
      (await (await cancellationResponse).json()).batchOperationKey,
    );

    await expectProcessState(
      request,
      subject.processInstanceKey,
      'TERMINATED',
      extendedAssertionOptions,
    );

    // A batch that matched nothing also reports Completed, so the state tile
    // alone proves nothing — the instance has to be listed among the items.
    await operateOperationsDetailsPage.goto(batchOperationKey);
    await waitForAssertion({
      assertion: async () => {
        await expect(operateOperationsDetailsPage.state).toHaveText(
          /completed/i,
          {timeout: UI_REFRESH_TIMEOUT},
        );
      },
      // The detail view does not refresh itself (#52021).
      onFailure: async () => {
        await page.reload();
      },
    });
    // Exact: a regex would also match a 1 inside a larger count.
    await expect(operateOperationsDetailsPage.summaryOfItems).toHaveText('1');
    await expect(
      operateOperationsDetailsPage.getProcessInstanceLink(
        subject.processInstanceKey,
      ),
    ).toBeVisible({timeout: UI_REFRESH_TIMEOUT});
  });

  test('A call activity child instance offers Suspend in its own header', async ({
    request,
    page,
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

    // The child's job is what both instances are waiting on, so running it
    // shows the resumed child still carries its parent to the end.
    await resumeAndCompleteServiceTask(request, childJobType, childKey);
    await expectProcessState(
      request,
      parent.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    void page;
  });
});
