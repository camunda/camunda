/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@fixtures';
import {expect} from '@playwright/test';
import {OperateProcessInstancePage} from '@pages/OperateProcessInstancePage';
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {
  assignUserTaskAndExpectAssigned,
  expectProcessState,
  expectUserTaskSuspended,
  expectVariableValues,
  resumeProcessInstance,
  startUserTaskForSuspension,
  suspendAndExpectSuspended,
} from '@requestHelpers';
import {extendedAssertionOptions} from 'utils/constants';
import {assertStatusCode} from 'utils/http';
import {cancelProcessInstance} from 'utils/zeebeClient';

const instancesToCancel: string[] = [];
const initialValues = {
  customer: 'Initial applicant',
  quantity: 1,
  approved: false,
};

test.describe('Suspended user tasks in Tasklist', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test.afterAll(async () => {
    const cancellations = await Promise.allSettled(
      instancesToCancel.map((key) => cancelProcessInstance(key)),
    );
    instancesToCancel.length = 0;
    expect(
      cancellations.filter((result) => result.status === 'rejected'),
    ).toEqual([]);
  });

  test('should update the open task when suspended from Operate', async ({
    request,
    page,
    context,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    const task = await startUserTaskForSuspension(
      request,
      'ut-ui-operate',
      instancesToCancel,
      initialValues,
    );
    await assignUserTaskAndExpectAssigned(request, task.userTaskKey, 'demo');
    await navigateToAppHome(page, 'tasklist');
    await taskPanelPage.openTask(task.processDefinitionId, {timeout: 60_000});
    await taskPanelPage.assertTaskSuspended(task.processDefinitionId, false);
    await expect(taskDetailsPage.suspendedLabel).toBeHidden();
    await expect(taskDetailsPage.unassignButton).toBeEnabled();
    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();

    const operatorPage = await context.newPage();
    try {
      const operate = new OperateProcessInstancePage(operatorPage);
      await operate.gotoProcessInstancePage({id: task.processInstanceKey});
      await expect(operate.instanceHeader).toBeVisible();
      await operate.suspendInstance(task.processInstanceKey);
      await expectUserTaskSuspended(request, task.userTaskKey, true);

      await page.bringToFront();
      await taskPanelPage.assertTaskSuspended(task.processDefinitionId);
      await expect(taskDetailsPage.suspendedLabel).toBeVisible();
      await expect(taskDetailsPage.unassignButton).toBeDisabled();
      await expect(taskDetailsPage.completeTaskButton).toBeDisabled();
    } finally {
      await operatorPage.close();
    }
  });

  test('should keep only the suspended task marked in every matching view', async ({
    request,
    page,
    taskPanelPage,
  }) => {
    const suspended = await startUserTaskForSuspension(
      request,
      'ut-ui-views-suspended',
      instancesToCancel,
      initialValues,
    );
    const active = await startUserTaskForSuspension(
      request,
      'ut-ui-views-active',
      instancesToCancel,
      initialValues,
    );
    await suspendAndExpectSuspended(request, suspended.processInstanceKey);
    await expectUserTaskSuspended(request, suspended.userTaskKey, true);
    await navigateToAppHome(page, 'tasklist');

    for (const view of ['All open tasks', 'Unassigned'] as const) {
      await test.step(`Check ${view}`, async () => {
        await taskPanelPage.filterBy(view);
        await taskPanelPage.assertTaskCardVisible(
          suspended.processDefinitionId,
          {timeout: 60_000},
        );
        await taskPanelPage.assertTaskCardVisible(active.processDefinitionId, {
          timeout: 60_000,
        });
        await taskPanelPage.assertTaskSuspended(suspended.processDefinitionId);
        await taskPanelPage.assertTaskSuspended(
          active.processDefinitionId,
          false,
        );
        for (const task of [suspended, active]) {
          await expect(
            taskPanelPage.taskLinkByName(task.processDefinitionId),
          ).toHaveAccessibleName(/^Unassigned task:/);
        }
      });
    }

    await assertStatusCode(
      await resumeProcessInstance(request, suspended.processInstanceKey),
      204,
    );
    await expectUserTaskSuspended(request, suspended.userTaskKey, false);
    for (const task of [suspended, active]) {
      await assignUserTaskAndExpectAssigned(request, task.userTaskKey, 'demo');
    }
    await suspendAndExpectSuspended(request, suspended.processInstanceKey);
    await expectUserTaskSuspended(request, suspended.userTaskKey, true);

    await test.step('Check Assigned to me', async () => {
      await taskPanelPage.filterBy('Assigned to me');
      await taskPanelPage.assertTaskCardVisible(suspended.processDefinitionId, {
        timeout: 60_000,
      });
      await taskPanelPage.assertTaskCardVisible(active.processDefinitionId, {
        timeout: 60_000,
      });
      await taskPanelPage.assertTaskSuspended(suspended.processDefinitionId);
      await taskPanelPage.assertTaskSuspended(
        active.processDefinitionId,
        false,
      );
      for (const task of [suspended, active]) {
        await expect(
          taskPanelPage.taskLinkByName(task.processDefinitionId),
        ).toHaveAccessibleName(/^Task assigned to me:/);
      }
    });
  });

  test('should disable assignment with a suspension tooltip', async ({
    request,
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    const task = await startUserTaskForSuspension(
      request,
      'ut-ui-unassigned',
      instancesToCancel,
      initialValues,
    );
    await navigateToAppHome(page, 'tasklist');
    await taskPanelPage.openTask(task.processDefinitionId, {timeout: 60_000});
    await expect(taskDetailsPage.assignToMeButton).toBeEnabled();
    await expect(taskDetailsPage.suspendedLabel).toBeHidden();

    await suspendAndExpectSuspended(request, task.processInstanceKey);
    await expectUserTaskSuspended(request, task.userTaskKey, true);
    await taskPanelPage.assertTaskSuspended(task.processDefinitionId);

    await expect(taskDetailsPage.suspendedLabel).toBeVisible();
    await taskDetailsPage.assertSuspendedActionDisabled(
      taskDetailsPage.assignToMeButton,
    );
  });

  test('should disable assigned actions during suspension and complete after resume', async ({
    request,
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    const task = await startUserTaskForSuspension(
      request,
      'ut-ui-assigned',
      instancesToCancel,
      initialValues,
    );
    await assignUserTaskAndExpectAssigned(request, task.userTaskKey, 'demo');
    await navigateToAppHome(page, 'tasklist');
    await taskPanelPage.openTask(task.processDefinitionId, {timeout: 60_000});

    const actions = [
      taskDetailsPage.unassignButton,
      taskDetailsPage.completeTaskButton,
      taskDetailsPage.addVariableButton,
    ];
    for (const button of actions) {
      await expect(button).toBeEnabled();
    }
    for (const name of Object.keys(initialValues)) {
      await expect(taskDetailsPage.variableValueInput(name)).toBeEditable();
    }

    await suspendAndExpectSuspended(request, task.processInstanceKey);
    await expectUserTaskSuspended(request, task.userTaskKey, true);
    await taskPanelPage.assertTaskSuspended(task.processDefinitionId);

    await expect(taskDetailsPage.suspendedLabel).toBeVisible();
    for (const button of actions) {
      await taskDetailsPage.assertSuspendedActionDisabled(button);
    }
    for (const name of Object.keys(initialValues)) {
      await expect(taskDetailsPage.variableValueInput(name)).toBeDisabled();
    }

    await assertStatusCode(
      await resumeProcessInstance(request, task.processInstanceKey),
      204,
    );
    await expectUserTaskSuspended(request, task.userTaskKey, false);
    await taskPanelPage.assertTaskSuspended(task.processDefinitionId, false);

    await expect(taskDetailsPage.suspendedLabel).toBeHidden();
    for (const button of actions) {
      await expect(button).toBeEnabled();
    }
    for (const name of Object.keys(initialValues)) {
      await expect(taskDetailsPage.variableValueInput(name)).toBeEditable();
    }
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(
      taskPanelPage.taskCardByName(task.processDefinitionId),
    ).toBeHidden({timeout: 15_000});
    await expectProcessState(
      request,
      task.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('should make a Camunda form read-only until resume and submit its values', async ({
    request,
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    const task = await startUserTaskForSuspension(
      request,
      'ut-ui-form',
      instancesToCancel,
      initialValues,
      true,
    );
    await assignUserTaskAndExpectAssigned(request, task.userTaskKey, 'demo');
    await navigateToAppHome(page, 'tasklist');
    await taskPanelPage.openTask(task.processDefinitionId, {timeout: 60_000});
    await expect(taskDetailsPage.formNameInput).toBeEditable();
    await expect(taskDetailsPage.numberInput).toBeEditable();
    await expect(taskDetailsPage.checkbox).toBeEnabled();

    await suspendAndExpectSuspended(request, task.processInstanceKey);
    await expectUserTaskSuspended(request, task.userTaskKey, true);
    await taskPanelPage.assertTaskSuspended(task.processDefinitionId);

    await expect(taskDetailsPage.suspendedLabel).toBeVisible();
    await expect(taskDetailsPage.formNameInput).not.toBeEditable();
    await expect(taskDetailsPage.numberInput).not.toBeEditable();
    await expect(taskDetailsPage.checkbox).not.toBeEditable();
    await expect(taskDetailsPage.completeTaskButton).toBeDisabled();

    await assertStatusCode(
      await resumeProcessInstance(request, task.processInstanceKey),
      204,
    );
    await expectUserTaskSuspended(request, task.userTaskKey, false);
    await taskPanelPage.assertTaskSuspended(task.processDefinitionId, false);

    await expect(taskDetailsPage.suspendedLabel).toBeHidden();
    await expect(taskDetailsPage.formNameInput).toBeEditable();
    await expect(taskDetailsPage.numberInput).toBeEditable();
    await expect(taskDetailsPage.checkbox).toBeEnabled();
    await taskDetailsPage.fillTextInput('Name', 'Approved applicant');
    await taskDetailsPage.fillNumber('7');
    await taskDetailsPage.checkCheckbox();
    await taskDetailsPage.clickCompleteTaskButton();
    await expectProcessState(
      request,
      task.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectVariableValues(request, task.processInstanceKey, {
      customer: 'Approved applicant',
      quantity: 7,
      approved: true,
    });
  });

  test('should retain unsaved form input through suspension and resume without navigation', async ({
    request,
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    const task = await startUserTaskForSuspension(
      request,
      'ut-ui-draft',
      instancesToCancel,
      initialValues,
      true,
    );
    await assignUserTaskAndExpectAssigned(request, task.userTaskKey, 'demo');
    await navigateToAppHome(page, 'tasklist');
    await taskPanelPage.openTask(task.processDefinitionId, {timeout: 60_000});
    await taskDetailsPage.fillTextInput('Name', 'Unsaved applicant');
    await taskDetailsPage.fillNumber('9');
    await taskDetailsPage.checkCheckbox();
    await expect(taskDetailsPage.formNameInput).toHaveValue(
      'Unsaved applicant',
    );
    await expect(taskDetailsPage.numberInput).toHaveValue('9');
    await expect(taskDetailsPage.checkbox).toBeChecked();
    await expectVariableValues(request, task.processInstanceKey, initialValues);

    await suspendAndExpectSuspended(request, task.processInstanceKey);
    await expectUserTaskSuspended(request, task.userTaskKey, true);
    await taskPanelPage.assertTaskSuspended(task.processDefinitionId);

    await expect(taskDetailsPage.suspendedLabel).toBeVisible();
    await expect(taskDetailsPage.formNameInput).not.toBeEditable();
    await expect(taskDetailsPage.numberInput).not.toBeEditable();
    await expect(taskDetailsPage.checkbox).not.toBeEditable();
    await expect(taskDetailsPage.completeTaskButton).toBeDisabled();
    await expect(taskDetailsPage.formNameInput).toHaveValue(
      'Unsaved applicant',
    );
    await expect(taskDetailsPage.numberInput).toHaveValue('9');
    await expect(taskDetailsPage.checkbox).toBeChecked();

    await assertStatusCode(
      await resumeProcessInstance(request, task.processInstanceKey),
      204,
    );
    await expectUserTaskSuspended(request, task.userTaskKey, false);
    await taskPanelPage.assertTaskSuspended(task.processDefinitionId, false);

    await expect(taskDetailsPage.suspendedLabel).toBeHidden();
    await expect(taskDetailsPage.formNameInput).toBeEditable();
    await expect(taskDetailsPage.numberInput).toBeEditable();
    await expect(taskDetailsPage.checkbox).toBeEnabled();
    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPage.formNameInput).toHaveValue(
      'Unsaved applicant',
    );
    await expect(taskDetailsPage.numberInput).toHaveValue('9');
    await expect(taskDetailsPage.checkbox).toBeChecked();
    await taskDetailsPage.clickCompleteTaskButton();
    await expectProcessState(
      request,
      task.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectVariableValues(request, task.processInstanceKey, {
      customer: 'Unsaved applicant',
      quantity: 9,
      approved: true,
    });
  });
});
