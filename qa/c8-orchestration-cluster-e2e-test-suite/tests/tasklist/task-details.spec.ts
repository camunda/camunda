/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {waitForAssertion} from 'utils/waitForAssertion';
import {test} from 'fixtures';
import {deploy, createInstances} from 'utils/zeebeClient';
import {navigateToApp} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.beforeAll(async () => {
  await deploy([
    './resources/usertask_to_be_completed.bpmn',
    './resources/user_task_with_form.bpmn',
    './resources/user_task_form.form',
    './resources/user_task_with_form_and_vars.bpmn',
    './resources/user_task_with_form_rerender_1.bpmn',
    './resources/user_task_with_form_rerender_2.bpmn',
    './resources/checkbox_task_with_form.bpmn',
    './resources/form_with_checkbox.form',
    './resources/checklist_task_with_form.bpmn',
    './resources/form_with_checklist.form',
    './resources/date_and_time_task_with_form.bpmn',
    './resources/form_with_date_and_time.form',
    './resources/number_task_with_form.bpmn',
    './resources/form_with_number.form',
    './resources/radio_button_task_with_form.bpmn',
    './resources/form_with_radio_button.form',
    './resources/select_task_with_form.bpmn',
    './resources/form_with_select.form',
    './resources/tag_list_task_with_form.bpmn',
    './resources/form_with_tag_list.form',
    './resources/text_templating_form_task.bpmn',
    './resources/form_with_text_templating.form',
    './resources/processWithDeployedForm.bpmn',
    './resources/create_invoice.form',
    './resources/zeebe_and_job_worker_process.bpmn',
    './resources/bigVariableProcessWithForm.bpmn',
    './resources/bigVariableForm.form',
  ]);
  await sleep(1000);

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
    createInstances('zeebe_and_job_worker_process', 1, 1),
    createInstances('bigVariableProcessWithForm', 1, 1),
  ]);

  await sleep(1000);
});

