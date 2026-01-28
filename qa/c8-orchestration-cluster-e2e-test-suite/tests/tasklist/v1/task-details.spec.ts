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
  test.beforeEach(async ({page, loginPage}) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('load task details when a task is selected', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.openTask('usertask_to_be_completed');

    await expect(taskDetailsPageV1.detailsHeader).toBeVisible();
    await expect(taskDetailsPageV1.detailsHeader).toContainText(
      'Some user activity',
    );
    await expect(taskDetailsPageV1.detailsHeader).toContainText(
      'usertask_to_be_completed',
    );
    await expect(taskDetailsPageV1.detailsHeader).toContainText('Unassigned');
    await expect(taskDetailsPageV1.assignToMeButton).toBeVisible();

    await expect(taskDetailsPageV1.detailsPanel).toBeVisible();
    await expect(taskDetailsPageV1.detailsPanel).toContainText('Creation date');
    await expect(
      taskDetailsPageV1.detailsPanel.getByText(
        /^\d{2}\s\w{3}\s\d{4}\s-\s\d{1,2}:\d{2}\s(AM|PM)$/,
      ),
    ).toBeVisible();
    await expect(taskDetailsPageV1.detailsPanel).toContainText('Candidates');
    await expect(taskDetailsPageV1.detailsPanel).toContainText('No candidates');
    await expect(taskDetailsPageV1.detailsPanel).toContainText('Due date');
    await expect(taskDetailsPageV1.detailsPanel).toContainText('No due date');
    await expect(taskDetailsPageV1.detailsPanel).not.toContainText(
      'Completion date',
    );
  });

  test('assign and unassign task', async ({
    page,
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.openTask('usertask_to_be_completed');

    await expect(taskDetailsPageV1.assignToMeButton).toBeVisible({
      timeout: 60000,
    });
    await expect(taskDetailsPageV1.completeTaskButton).toBeDisabled();
    await taskDetailsPageV1.clickAssignToMeButton();

    await expect(taskDetailsPageV1.unassignButton).toBeVisible({
      timeout: 60000,
    });
    await expect(taskDetailsPageV1.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPageV1.assignee).toHaveText('Assigned to me', {
      useInnerText: true,
    });

    await taskDetailsPageV1.unassignButton.click();
    await expect(taskDetailsPageV1.assignToMeButton).toBeVisible();
    await expect(taskDetailsPageV1.completeTaskButton).toBeDisabled();
    await expect(taskDetailsPageV1.assignee).toHaveText('Unassigned', {
      useInnerText: true,
    });

    await expect(taskDetailsPageV1.completeTaskButton).toBeDisabled({
      timeout: 60000,
    });
  });

  test('complete task', async ({page, taskPanelPageV1, taskDetailsPageV1}) => {
    await taskPanelPageV1.openTask('usertask_to_be_completed');

    const taskUrl = page.url();
    await taskDetailsPageV1.clickAssignToMeButton();
    await taskDetailsPageV1.completeTaskButton.click();
    await expect(taskDetailsPageV1.pickATaskHeader).toBeVisible();

    await page.goto(taskUrl);

    await expect(taskDetailsPageV1.assignToMeButton).toBeHidden();
    await expect(taskDetailsPageV1.unassignButton).toBeHidden();
    await expect(taskDetailsPageV1.completeTaskButton).toBeHidden();
  });

  test('complete zeebe and job worker tasks', async ({
    page,
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    test.slow();
    await taskPanelPageV1.openTask('Zeebe_user_task');
    await taskDetailsPageV1.clickUnassignButton();
    await taskDetailsPageV1.clickAssignToMeButton();
    await expect(taskDetailsPageV1.completeTaskButton).toBeEnabled();
    await taskDetailsPageV1.addVariable({
      name: 'zeebeVar',
      value: '{"Name":"John","Age":20}',
    });
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.openTask('JobWorker_user_task');
    await expect(taskDetailsPageV1.completeTaskButton).toBeDisabled({
      timeout: 60000,
    });
    await taskDetailsPageV1.clickAssignToMeButton();
    await expect(taskDetailsPageV1.completeTaskButton).toBeEnabled();
    await taskDetailsPageV1.addVariable({
      name: 'jobWorkerVar',
      value: '{"Name":"John","Age":22}',
    });
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('Zeebe_user_task');
    await waitForAssertion({
      assertion: async () => {
        await expect(page.getByRole('cell', {name: 'zeebeVar'})).toBeVisible({
          timeout: 60000,
        });
      },
      onFailure: async () => {
        await page.reload();
      },
    });
    await expect(taskDetailsPageV1.assignToMeButton).toBeHidden();
    await expect(taskDetailsPageV1.unassignButton).toBeHidden();
    await expect(taskDetailsPageV1.completeTaskButton).toBeHidden();

    await taskPanelPageV1.openTask('JobWorker_user_task');

    // this is necessary because sometimes the importer takes some time to receive the variables
    await waitForAssertion({
      assertion: async () => {
        await expect(
          page.getByRole('cell', {name: 'jobWorkerVar'}),
        ).toBeVisible();
        await expect(page.getByRole('cell', {name: 'zeebeVar'})).toBeVisible({
          timeout: 60000,
        });
      },
      onFailure: async () => {
        await page.reload();
      },
    });
    await expect(taskDetailsPageV1.assignToMeButton).toBeHidden();
    await expect(taskDetailsPageV1.unassignButton).toBeHidden();
    await expect(taskDetailsPageV1.completeTaskButton).toBeHidden();
  });

  test('task completion with form', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.openTask('User registration');

    await expect(taskDetailsPageV1.nameInput).toBeVisible();
    await taskDetailsPageV1.clickAssignToMeButton();
    await expect(taskDetailsPageV1.unassignButton).toBeVisible();

    await taskDetailsPageV1.fillTextInput('Name*', 'Jon');
    await taskDetailsPageV1.fillTextInput('Address*', 'Earth');
    await taskDetailsPageV1.fillTextInput('Age', '21');
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('User registration');

    await taskDetailsPageV1.assertFieldValue('Name*', 'Jon');
    await taskDetailsPageV1.assertFieldValue('Address*', 'Earth');
    await taskDetailsPageV1.assertFieldValue('Age', '21');
  });

  test('task completion with deployed form', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await expect(async () => {
      await expect(
        taskPanelPageV1.availableTasks
          .getByText('processWithDeployedForm')
          .first(),
      ).toBeVisible();
    }).toPass();
    await taskPanelPageV1.openTask('processWithDeployedForm');

    await taskDetailsPageV1.clickAssignToMeButton();
    await expect(taskDetailsPageV1.unassignButton).toBeVisible({
      timeout: 30000,
    });
    await taskDetailsPageV1.fillTextInput('Client Name*', 'Jon');
    await taskDetailsPageV1.fillTextInput('Client Address*', 'Earth');
    await taskDetailsPageV1.fillDatetimeField('Invoice Date', '1/1/3000');
    await taskDetailsPageV1.fillDatetimeField('Due Date', '1/2/3000');
    await taskDetailsPageV1.fillTextInput('Invoice Number*', '123');

    await taskDetailsPageV1.selectDropdownOption(
      'USD - United States Dollar',
      'EUR - Euro',
    );

    await taskDetailsPageV1.addDynamicListRow();
    await taskDetailsPageV1.fillDynamicList('Item Name*', 'Laptop');
    await taskDetailsPageV1.fillDynamicList('Unit Price*', '1');
    await taskDetailsPageV1.fillDynamicList('Quantity*', '2');

    await expect(taskDetailsPageV1.form).toContainText('EUR 231');
    await expect(taskDetailsPageV1.form).toContainText('EUR 264');
    await expect(taskDetailsPageV1.form).toContainText('Total: EUR 544.5');

    await taskDetailsPageV1.completeTaskButton.click();

    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible({
      timeout: 60000,
    });
    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await expect(async () => {
      await expect(
        taskPanelPageV1.availableTasks
          .getByText('processWithDeployedForm')
          .first(),
      ).toBeVisible();
    }).toPass();
    await taskPanelPageV1.openTask('processWithDeployedForm');

    await taskDetailsPageV1.assertFieldValue('Client Name*', 'Jon');
    await taskDetailsPageV1.assertFieldValue('Client Address*', 'Earth');
    await taskDetailsPageV1.assertFieldValue('Invoice Date*', '1/1/3000');
    await taskDetailsPageV1.assertFieldValue('Due Date*', '1/2/3000');
    await taskDetailsPageV1.assertFieldValue('Invoice Number*', '123');

    expect(await taskDetailsPageV1.getDynamicListValues('Item Name*')).toEqual([
      'Laptop1',
      'Laptop2',
    ]);

    expect(await taskDetailsPageV1.getDynamicListValues('Unit Price*')).toEqual(
      ['11', '12'],
    );

    expect(await taskDetailsPageV1.getDynamicListValues('Quantity*')).toEqual([
      '21',
      '22',
    ]);

    await expect(taskDetailsPageV1.form).toContainText('EUR 231');
    await expect(taskDetailsPageV1.form).toContainText('EUR 264');
    await expect(taskDetailsPageV1.form).toContainText('Total: EUR 544.5');
  });

  test('task completion with form from assigned to me filter', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.openTask('User registration');

    await expect(taskDetailsPageV1.nameInput).toBeVisible();
    await taskDetailsPageV1.clickAssignToMeButton();
    await expect(taskDetailsPageV1.completeTaskButton).toBeEnabled();

    await taskPanelPageV1.filterBy('Assigned to me');
    await taskPanelPageV1.openTask('User registration');

    await expect(taskDetailsPageV1.nameInput).toBeVisible();
    await taskDetailsPageV1.fillTextInput('Name*', 'Gaius Julius Caesar');
    await taskDetailsPageV1.fillTextInput('Address*', 'Rome');
    await taskDetailsPageV1.fillTextInput('Age', '55');

    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('User registration');

    await taskDetailsPageV1.assertFieldValue('Name*', 'Gaius Julius Caesar');
    await taskDetailsPageV1.assertFieldValue('Address*', 'Rome');
    await taskDetailsPageV1.assertFieldValue('Age', '55');
  });

  test('task completion with prefilled form', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('User registration with vars');
    await taskDetailsPageV1.clickAssignToMeButton();

    await taskDetailsPageV1.assertFieldValue('Name*', 'Jane');
    await taskDetailsPageV1.assertFieldValue('Address*', '');
    await taskDetailsPageV1.assertFieldValue('Age', '50');

    await taskDetailsPageV1.fillTextInput('Name*', 'Jon');
    await taskDetailsPageV1.fillTextInput('Address*', 'Earth');
    await taskDetailsPageV1.fillTextInput('Age', '21');

    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('User registration with vars');

    await taskDetailsPageV1.assertFieldValue('Name*', 'Jon');
    await taskDetailsPageV1.assertFieldValue('Address*', 'Earth');
    await taskDetailsPageV1.assertFieldValue('Age', '21');
  });

  // eslint-disable-next-line playwright/expect-expect
  test('should rerender forms properly', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.openTask('User Task with form rerender 1');
    await taskDetailsPageV1.assertFieldValue('Name*', 'Mary');

    await taskPanelPageV1.openTask('User Task with form rerender 2');
    await taskDetailsPageV1.assertFieldValue('Name*', 'Stuart');
  });

  test('task completion with number form by input', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('UserTask_Number');
    await taskDetailsPageV1.clickAssignToMeButton();

    await taskDetailsPageV1.fillTextInput('Number', '4');
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('UserTask_Number');

    await taskDetailsPageV1.assertFieldValue('Number', '4');
  });

  test('task completion with number form by buttons', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('UserTask_Number');
    await taskDetailsPageV1.clickAssignToMeButton();

    await taskDetailsPageV1.clickIncrementButton();
    await taskDetailsPageV1.assertFieldValue('Number', '1');
    await taskDetailsPageV1.clickIncrementButton();
    await taskDetailsPageV1.assertFieldValue('Number', '2');
    await taskDetailsPageV1.clickDecrementButton();
    await taskDetailsPageV1.assertFieldValue('Number', '1');
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('UserTask_Number');
    await taskDetailsPageV1.assertFieldValue('Number', '1');
  });

  test('task completion with date and time form', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('Date and Time Task');
    await taskDetailsPageV1.clickAssignToMeButton();
    await taskDetailsPageV1.fillDatetimeField('Date', '1/1/3000');
    await taskDetailsPageV1.fillDatetimeField('Time', '12:00 PM');
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible({
      timeout: 30000,
    });
    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('Date and Time Task');
    await taskDetailsPageV1.assertFieldValue('Date', '1/1/3000');
    await taskDetailsPageV1.assertFieldValue('Time', '12:00 PM');
  });

  test('task completion with checkbox form', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('Checkbox Task');
    await taskDetailsPageV1.clickAssignToMeButton();

    await taskDetailsPageV1.checkCheckbox();
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('Checkbox Task');

    await expect(taskDetailsPageV1.checkbox).toBeChecked({timeout: 60000});
  });

  test('task completion with select form', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('Select User Task');
    await taskDetailsPageV1.clickAssignToMeButton();

    await expect(taskDetailsPageV1.unassignButton).toBeVisible();
    await taskDetailsPageV1.selectDropdownValue('Value');
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('Select User Task');

    await expect(taskDetailsPageV1.form).toContainText('Value', {
      timeout: 30000,
    });
  });

  test('task completion with radio button form', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('Radio Button Task');
    await taskDetailsPageV1.clickAssignToMeButton();

    await taskDetailsPageV1.clickRadioButton('Value');
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('Radio Button Task');

    await taskDetailsPageV1.assertItemChecked('Value');
  });

  test('task completion with checklist form', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('Checklist User Task');
    await taskDetailsPageV1.clickAssignToMeButton();
    await taskDetailsPageV1.checkChecklistBox('Value1');
    await taskDetailsPageV1.checkChecklistBox('Value2');
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('Checklist User Task');

    await taskDetailsPageV1.assertItemChecked('Value1');
    await taskDetailsPageV1.assertItemChecked('Value2');
  });

  // TODO issue #3719
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip('task completion with tag list form', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    test.slow();
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('Tag list Task');
    await taskDetailsPageV1.clickAssignToMeButton();

    await taskDetailsPageV1.selectTaglistValues(['Value 2', 'Value']);
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('Tag list Task');

    await expect(
      taskDetailsPageV1.form.getByText('Value', {exact: true}),
    ).toBeVisible();
    await expect(taskDetailsPageV1.form.getByText('Value 2')).toBeVisible();
  });

  // TODO issue #3719
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip('task completion with text template form', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    test.slow();
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('Text_Templating_Form_Task');
    await taskDetailsPageV1.clickAssignToMeButton();

    await expect(taskDetailsPageV1.form).toContainText('Hello Jane');
    await expect(taskDetailsPageV1.form).toContainText('You are 50 years old');
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.openTask('Text_Templating_Form_Task');

    await expect(taskDetailsPageV1.form).toContainText('Hello Jane');
    await expect(taskDetailsPageV1.form).toContainText('You are 50 years old');
  });

  test('show process model', async ({taskPanelPageV1, taskDetailsPageV1}) => {
    await taskPanelPageV1.openTask('User registration');

    await expect(taskDetailsPageV1.processTab).toBeVisible();
    await taskDetailsPageV1.processTab.click();

    await expect(taskDetailsPageV1.bpmnDiagram).toBeVisible();
  });

  test('task completion with large variable form', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
    page,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('Big Variable Usertask');
    await taskDetailsPageV1.clickAssignToMeButton();

    await taskDetailsPageV1.assertFieldValue(
      'Process input variable',
      'name:"Adeel Solangi"',
      {partial: true},
    );
    await taskDetailsPageV1.assertFieldValue(
      'Process input variable',
      'Maecenas quis nisi nunc.", version:2.69',
      {partial: true},
    );
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.assertCompletedHeadingVisible();
    await taskPanelPageV1.openTask('Big Variable Usertask');

    await taskDetailsPageV1.assertFieldValue(
      'Process input variable',
      'name:"Adeel Solangi"',
      {partial: true},
    );
    await taskDetailsPageV1.assertFieldValue(
      'Process input variable',
      'Maecenas quis nisi nunc.", version:2.69',
      {partial: true},
    );
  });
});
