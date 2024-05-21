/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
    './e2e/resources/processWithDeployedForm.bpmn',
    './e2e/resources/create-invoice_8-5.form',
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
    await expect(taskDetailsPage.detailsPanel).toContainText('Due date');
    await expect(taskDetailsPage.detailsPanel).toContainText('No due date');
    await expect(taskDetailsPage.detailsPanel).not.toContainText(
      'Completion date',
    );
  });

  test('assign and unassign task', async ({
    page,
    taskDetailsPage,
    taskPanelPage,
    formJSDetailsPage,
  }) => {
    await taskPanelPage.openTask('usertask_to_be_completed');

    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await expect(formJSDetailsPage.completeTaskButton).toBeDisabled();
    await taskDetailsPage.assignToMeButton.click();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await expect(formJSDetailsPage.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPage.assignee).toHaveText('Assigned to me', {
      useInnerText: true,
    });

    await taskDetailsPage.unassignButton.click();
    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await expect(formJSDetailsPage.completeTaskButton).toBeDisabled();
    await expect(taskDetailsPage.assignee).toHaveText('Unassigned', {
      useInnerText: true,
    });

    await page.reload();

    await expect(formJSDetailsPage.completeTaskButton).toBeDisabled();
  });

  test('complete task', async ({
    page,
    taskDetailsPage,
    taskPanelPage,
    formJSDetailsPage,
  }) => {
    await taskPanelPage.openTask('usertask_to_be_completed');

    const taskUrl = page.url();
    await taskDetailsPage.assignToMeButton.click();
    await formJSDetailsPage.completeTaskButton.click();
    await expect(taskDetailsPage.pickATaskHeader).toBeVisible();

    await page.goto(taskUrl);

    await expect(taskDetailsPage.assignToMeButton).not.toBeVisible();
    await expect(taskDetailsPage.unassignButton).not.toBeVisible();
    await expect(formJSDetailsPage.completeTaskButton).not.toBeVisible();
  });

  test('task completion with form', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
  }) => {
    await taskPanelPage.openTask('User registration');

    await expect(formJSDetailsPage.nameInput).toBeVisible();
    await taskDetailsPage.assignToMeButton.click();
    await expect(taskDetailsPage.unassignButton).toBeVisible();

    await formJSDetailsPage.nameInput.fill('Jon');
    await formJSDetailsPage.addressInput.fill('Earth');
    await formJSDetailsPage.ageInput.fill('21');
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('User registration');

    await expect(formJSDetailsPage.nameInput).toHaveValue('Jon');
    await expect(formJSDetailsPage.addressInput).toHaveValue('Earth');
    await expect(formJSDetailsPage.ageInput).toHaveValue('21');
  });

  test('task completion with deployed form', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
  }) => {
    await taskPanelPage.openTask('processWithDeployedForm');

    await taskDetailsPage.assignToMeButton.click();
    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await page.getByLabel('Client Name*').fill('Jon');
    await page.getByLabel('Client Address*').fill('Earth');
    await formJSDetailsPage.fillDateField('Invoice Date*', '1/1/3000');
    await formJSDetailsPage.fillDateField('Due Date*', '1/2/3000');
    await page.getByLabel('Invoice Number*').fill('123');

    await formJSDetailsPage.selectDropdownOption(
      'USD - United States Dollar',
      'EUR - Euro',
    );

    await page.getByRole('button', {name: /add new/i}).click();
    await formJSDetailsPage.forEachDynamicListItem(
      page.getByLabel('Item Name*'),
      async (element, index) => {
        await element.fill(`${'Laptop'}${index + 1}`);
      },
    );
    await formJSDetailsPage.forEachDynamicListItem(
      page.getByLabel('Unit Price*'),
      async (element, index) => {
        await element.fill(`${'1'}${index + 1}`);
      },
    );
    await formJSDetailsPage.forEachDynamicListItem(
      page.getByLabel('Quantity*'),
      async (element, index) => {
        await element.clear();
        await element.fill(`${'2'}${index + 1}`);
      },
    );

    await expect(formJSDetailsPage.form).toContainText('EUR 231');
    await expect(formJSDetailsPage.form).toContainText('EUR 264');
    await expect(formJSDetailsPage.form).toContainText('Total: EUR 544.5');

    await formJSDetailsPage.completeTaskButton.click();

    await expect(taskDetailsPage.taskCompletionNotification).toBeVisible({
      timeout: 40000,
    });
    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('processWithDeployedForm');

    expect(page.getByLabel('Client Name*')).toHaveValue('Jon');
    expect(page.getByLabel('Client Address*')).toHaveValue('Earth');
    await expect(page.getByLabel('Invoice Date*')).toHaveValue('1/1/3000');
    expect(page.getByLabel('Due Date*')).toHaveValue('1/2/3000');
    expect(page.getByLabel('Invoice Number*')).toHaveValue('123');

    expect(
      await formJSDetailsPage.mapDynamicListItems(
        page.getByLabel('Item Name*'),
        async (element) => {
          return element.inputValue();
        },
      ),
    ).toEqual(['Laptop1', 'Laptop2']);

    expect(
      await formJSDetailsPage.mapDynamicListItems(
        page.getByLabel('Unit Price*'),
        async (element) => {
          return await element.inputValue();
        },
      ),
    ).toEqual(['11', '12']);

    expect(
      await formJSDetailsPage.mapDynamicListItems(
        page.getByLabel('Quantity*'),
        async (element) => {
          return await element.inputValue();
        },
      ),
    ).toEqual(['21', '22']);

    await expect(formJSDetailsPage.form).toContainText('EUR 231');
    await expect(formJSDetailsPage.form).toContainText('EUR 264');
    await expect(formJSDetailsPage.form).toContainText('Total: EUR 544.5');
  });

  test('task completion with form from assigned to me filter', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
  }) => {
    await taskPanelPage.openTask('User registration');

    await expect(formJSDetailsPage.nameInput).toBeVisible();
    await taskDetailsPage.assignToMeButton.click();
    await expect(formJSDetailsPage.completeTaskButton).toBeEnabled();

    await taskPanelPage.filterBy('Assigned to me');
    await taskPanelPage.openTask('User registration');

    await expect(formJSDetailsPage.nameInput).toBeVisible();
    await formJSDetailsPage.nameInput.fill('Gaius Julius Caesar');
    await formJSDetailsPage.addressInput.fill('Rome');
    await formJSDetailsPage.ageInput.fill('55');
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('User registration');

    await expect(formJSDetailsPage.nameInput).toHaveValue(
      'Gaius Julius Caesar',
    );
    await expect(formJSDetailsPage.addressInput).toHaveValue('Rome');
    await expect(formJSDetailsPage.ageInput).toHaveValue('55');
  });

  test('task completion with prefilled form', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('User registration with vars');
    await taskDetailsPage.assignToMeButton.click();

    await expect(formJSDetailsPage.nameInput).toHaveValue('Jane');
    await expect(formJSDetailsPage.addressInput).toHaveValue('');
    await expect(formJSDetailsPage.ageInput).toHaveValue('50');

    await formJSDetailsPage.nameInput.fill('Jon');
    await formJSDetailsPage.addressInput.fill('Earth');
    await formJSDetailsPage.ageInput.fill('21');
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('User registration with vars');

    await expect(formJSDetailsPage.nameInput).toHaveValue('Jon');
    await expect(formJSDetailsPage.addressInput).toHaveValue('Earth');
    await expect(formJSDetailsPage.ageInput).toHaveValue('21');
  });

  test('should rerender forms properly', async ({
    taskPanelPage,
    formJSDetailsPage,
  }) => {
    await taskPanelPage.openTask('User Task with form rerender 1');
    await expect(formJSDetailsPage.nameInput).toHaveValue('Mary');

    await taskPanelPage.openTask('User Task with form rerender 2');
    await expect(formJSDetailsPage.nameInput).toHaveValue('Stuart');
  });

  test('task completion with number form by input', async ({
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('UserTask_Number');
    await taskDetailsPage.assignToMeButton.click();

    await formJSDetailsPage.numberInput.fill('4');
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('UserTask_Number');

    await expect(formJSDetailsPage.numberInput).toHaveValue('4');
  });

  test('task completion with number form by buttons', async ({
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('UserTask_Number');
    await taskDetailsPage.assignToMeButton.click();

    await formJSDetailsPage.incrementButton.click();
    await expect(formJSDetailsPage.numberInput).toHaveValue('1');
    await formJSDetailsPage.incrementButton.click();
    await expect(formJSDetailsPage.numberInput).toHaveValue('2');
    await formJSDetailsPage.decrementButton.click();
    await expect(formJSDetailsPage.numberInput).toHaveValue('1');
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('UserTask_Number');
    await expect(formJSDetailsPage.numberInput).toHaveValue('1');
  });

  test('task completion with date and time form', async ({
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Date and Time Task');
    await taskDetailsPage.assignToMeButton.click();

    await formJSDetailsPage.fillDate('1/1/3000');
    await formJSDetailsPage.enterTime('12:00 PM');
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Date and Time Task');

    await expect(formJSDetailsPage.dateInput).toHaveValue('1/1/3000');
    await expect(formJSDetailsPage.timeInput).toHaveValue('12:00 PM');
  });

  test('task completion with checkbox form', async ({
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Checkbox Task');
    await taskDetailsPage.assignToMeButton.click();

    await formJSDetailsPage.checkbox.check();
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Checkbox Task');

    await expect(formJSDetailsPage.checkbox).toBeChecked();
  });

  test('task completion with select form', async ({
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Select User Task');
    await taskDetailsPage.assignToMeButton.click();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await formJSDetailsPage.selectDropdownValue('Value');
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Select User Task');

    await expect(formJSDetailsPage.form.getByText('Value')).toBeVisible();
  });

  test('task completion with radio button form', async ({
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Radio Button Task');
    await taskDetailsPage.assignToMeButton.click();

    await page.getByText('Value').check();
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Radio Button Task');

    await expect(page.getByText('Value')).toBeChecked();
  });

  test('task completion with checklist form', async ({
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
    page,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Checklist User Task');
    await taskDetailsPage.assignToMeButton.click();

    await page.getByRole('checkbox', {name: 'Value1'}).check();
    await page.getByRole('checkbox', {name: 'Value2'}).check();
    await formJSDetailsPage.completeTaskButton.click();
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
    formJSDetailsPage,
    page,
  }) => {
    test.slow();
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Tag list Task');
    await taskDetailsPage.assignToMeButton.click();

    await formJSDetailsPage.selectTaglistValues(['Value 2', 'Value']);
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Tag list Task');

    await expect(
      formJSDetailsPage.form.getByText('Value', {exact: true}),
    ).toBeVisible();
    await expect(formJSDetailsPage.form.getByText('Value 2')).toBeVisible();
  });

  // TODO issue #3719
  test.skip('task completion with text template form', async ({
    taskPanelPage,
    taskDetailsPage,
    formJSDetailsPage,
    page,
  }) => {
    test.slow();
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Text_Templating_Form_Task');
    await taskDetailsPage.assignToMeButton.click();

    await expect(formJSDetailsPage.form).toContainText('Hello Jane');
    await expect(formJSDetailsPage.form).toContainText('You are 50 years old');
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Text_Templating_Form_Task');

    await expect(formJSDetailsPage.form).toContainText('Hello Jane');
    await expect(formJSDetailsPage.form).toContainText('You are 50 years old');
  });

  test('show process model', async ({taskPanelPage, taskDetailsPage}) => {
    await taskPanelPage.openTask('User registration');

    await expect(taskDetailsPage.detailsNav).toBeVisible();
    await taskDetailsPage.detailsNav.getByText(/process/i).click();

    await expect(taskDetailsPage.bpmnDiagram).toBeVisible();
  });
});
