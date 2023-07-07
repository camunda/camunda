/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {deploy, createInstances} from '../zeebeClient';

test.afterAll(async ({resetData}) => {
  await resetData();
});

test.beforeAll(async () => {
  await deploy([
    './e2e/resources/usertask_to_be_completed.bpmn',
    './e2e/resources/user_task_with_form.bpmn',
    './e2e/resources/user_task_with_form_and_vars.bpmn',
    './e2e/resources/user_task_with_form_rerender_1.bpmn',
    './e2e/resources/user_task_with_form_rerender_2.bpmn',
  ]);
  await Promise.all([
    createInstances('usertask_to_be_completed', 1, 1),
    createInstances('user_registration', 1, 2),
    createInstances('user_registration_with_vars', 1, 2, {
      name: 'Jane',
      age: '50',
    }),
    createInstances('user_task_with_form_rerender_1', 1, 1, {
      name: 'Mary',
      age: '20',
    }),
    createInstances('user_task_with_form_rerender_2', 1, 1, {
      name: 'Stuart',
      age: '30',
    }),
  ]);
});

test.beforeEach(async ({page, testSetupPage, loginPage}) => {
  await testSetupPage.goToLoginPage();
  await loginPage.login({
    username: 'demo',
    password: 'demo',
  });
  await expect(page).toHaveURL('/');
});

test.describe('task details page', () => {
  test('load task details when a task is selected', async ({
    taskDetailsPage,
    taskPanelPage,
  }) => {
    await taskPanelPage.openTask('usertask_to_be_completed');

    await expect(taskDetailsPage.detailsHeader).toBeVisible();
    await expect(taskDetailsPage.detailsHeader).toContainText(
      'Some user activity',
    );
    await expect(taskDetailsPage.detailsHeader).toContainText(
      'usertask_to_be_completed',
    );
    await expect(taskDetailsPage.detailsHeader).toContainText('Unassigned');
    await expect(taskDetailsPage.assignToMeButton).toBeVisible();

    await expect(taskDetailsPage.detailsPanel).toBeVisible();
    await expect(taskDetailsPage.detailsPanel).toContainText('Creation date');
    await expect(
      taskDetailsPage.detailsPanel.getByText(
        /^\d{2}\s\w{3}\s\d{4}\s-\s\d{2}:\d{2}\s(AM|PM)$/,
      ),
    ).toBeVisible();
    await expect(taskDetailsPage.detailsPanel).toContainText('Candidates');
    await expect(taskDetailsPage.detailsPanel).toContainText('No candidates');
    await expect(taskDetailsPage.detailsPanel).toContainText('Completion date');
    await expect(taskDetailsPage.detailsPanel).toContainText('Pending task');
    await expect(taskDetailsPage.detailsPanel).toContainText('Due date');
    await expect(taskDetailsPage.detailsPanel).toContainText('No due date');
    await expect(taskDetailsPage.detailsPanel).toContainText('Follow up date');
    await expect(taskDetailsPage.detailsPanel).toContainText(
      'No follow up date',
    );
  });

  test('assign and unassign task', async ({
    page,
    taskDetailsPage,
    taskPanelPage,
  }) => {
    await taskPanelPage.openTask('usertask_to_be_completed');

    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await expect(taskDetailsPage.completeButton).toBeDisabled();
    await taskDetailsPage.clickAssignToMeButton();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await expect(taskDetailsPage.completeButton).toBeEnabled();
    await expect(taskDetailsPage.assignee).toHaveText('Assigned to me');

    await taskDetailsPage.clickUnassignButton();
    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await expect(taskDetailsPage.completeButton).toBeDisabled();
    await expect(taskDetailsPage.assignee).toHaveText('Unassigned');

    await page.reload();

    await expect(taskDetailsPage.completeButton).toBeDisabled();
  });

  test('complete task', async ({page, taskDetailsPage, taskPanelPage}) => {
    await taskPanelPage.openTask('usertask_to_be_completed');

    await expect(taskDetailsPage.pendingTaskDescription).toBeVisible();
    const taskUrl = page.url();
    await taskDetailsPage.clickAssignToMeButton();
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.pickATaskHeader).toBeVisible();

    await page.goto(taskUrl);

    await expect(taskDetailsPage.assignToMeButton).not.toBeVisible();
    await expect(taskDetailsPage.unassignButton).not.toBeVisible();
    await expect(taskDetailsPage.completeTaskButton).not.toBeVisible();
    await expect(taskDetailsPage.pendingTaskDescription).not.toBeVisible();
  });

  test('task completion with form', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.openTask('User registration');

    await expect(taskDetailsPage.nameInput).toBeVisible();
    await taskDetailsPage.clickAssignToMeButton();
    await expect(taskDetailsPage.unassignButton).toBeVisible();

    await taskDetailsPage.nameInput.fill('Jon');
    await taskDetailsPage.addressInput.fill('Earth');
    await taskDetailsPage.ageInput.fill('21');
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('User registration');

    await expect(taskDetailsPage.nameInput).toHaveValue('Jon');
    await expect(taskDetailsPage.addressInput).toHaveValue('Earth');
    await expect(taskDetailsPage.ageInput).toHaveValue('21');
  });

  test('task completion with form from assigned to me filter', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.openTask('User registration');

    await expect(taskDetailsPage.nameInput).toBeVisible();
    await taskDetailsPage.clickAssignToMeButton();
    await expect(taskDetailsPage.completeButton).toBeEnabled();

    await taskPanelPage.filterBy('Assigned to me');
    await taskPanelPage.openTask('User registration');

    await expect(taskDetailsPage.nameInput).toBeVisible();
    await taskDetailsPage.nameInput.fill('Gaius Julius Caesar');
    await taskDetailsPage.addressInput.fill('Rome');
    await taskDetailsPage.ageInput.fill('55');
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('User registration');

    await expect(taskDetailsPage.nameInput).toHaveValue('Gaius Julius Caesar');
    await expect(taskDetailsPage.addressInput).toHaveValue('Rome');
    await expect(taskDetailsPage.ageInput).toHaveValue('55');
  });

  test('task completion with prefilled form', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('User registration with vars');
    await taskDetailsPage.clickAssignToMeButton();

    await expect(taskDetailsPage.nameInput).toHaveValue('Jane');
    await expect(taskDetailsPage.addressInput).toHaveValue('');
    await expect(taskDetailsPage.ageInput).toHaveValue('50');

    await taskDetailsPage.nameInput.fill('Jon');
    await taskDetailsPage.addressInput.fill('Earth');
    await taskDetailsPage.ageInput.fill('21');
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('User registration with vars');

    await expect(taskDetailsPage.nameInput).toHaveValue('Jon');
    await expect(taskDetailsPage.addressInput).toHaveValue('Earth');
    await expect(taskDetailsPage.ageInput).toHaveValue('21');
  });

  test('should rerender forms properly', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.openTask('User Task with form rerender 1');
    await expect(taskDetailsPage.nameInput).toHaveValue('Mary');

    await taskPanelPage.openTask('User Task with form rerender 2');
    await expect(taskDetailsPage.nameInput).toHaveValue('Stuart');
  });
});