test.describe('task details page', () => {
  test.beforeEach(async ({page, taskListLoginPage}) => {
    await navigateToApp(page, 'tasklist');
    await taskListLoginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('load task details when a task is selected', async ({
    taskPanelPage,
    taskDetailsPage,
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
        /^\d{2}\s\w{3}\s\d{4}\s-\s\d{1,2}:\d{2}\s(AM|PM)$/,
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
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.openTask('usertask_to_be_completed');

    await expect(taskDetailsPage.assignToMeButton).toBeVisible({
      timeout: 60000,
    });
    await expect(taskDetailsPage.completeTaskButton).toBeDisabled();
    await taskDetailsPage.clickAssignToMeButton();

    await expect(taskDetailsPage.unassignButton).toBeVisible({timeout: 60000});
    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPage.assignee).toHaveText('Assigned to me', {
      useInnerText: true,
    });

    await taskDetailsPage.unassignButton.click();
    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await expect(taskDetailsPage.completeTaskButton).toBeDisabled();
    await expect(taskDetailsPage.assignee).toHaveText('Unassigned', {
      useInnerText: true,
    });

    await page.reload();

    await expect(taskDetailsPage.completeTaskButton).toBeDisabled({
      timeout: 60000,
    });
  });

  test('complete task', async ({page, taskPanelPage, taskDetailsPage}) => {
    await taskPanelPage.openTask('usertask_to_be_completed');

    const taskUrl = page.url();
    await taskDetailsPage.clickAssignToMeButton();
    await taskDetailsPage.completeTaskButton.click();
    await expect(taskDetailsPage.pickATaskHeader).toBeVisible();

    await page.goto(taskUrl);

    await expect(taskDetailsPage.assignToMeButton).toBeHidden();
    await expect(taskDetailsPage.unassignButton).toBeHidden();
    await expect(taskDetailsPage.completeTaskButton).toBeHidden();
  });

  test('complete zeebe and job worker tasks', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    test.slow();
    await taskPanelPage.openTask('Zeebe_user_task');
    await taskDetailsPage.clickUnassignButton();
    await taskDetailsPage.clickAssignToMeButton();
    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();
    await taskDetailsPage.addVariable({
      name: 'zeebeVar',
      value: '{"Name":"John","Age":20}',
    });
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();
    await taskPanelPage.openTask('JobWorker_user_task');
    await expect(taskDetailsPage.completeTaskButton).toBeDisabled({
      timeout: 120000,
    });
    await taskDetailsPage.clickAssignToMeButton();
    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();
    await taskDetailsPage.addVariable({
      name: 'jobWorkerVar',
      value: '{"Name":"John","Age":22}',
    });
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('Zeebe_user_task');
    await expect(page.getByText('zeebeVar', {exact: true})).toBeVisible({
      timeout: 60000,
    });
    await expect(taskDetailsPage.assignToMeButton).toBeHidden();
    await expect(taskDetailsPage.unassignButton).toBeHidden();
    await expect(taskDetailsPage.completeTaskButton).toBeHidden();

    await taskPanelPage.openTask('JobWorker_user_task');

    await waitForAssertion({
      assertion: async () => {
        await expect(page.getByText('jobWorkerVar')).toBeVisible();
        await expect(page.getByText('zeebeVar')).toBeVisible({timeout: 60000});
      },
      onFailure: async () => {
        await page.reload();
      },
    });
    await expect(taskDetailsPage.assignToMeButton).toBeHidden();
    await expect(taskDetailsPage.unassignButton).toBeHidden();
    await expect(taskDetailsPage.completeTaskButton).toBeHidden();
  });

  test('task completion with form', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.openTask('User registration');

    await expect(taskDetailsPage.nameInput).toBeVisible();
    await taskDetailsPage.clickAssignToMeButton();
    await expect(taskDetailsPage.unassignButton).toBeVisible();

    await taskDetailsPage.fillTextInput('Name*', 'Jon');
    await taskDetailsPage.fillTextInput('Address*', 'Earth');
    await taskDetailsPage.fillTextInput('Age', '21');
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('User registration');

    await taskDetailsPage.assertFieldValue('Name*', 'Jon');
    await taskDetailsPage.assertFieldValue('Address*', 'Earth');
    await taskDetailsPage.assertFieldValue('Age', '21');
  });

  test('task completion with deployed form', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.openTask('processWithDeployedForm');

    await taskDetailsPage.clickAssignToMeButton();
    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await taskDetailsPage.fillTextInput('Client Name*', 'Jon');
    await taskDetailsPage.fillTextInput('Client Address*', 'Earth');
    await taskDetailsPage.fillDatetimeField('Invoice Date', '1/1/3000');
    await taskDetailsPage.fillDatetimeField('Due Date', '1/2/3000');
    await taskDetailsPage.fillTextInput('Invoice Number*', '123');

    await taskDetailsPage.selectDropdownOption(
      'USD - United States Dollar',
      'EUR - Euro',
    );

    await taskDetailsPage.addDynamicListRow();
    await taskDetailsPage.fillDynamicList('Item Name*', 'Laptop');
    await taskDetailsPage.fillDynamicList('Unit Price*', '1');
    await taskDetailsPage.fillDynamicList('Quantity*', '2');

    await expect(taskDetailsPage.form).toContainText('EUR 231');
    await expect(taskDetailsPage.form).toContainText('EUR 264');
    await expect(taskDetailsPage.form).toContainText('Total: EUR 544.5');

    await taskDetailsPage.clickCompleteTaskButton();
    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await expect(async () => {
      await expect(
        taskPanelPage.availableTasks

          .getByText('processWithDeployedForm')

          .first(),
      ).toBeVisible();
    }).toPass();
    await taskPanelPage.openTask('processWithDeployedForm');

    await taskDetailsPage.assertFieldValue('Client Name*', 'Jon');
    await taskDetailsPage.assertFieldValue('Client Address*', 'Earth');
    await taskDetailsPage.assertFieldValue('Invoice Date*', '1/1/3000');
    await taskDetailsPage.assertFieldValue('Due Date*', '1/2/3000');
    await taskDetailsPage.assertFieldValue('Invoice Number*', '123');

    expect(await taskDetailsPage.getDynamicListValues('Item Name*')).toEqual([
      'Laptop1',
      'Laptop2',
    ]);

    expect(await taskDetailsPage.getDynamicListValues('Unit Price*')).toEqual([
      '11',
      '12',
    ]);

    expect(await taskDetailsPage.getDynamicListValues('Quantity*')).toEqual([
      '21',
      '22',
    ]);

    await expect(taskDetailsPage.form).toContainText('EUR 231');
    await expect(taskDetailsPage.form).toContainText('EUR 264');
    await expect(taskDetailsPage.form).toContainText('Total: EUR 544.5');
  });

  test('task completion with form from assigned to me filter', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.openTask('User registration');

    await expect(taskDetailsPage.nameInput).toBeVisible();
    await taskDetailsPage.clickAssignToMeButton();
    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();

    await taskPanelPage.filterBy('Assigned to me');
    await taskPanelPage.openTask('User registration');

    await expect(taskDetailsPage.nameInput).toBeVisible();
    await taskDetailsPage.fillTextInput('Name*', 'Gaius Julius Caesar');
    await taskDetailsPage.fillTextInput('Address*', 'Rome');
    await taskDetailsPage.fillTextInput('Age', '55');

    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('User registration');

    await taskDetailsPage.assertFieldValue('Name*', 'Gaius Julius Caesar');
    await taskDetailsPage.assertFieldValue('Address*', 'Rome');
    await taskDetailsPage.assertFieldValue('Age', '55');
  });

  test('task completion with prefilled form', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('User registration with vars');
    await taskDetailsPage.clickAssignToMeButton();

    await taskDetailsPage.assertFieldValue('Name*', 'Jane');
    await taskDetailsPage.assertFieldValue('Address*', '');
    await taskDetailsPage.assertFieldValue('Age', '50');

    await taskDetailsPage.fillTextInput('Name*', 'Jon');
    await taskDetailsPage.fillTextInput('Address*', 'Earth');
    await taskDetailsPage.fillTextInput('Age', '21');

    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('User registration with vars');

    await taskDetailsPage.assertFieldValue('Name*', 'Jon');
    await taskDetailsPage.assertFieldValue('Address*', 'Earth');
    await taskDetailsPage.assertFieldValue('Age', '21');
  });

  // eslint-disable-next-line playwright/expect-expect
  test('should rerender forms properly', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.openTask('User Task with form rerender 1');
    await taskDetailsPage.assertFieldValue('Name*', 'Mary');

    await taskPanelPage.openTask('User Task with form rerender 2');
    await taskDetailsPage.assertFieldValue('Name*', 'Stuart');
  });

  test('task completion with number form by input', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('UserTask_Number');
    await taskDetailsPage.clickAssignToMeButton();

    await taskDetailsPage.fillTextInput('Number', '4');
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('UserTask_Number');

    await taskDetailsPage.assertFieldValue('Number', '4');
  });

  test('task completion with number form by buttons', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('UserTask_Number');
    await taskDetailsPage.clickAssignToMeButton();

    await taskDetailsPage.clickIncrementButton();
    await taskDetailsPage.assertFieldValue('Number', '1');
    await taskDetailsPage.clickIncrementButton();
    await taskDetailsPage.assertFieldValue('Number', '2');
    await taskDetailsPage.clickDecrementButton();
    await taskDetailsPage.assertFieldValue('Number', '1');
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('UserTask_Number');
    await taskDetailsPage.assertFieldValue('Number', '1');
  });

  test('task completion with date and time form', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Date and Time Task');
    await taskDetailsPage.clickAssignToMeButton();
    await taskDetailsPage.fillDatetimeField('Date', '1/1/3000');
    await taskDetailsPage.fillDatetimeField('Time', '12:00 PM');
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();
    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('Date and Time Task');
    await taskDetailsPage.assertFieldValue('Date', '1/1/3000');
    await taskDetailsPage.assertFieldValue('Time', '12:00 PM');
  });

  test('task completion with checkbox form', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Checkbox Task');
    await taskDetailsPage.clickAssignToMeButton();

    await taskDetailsPage.checkCheckbox();
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('Checkbox Task');

    await expect(taskDetailsPage.checkbox).toBeChecked();
  });

  test('task completion with select form', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Select User Task');
    await taskDetailsPage.clickAssignToMeButton();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await taskDetailsPage.selectDropdownValue('Value');
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('Select User Task');

    await expect(taskDetailsPage.form).toContainText('Value');
  });

  test('task completion with radio button form', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Radio Button Task');
    await taskDetailsPage.clickAssignToMeButton();

    await taskDetailsPage.clickRadioButton('Value');
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('Radio Button Task');

    await taskDetailsPage.assertItemChecked('Value');
  });

  test('task completion with checklist form', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Checklist User Task');
    await taskDetailsPage.clickAssignToMeButton();
    await taskDetailsPage.checkChecklistBox('Value1');
    await taskDetailsPage.checkChecklistBox('Value2');
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('Checklist User Task');

    await taskDetailsPage.assertItemChecked('Value1');
    await taskDetailsPage.assertItemChecked('Value2');
  });

  // TODO issue #3719
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip('task completion with tag list form', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    test.slow();
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Tag list Task');
    await taskDetailsPage.clickAssignToMeButton();

    await taskDetailsPage.selectTaglistValues(['Value 2', 'Value']);
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.assertCompletedHeadingVisible();
    await taskPanelPage.openTask('Tag list Task');

    await expect(
      taskDetailsPage.form.getByText('Value', {exact: true}),
    ).toBeVisible();
    await expect(taskDetailsPage.form.getByText('Value 2')).toBeVisible();
  });

  // TODO issue #3719
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip('task completion with text template form', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    test.slow();
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Text_Templating_Form_Task');
    await taskDetailsPage.clickAssignToMeButton();

    await expect(taskDetailsPage.form).toContainText('Hello Jane');
    await expect(taskDetailsPage.form).toContainText('You are 50 years old');
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('Text_Templating_Form_Task');

    await expect(taskDetailsPage.form).toContainText('Hello Jane');
    await expect(taskDetailsPage.form).toContainText('You are 50 years old');
  });

  test('show process model', async ({taskPanelPage, taskDetailsPage}) => {
    await taskPanelPage.openTask('User registration');

    await expect(taskDetailsPage.processTab).toBeVisible();
    await taskDetailsPage.processTab.click();

    await expect(taskDetailsPage.bpmnDiagram).toBeVisible();
  });

  test('task completion with large variable form', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('Big Variable Usertask');

    await taskDetailsPage.clickAssignToMeButton();
    await taskDetailsPage.assertFieldValue(
      'Process input variable',
      'name:"Adeel Solangi"',
      {contains: true},
    );
    await taskDetailsPage.assertFieldValue(
      'Process input variable',
      'Maecenas quis nisi nunc.", version:2.69',
      {contains: true},
    );
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();
  });
});
