/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {createInstances, deploy} from '../zeebeClient';

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
    './e2e/resources/checkbox_task_with_form.bpmn',
    './e2e/resources/checklist_task_with_form.bpmn',
    './e2e/resources/date_and_time_task_with_form.bpmn',
    './e2e/resources/number_task_with_form.bpmn',
    './e2e/resources/radio_button_task_with_form.bpmn',
    './e2e/resources/select_task_with_form.bpmn',
    './e2e/resources/tag_list_task_with_form.bpmn',
    './e2e/resources/text-templating-form-task.bpmn',
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
    createInstances('Checkbox_User_Task', 1, 1),
    createInstances('Checklist_Task', 1, 1),
    createInstances('Date_and_Time_Task', 1, 1),
    createInstances('Number_Task', 1, 2),
    createInstances('Radio_Button_User_Task', 1, 1),
    createInstances('Select', 1, 1),
    createInstances('Tag_List_Task', 1, 1),
    createInstances('Text_Templating_Form_Task', 1, 1, {
      name: 'Jane',
      age: '50',
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
    await taskDetailsPage.assignToMeButton.click();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await expect(taskDetailsPage.completeButton).toBeEnabled();
    await expect(taskDetailsPage.assignee).toHaveText('Assigned to me');

    await taskDetailsPage.unassignButton.click();
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
    await taskDetailsPage.assignToMeButton.click();
    await taskDetailsPage.completeTaskButton.click();
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
    await taskDetailsPage.assignToMeButton.click();
    await expect(taskDetailsPage.unassignButton).toBeVisible();

    await taskDetailsPage.nameInput.fill('Jon');
    await taskDetailsPage.addressInput.fill('Earth');
    await taskDetailsPage.ageInput.fill('21');
    await taskDetailsPage.completeTaskButton.click();
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
    await taskDetailsPage.assignToMeButton.click();
    await expect(taskDetailsPage.completeButton).toBeEnabled();

    await taskPanelPage.filterBy('Assigned to me');
    await taskPanelPage.openTask('User registration');

    await expect(taskDetailsPage.nameInput).toBeVisible();
    await taskDetailsPage.nameInput.fill('Gaius Julius Caesar');
    await taskDetailsPage.addressInput.fill('Rome');
    await taskDetailsPage.ageInput.fill('55');
    await taskDetailsPage.completeTaskButton.click();
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
    await taskDetailsPage.assignToMeButton.click();

    await expect(taskDetailsPage.nameInput).toHaveValue('Jane');
    await expect(taskDetailsPage.addressInput).toHaveValue('');
    await expect(taskDetailsPage.ageInput).toHaveValue('50');

    await taskDetailsPage.nameInput.fill('Jon');
    await taskDetailsPage.addressInput.fill('Earth');
    await taskDetailsPage.ageInput.fill('21');
    await taskDetailsPage.completeTaskButton.click();
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

  test('task completion with number form by input', async ({
    taskPanelPage,
    taskDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('UserTask_Number');
    await taskDetailsPage.assignToMeButton.click();

    await taskDetailsPage.numberInput.fill('4');
    await taskDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('UserTask_Number');

    await expect(taskDetailsPage.numberInput).toHaveValue('4');
  });

  test('task completion with number form by buttons', async ({
    taskPanelPage,
    taskDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('UserTask_Number');
    await taskDetailsPage.assignToMeButton.click();

    await taskDetailsPage.incrementButton.click();
    await expect(taskDetailsPage.numberInput).toHaveValue('1');
    await taskDetailsPage.incrementButton.click();
    await expect(taskDetailsPage.numberInput).toHaveValue('2');
    await taskDetailsPage.decrementButton.click();
    await expect(taskDetailsPage.numberInput).toHaveValue('1');
    await taskDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('UserTask_Number');
    await expect(taskDetailsPage.numberInput).toHaveValue('1');
  });

  test('task completion with date and time form', async ({
    taskPanelPage,
    taskDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Date and Time Task');
    await taskDetailsPage.assignToMeButton.click();

    await taskDetailsPage.fillDate('1/1/3000');
    await taskDetailsPage.enterTime('12:00 PM');
    await taskDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Date and Time Task');

    await expect(taskDetailsPage.dateInput).toHaveValue('1/1/3000');
    await expect(taskDetailsPage.timeInput).toHaveValue('12:00 PM');
  });

  test('task completion with checkbox form', async ({
    taskPanelPage,
    taskDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Checkbox Task');
    await taskDetailsPage.assignToMeButton.click();

    await taskDetailsPage.checkbox.check();
    await taskDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Checkbox Task');

    await expect(taskDetailsPage.checkbox).toBeChecked();
  });

  test('task completion with select form', async ({
    taskPanelPage,
    taskDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Select User Task');
    await taskDetailsPage.assignToMeButton.click();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await taskDetailsPage.selectDropdownValue('Value');
    await taskDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Select User Task');

    await expect(taskDetailsPage.form.getByText('Value')).toBeVisible();
  });

  test('task completion with radio button form', async ({
    taskPanelPage,
    taskDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Radio Button Task');
    await taskDetailsPage.assignToMeButton.click();

    await page.getByText('Value').check();
    await taskDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Radio Button Task');

    await expect(page.getByText('Value')).toBeChecked();
  });

  test('task completion with checklist form', async ({
    taskPanelPage,
    taskDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Checklist User Task');
    await taskDetailsPage.assignToMeButton.click();

    await page.getByRole('checkbox', {name: 'Value1'}).check();
    await page.getByRole('checkbox', {name: 'Value2'}).check();
    await taskDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Checklist User Task');

    await expect(page.getByLabel('Value1')).toBeChecked();
    await expect(page.getByLabel('Value2')).toBeChecked();
  });

  // TODO issue #3719
  test.skip('task completion with tag list form', async ({
    taskPanelPage,
    taskDetailsPage,
    page,
  }) => {
    test.slow();
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Tag list Task');
    await taskDetailsPage.assignToMeButton.click();

    await taskDetailsPage.selectTaglistValues(['Value 2', 'Value']);
    await taskDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Tag list Task');

    await expect(
      taskDetailsPage.form.getByText('Value', {exact: true}),
    ).toBeVisible();
    await expect(taskDetailsPage.form.getByText('Value 2')).toBeVisible();
  });

  // TODO issue #3719
  test.skip('task completion with text template form', async ({
    taskPanelPage,
    taskDetailsPage,
    page,
  }) => {
    test.slow();
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Text_Templating_Form_Task');
    await taskDetailsPage.assignToMeButton.click();

    await expect(taskDetailsPage.form).toContainText('Hello Jane');
    await expect(taskDetailsPage.form).toContainText('You are 50 years old');
    await taskDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Text_Templating_Form_Task');

    await expect(taskDetailsPage.form).toContainText('Hello Jane');
    await expect(taskDetailsPage.form).toContainText('You are 50 years old');
  });
});
