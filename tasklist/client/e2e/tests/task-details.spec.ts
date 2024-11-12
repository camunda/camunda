/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@/test-fixtures';
import {createInstances, deploy} from '@/utils/zeebeClient';
import {waitForAssertion} from '@/utils/waitForAssertion';

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
    './e2e/resources/processWithDeployedForm.bpmn',
    './e2e/resources/create-invoice_8-5.form',
    './e2e/resources/Zeebe_Process.bpmn',
  ]);

  await Promise.all([
    createInstances('usertask_to_be_completed', 1, 1),
    createInstances('user_registration', 1, 3),
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
    createInstances('Tag_List_Task', 1, 1),
    createInstances('Radio_Button_User_Task', 1, 1),
    createInstances('Select', 1, 1),

    createInstances('Text_Templating_Form_Task', 1, 1, {
      name: 'Jane',
      age: '50',
    }),
    createInstances('processWithDeployedForm', 1, 1),
    createInstances('Zeebe_Process', 1, 1),
  ]);
});

test.beforeEach(async ({page, loginPage}) => {
  await loginPage.goto();
  await loginPage.login({
    username: 'demo',
    password: 'demo',
  });
  await expect(page).toHaveURL('/tasklist');
});

test.describe('task details page', () => {
  test('load task details when a task is selected', async ({tasksPage}) => {
    await tasksPage.openTask('usertask_to_be_completed');

    await expect(tasksPage.detailsHeader).toBeVisible();
    await expect(tasksPage.detailsHeader).toContainText('Some user activity');
    await expect(tasksPage.detailsHeader).toContainText(
      'usertask_to_be_completed',
    );
    await expect(tasksPage.detailsHeader).toContainText('Unassigned');
    await expect(tasksPage.assignToMeButton).toBeVisible();

    await expect(tasksPage.detailsPanel).toBeVisible();
    await expect(tasksPage.detailsPanel).toContainText('Creation date');
    await expect(
      tasksPage.detailsPanel.getByText(
        /^\d{2}\s\w{3}\s\d{4}\s-\s\d{1,2}:\d{2}\s(AM|PM)$/,
      ),
    ).toBeVisible();
    await expect(tasksPage.detailsPanel).toContainText('Candidates');
    await expect(tasksPage.detailsPanel).toContainText('No candidates');
    await expect(tasksPage.detailsPanel).toContainText('Due date');
    await expect(tasksPage.detailsPanel).toContainText('No due date');
    await expect(tasksPage.detailsPanel).not.toContainText('Completion date');
  });

  test('assign and unassign task', async ({page, tasksPage}) => {
    await tasksPage.openTask('usertask_to_be_completed');

    await expect(tasksPage.assignToMeButton).toBeVisible();
    await expect(tasksPage.completeTaskButton).toBeDisabled();
    await tasksPage.assignToMeButton.click();

    await expect(tasksPage.unassignButton).toBeVisible();
    await expect(tasksPage.completeTaskButton).toBeEnabled();
    await expect(tasksPage.assignee).toHaveText('Assigned to me', {
      useInnerText: true,
    });

    await tasksPage.unassignButton.click();
    await expect(tasksPage.assignToMeButton).toBeVisible();
    await expect(tasksPage.completeTaskButton).toBeDisabled();
    await expect(tasksPage.assignee).toHaveText('Unassigned', {
      useInnerText: true,
    });

    await page.reload();

    await expect(tasksPage.completeTaskButton).toBeDisabled();
  });

  test('complete task', async ({page, tasksPage}) => {
    await tasksPage.openTask('usertask_to_be_completed');

    const taskUrl = page.url();
    await tasksPage.assignToMeButton.click();
    await tasksPage.completeTaskButton.click();
    await expect(tasksPage.pickATaskHeader).toBeVisible();

    await page.goto(taskUrl);

    await expect(tasksPage.assignToMeButton).not.toBeVisible();
    await expect(tasksPage.unassignButton).not.toBeVisible();
    await expect(tasksPage.completeTaskButton).not.toBeVisible();
  });

  test('complete zeebe and job worker tasks', async ({
    page,
    tasksPage,
    taskVariableView,
  }) => {
    await tasksPage.openTask('Zeebe_user_task');
    await tasksPage.unassignButton.click();
    await expect(tasksPage.completeTaskButton).toBeDisabled();
    await tasksPage.assignToMeButton.click();
    await expect(tasksPage.completeTaskButton).toBeEnabled();
    await taskVariableView.addVariable({
      name: 'zeebeVar',
      value: '{"Name":"John","Age":20}',
    });
    await tasksPage.completeTaskButton.click();
    await expect(tasksPage.taskCompletionNotification).toBeVisible();

    await tasksPage.openTask('JobWorker_user_task');
    await expect(tasksPage.completeTaskButton).toBeDisabled();
    await tasksPage.assignToMeButton.click();
    await expect(tasksPage.completeTaskButton).toBeEnabled();
    await taskVariableView.addVariable({
      name: 'jobWorkerVar',
      value: '{"Name":"John","Age":22}',
    });
    await tasksPage.completeTaskButton.click();
    await expect(tasksPage.taskCompletionNotification).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('Zeebe_user_task');
    await expect(page.getByText('zeebeVar')).toBeVisible();
    await expect(tasksPage.assignToMeButton).not.toBeVisible();
    await expect(tasksPage.unassignButton).not.toBeVisible();
    await expect(tasksPage.completeTaskButton).not.toBeVisible();

    await tasksPage.openTask('JobWorker_user_task');

    // this is necessary because sometimes the importer takes some time to receive the variables
    await waitForAssertion({
      assertion: async () => {
        await expect(page.getByText('jobWorkerVar')).toBeVisible();
        await expect(page.getByText('zeebeVar')).toBeVisible();
      },
      onFailure: async () => {
        await page.reload();
      },
    });
    await expect(tasksPage.assignToMeButton).not.toBeVisible();
    await expect(tasksPage.unassignButton).not.toBeVisible();
    await expect(tasksPage.completeTaskButton).not.toBeVisible();
  });

  test('task completion with form', async ({page, tasksPage, taskFormView}) => {
    await tasksPage.openTask('User registration');

    await expect(taskFormView.nameInput).toBeVisible();
    await tasksPage.assignToMeButton.click();
    await expect(tasksPage.unassignButton).toBeVisible();

    await taskFormView.nameInput.fill('Jon');
    await taskFormView.addressInput.fill('Earth');
    await taskFormView.ageInput.fill('21');
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('User registration');

    await expect(taskFormView.nameInput).toHaveValue('Jon');
    await expect(taskFormView.addressInput).toHaveValue('Earth');
    await expect(taskFormView.ageInput).toHaveValue('21');
  });

  test('task completion with deployed form', async ({
    page,
    tasksPage,
    taskFormView,
  }) => {
    await tasksPage.openTask('processWithDeployedForm');

    await tasksPage.assignToMeButton.click();
    await expect(tasksPage.unassignButton).toBeVisible();
    await page.getByLabel('Client Name*').fill('Jon');
    await page.getByLabel('Client Address*').fill('Earth');
    await taskFormView.fillDatetimeField('Invoice Date', '1/1/3000');
    await taskFormView.fillDatetimeField('Due Date', '1/2/3000');
    await page.getByLabel('Invoice Number*').fill('123');

    await taskFormView.selectDropdownOption(
      'USD - United States Dollar',
      'EUR - Euro',
    );

    await page.getByRole('button', {name: /add new/i}).click();
    await taskFormView.forEachDynamicListItem(
      page.getByLabel('Item Name*'),
      async (element, index) => {
        await element.fill(`${'Laptop'}${index + 1}`);
      },
    );
    await taskFormView.forEachDynamicListItem(
      page.getByLabel('Unit Price*'),
      async (element, index) => {
        await element.fill(`${'1'}${index + 1}`);
      },
    );
    await taskFormView.forEachDynamicListItem(
      page.getByLabel('Quantity*'),
      async (element, index) => {
        await element.clear();
        await element.fill(`${'2'}${index + 1}`);
      },
    );

    await expect(taskFormView.form).toContainText('EUR 231');
    await expect(taskFormView.form).toContainText('EUR 264');
    await expect(taskFormView.form).toContainText('Total: EUR 544.5');

    await tasksPage.completeTaskButton.click();

    await expect(tasksPage.taskCompletionNotification).toBeVisible({
      timeout: 40000,
    });
    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('processWithDeployedForm');

    expect(page.getByLabel('Client Name*')).toHaveValue('Jon');
    expect(page.getByLabel('Client Address*')).toHaveValue('Earth');
    await expect(page.getByLabel('Invoice Date*')).toHaveValue('1/1/3000');
    expect(page.getByLabel('Due Date*')).toHaveValue('1/2/3000');
    expect(page.getByLabel('Invoice Number*')).toHaveValue('123');

    expect(
      await taskFormView.mapDynamicListItems(
        page.getByLabel('Item Name*'),
        async (element) => {
          return element.inputValue();
        },
      ),
    ).toEqual(['Laptop1', 'Laptop2']);

    expect(
      await taskFormView.mapDynamicListItems(
        page.getByLabel('Unit Price*'),
        async (element) => {
          return await element.inputValue();
        },
      ),
    ).toEqual(['11', '12']);

    expect(
      await taskFormView.mapDynamicListItems(
        page.getByLabel('Quantity*'),
        async (element) => {
          return await element.inputValue();
        },
      ),
    ).toEqual(['21', '22']);

    await expect(taskFormView.form).toContainText('EUR 231');
    await expect(taskFormView.form).toContainText('EUR 264');
    await expect(taskFormView.form).toContainText('Total: EUR 544.5');
  });

  test('task completion with form from assigned to me filter', async ({
    page,
    tasksPage,
    taskFormView,
  }) => {
    await tasksPage.openTask('User registration');

    await expect(taskFormView.nameInput).toBeVisible();
    await tasksPage.assignToMeButton.click();
    await expect(tasksPage.completeTaskButton).toBeEnabled();

    await tasksPage.filterBy('Assigned to me');
    await tasksPage.openTask('User registration');

    await expect(taskFormView.nameInput).toBeVisible();
    await taskFormView.nameInput.fill('Gaius Julius Caesar');
    await taskFormView.addressInput.fill('Rome');
    await taskFormView.ageInput.fill('55');
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('User registration');

    await expect(taskFormView.nameInput).toHaveValue('Gaius Julius Caesar');
    await expect(taskFormView.addressInput).toHaveValue('Rome');
    await expect(taskFormView.ageInput).toHaveValue('55');
  });

  test('task completion with prefilled form', async ({
    page,
    tasksPage,
    taskFormView,
  }) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('User registration with vars');
    await tasksPage.assignToMeButton.click();

    await expect(taskFormView.nameInput).toHaveValue('Jane');
    await expect(taskFormView.addressInput).toHaveValue('');
    await expect(taskFormView.ageInput).toHaveValue('50');

    await taskFormView.nameInput.fill('Jon');
    await taskFormView.addressInput.fill('Earth');
    await taskFormView.ageInput.fill('21');
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('User registration with vars');

    await expect(taskFormView.nameInput).toHaveValue('Jon');
    await expect(taskFormView.addressInput).toHaveValue('Earth');
    await expect(taskFormView.ageInput).toHaveValue('21');
  });

  test('should rerender forms properly', async ({tasksPage, taskFormView}) => {
    await tasksPage.openTask('User Task with form rerender 1');
    await expect(taskFormView.nameInput).toHaveValue('Mary');

    await tasksPage.openTask('User Task with form rerender 2');
    await expect(taskFormView.nameInput).toHaveValue('Stuart');
  });

  test('task completion with number form by input', async ({
    tasksPage,
    taskFormView,
    page,
  }) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('UserTask_Number');
    await tasksPage.assignToMeButton.click();

    await taskFormView.numberInput.fill('4');
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('UserTask_Number');

    await expect(taskFormView.numberInput).toHaveValue('4');
  });

  test('task completion with number form by buttons', async ({
    tasksPage,
    taskFormView,
    page,
  }) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('UserTask_Number');
    await tasksPage.assignToMeButton.click();

    await taskFormView.incrementButton.click();
    await expect(taskFormView.numberInput).toHaveValue('1');
    await taskFormView.incrementButton.click();
    await expect(taskFormView.numberInput).toHaveValue('2');
    await taskFormView.decrementButton.click();
    await expect(taskFormView.numberInput).toHaveValue('1');
    await taskFormView.numberInput.focus();
    await page.keyboard.press('Tab');
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('UserTask_Number');
    await expect(taskFormView.numberInput).toHaveValue('1');
  });

  test('task completion with date and time form', async ({
    tasksPage,
    taskFormView,
    page,
  }) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('Date and Time Task');
    await tasksPage.assignToMeButton.click();

    await taskFormView.fillDatetimeField('Date', '1/1/3000');
    await taskFormView.fillDatetimeField('Time', '12:00 PM');
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('Date and Time Task');

    await expect(taskFormView.dateInput).toHaveValue('1/1/3000');
    await expect(taskFormView.timeInput).toHaveValue('12:00 PM');
  });

  test('task completion with checkbox form', async ({
    tasksPage,
    taskFormView,
    page,
  }) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('Checkbox Task');
    await tasksPage.assignToMeButton.click();

    await taskFormView.checkbox.check();
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('Checkbox Task');

    await expect(taskFormView.checkbox).toBeChecked();
  });

  test('task completion with select form', async ({
    tasksPage,
    taskFormView,
    page,
  }) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('Select User Task');
    await tasksPage.assignToMeButton.click();

    await expect(tasksPage.unassignButton).toBeVisible();
    await taskFormView.selectDropdownValue('Value');
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('Select User Task');

    await expect(taskFormView.form.getByText('Value')).toBeVisible();
  });

  test('task completion with radio button form', async ({tasksPage, page}) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('Radio Button Task');
    await tasksPage.assignToMeButton.click();

    await page.getByText('Value').check();
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('Radio Button Task');

    await expect(page.getByText('Value')).toBeChecked();
  });

  test('task completion with checklist form', async ({tasksPage, page}) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('Checklist User Task');
    await tasksPage.assignToMeButton.click();

    await page.getByRole('checkbox', {name: 'Value1'}).check();
    await page.getByRole('checkbox', {name: 'Value2'}).check();
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('Checklist User Task');

    await expect(page.getByLabel('Value1')).toBeChecked();
    await expect(page.getByLabel('Value2')).toBeChecked();
  });

  // TODO issue #3719
  test.skip('task completion with tag list form', async ({
    tasksPage,
    taskFormView,
    page,
  }) => {
    test.slow();
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('Tag list Task');
    await tasksPage.assignToMeButton.click();

    await taskFormView.selectTaglistValues(['Value 2', 'Value']);
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('Tag list Task');

    await expect(
      taskFormView.form.getByText('Value', {exact: true}),
    ).toBeVisible();
    await expect(taskFormView.form.getByText('Value 2')).toBeVisible();
  });

  // TODO issue #3719
  test.skip('task completion with text template form', async ({
    tasksPage,
    taskFormView,
    page,
  }) => {
    test.slow();
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('Text_Templating_Form_Task');
    await tasksPage.assignToMeButton.click();

    await expect(taskFormView.form).toContainText('Hello Jane');
    await expect(taskFormView.form).toContainText('You are 50 years old');
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('Text_Templating_Form_Task');

    await expect(taskFormView.form).toContainText('Hello Jane');
    await expect(taskFormView.form).toContainText('You are 50 years old');
  });

  test('show process model', async ({tasksPage}) => {
    await tasksPage.openTask('User registration');

    await expect(tasksPage.detailsNav).toBeVisible();
    await tasksPage.detailsNav.getByText(/process/i).click();

    await expect(tasksPage.bpmnDiagram).toBeVisible();
  });
});
