/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@fixtures/8.6';
import {createInstances, deploy} from 'utils/zeebeClient';
import {navigateToApp} from '@pages/8.6/UtilitiesPage';
import {sleep} from 'utils/sleep';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.beforeAll(async () => {
  await deploy([
    './resources/Job_Worker_Process.bpmn',
    './resources/Variable_Process.bpmn',
    './resources/Zeebe_User_Task_Process.bpmn',
    './resources/New Form.form',
    './resources/User_Task_Process_With_Form.bpmn',
    './resources/Start_Form_Process.bpmn',
    './resources/Zeebe_User_Task_Process_With_Priority.bpmn',
  ]);
  await createInstances('Job_Worker_Process', 1, 1);
  await createInstances('Zeebe_User_Task_Process', 1, 1);
  await createInstances('Variable_Process', 1, 1, {
    testVariable: 'testValue',
  });
  await createInstances('Form_User_Task', 1, 1);
  await createInstances('Start_Form_Process', 1, 1);
  await createInstances('Zeebe_User_Task_Process_With_Priority', 1, 1);
});

test.describe('HTO User Flow Tests', () => {
  test.beforeEach(async ({page, taskListLoginPage, taskPanelPage}) => {
    await navigateToApp(page, 'tasklist');
    await taskListLoginPage.login('demo', 'demo');
    await taskPanelPage.taskListBannerIsVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test.use({
    httpCredentials: {
      username: 'demo',
      password: 'demo',
    },
  });

  test('User Task Most Common Flow', async ({
    operateHomePage,
    operateProcessesPage,
    taskDetailsPage,
    taskPanelPage,
    operateProcessInstancePage,
    page,
    operateLoginPage,
    taskListLoginPage,
  }) => {
    await test.step('View Process Instance in Operate, complete User Task in Tasklist & assert process complete in Operate', async () => {
      await navigateToApp(page, 'operate');
      await operateLoginPage.login('demo', 'demo');
      await operateHomePage.operateBannerIsVisible();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink('Job_Worker_Process');
      await operateProcessInstancePage.activeIconAssertion();

      await navigateToApp(page, 'tasklist');
      await taskListLoginPage.login('demo', 'demo');
      await taskPanelPage.openTask('Job_Worker_Process');
      await taskDetailsPage.clickAssignToMeButton();
      await taskDetailsPage.clickCompleteTaskButton();
      await expect(page.getByText('Task completed')).toBeVisible({
        timeout: 200000,
      });

      await navigateToApp(page, 'operate');
      await operateLoginPage.login('demo', 'demo');

      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await sleep(10000);
      await operateProcessesPage.clickProcessInstanceLink('Job_Worker_Process');
      await operateProcessInstancePage.completedIconAssertion();
    });
  });

  test('User Task Editing Variables Flow', async ({
    page,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    taskPanelPage,
    taskDetailsPage,
    operateLoginPage,
    taskListLoginPage,
  }) => {
    await test.step('View Process Instance in Operate, Edit the Variable & Assert the Variable is Updated in Tasklist', async () => {
      await navigateToApp(page, 'operate');
      await operateLoginPage.login('demo', 'demo');
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink('Variable_Process');

      await operateProcessInstancePage.activeIconAssertion();
      await expect(page.getByText('"testValue"')).toBeVisible({
        timeout: 120000,
      });
      await expect(page.getByText('testVariable', {exact: true})).toBeVisible();
      await operateHomePage.clickEditVariableButton('testVariable');
      await operateHomePage.clickVariableValueInput();
      await operateHomePage.clearVariableValueInput();
      await operateHomePage.fillVariableValueInput('"updatedValue"');
      await expect(operateHomePage.saveVariableButton).toBeVisible({
        timeout: 30000,
      });
      await operateHomePage.clickSaveVariableButton();

      await expect(operateHomePage.editVariableSpinner).not.toBeVisible({
        timeout: 120000,
      });

      await expect(page.getByText('"testValue"')).not.toBeVisible({
        timeout: 90000,
      });
      await expect(page.getByText('"updatedValue"')).toBeVisible();
      await sleep(10000);

      await navigateToApp(page, 'tasklist');
      await taskListLoginPage.login('demo', 'demo');

      await taskPanelPage.openTask('Variable_Process');
      await taskDetailsPage.clickAssignToMeButton();
      await expect(page.getByText('Assigning...')).not.toBeVisible({
        timeout: 90000,
      });
      await expect(page.getByText('testVariable', {exact: true})).toBeVisible();
      await taskDetailsPage.assertVariableValue(
        'testVariable',
        '"updatedValue"',
      );
      await taskDetailsPage.clickCompleteTaskButton();
      await expect(page.getByText('Task completed')).toBeVisible({
        timeout: 200000,
      });
    });
  });

  test('Form.js Integration with User Task', async ({
    taskDetailsPage,
    taskPanelPage,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    page,
    taskListLoginPage,
    operateLoginPage,
  }) => {
    await test.step('View Process Instance in Operate and complete User Task in Tasklist', async () => {
      await navigateToApp(page, 'operate');
      await operateLoginPage.login('demo', 'demo');
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink('Form_User_Task');

      await operateProcessInstancePage.activeIconAssertion();

      await navigateToApp(page, 'tasklist');
      await taskListLoginPage.login('demo', 'demo');
      await taskPanelPage.openTask('Form_User_Task');
      await taskDetailsPage.clickAssignToMeButton();
      await taskDetailsPage.clickTextInput();
      await taskDetailsPage.fillTextInput('Test User');
      await taskDetailsPage.clickCompleteTaskButton();
      await expect(page.getByText('Task completed')).toBeVisible({
        timeout: 200000,
      });

      await navigateToApp(page, 'operate');
      await operateLoginPage.login('demo', 'demo');
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await sleep(1000);
      await operateProcessesPage.clickProcessInstanceLink('Form_User_Task');
      await operateProcessInstancePage.completedIconAssertion();
    });
  });

  test('Form.js Integration with Start form', async ({
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    operateLoginPage,
    page,
  }) => {
    await test.step('View Process Instance in Operate and check if process is complete', async () => {
      await navigateToApp(page, 'operate');
      await operateLoginPage.login('demo', 'demo');
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await sleep(1000);
      await operateProcessesPage.clickProcessInstanceLink('Start_Form_Process');
      await operateProcessInstancePage.completedIconAssertion();
    });
  });

  test('Zeebe User Task With Priority', async ({
    page,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    taskDetailsPage,
    taskPanelPage,
    taskListLoginPage,
    operateLoginPage,
  }) => {
    await test.step('Complete User Task in Tasklist & assert process complete in Operate', async () => {
      await navigateToApp(page, 'tasklist');
      await taskListLoginPage.login('demo', 'demo');
      await expect(
        page.getByText('Zeebe_User_Task_Process_With_Priority').first(),
      ).toBeVisible({timeout: 60000});
      await taskPanelPage.openTask('priorityTest4');
      await taskDetailsPage.clickAssignToMeButton();
      await expect(
        taskDetailsPage.detailsPanel.getByText('critical'),
      ).toBeVisible();
      await taskDetailsPage.clickCompleteTaskButton();
      await taskPanelPage.openTask('priorityTest3');
      await taskDetailsPage.clickAssignToMeButton();
      await expect(
        taskDetailsPage.detailsPanel.getByText('high'),
      ).toBeVisible();
      await taskDetailsPage.taskCompletedBanner.waitFor({state: 'hidden'});
      await taskDetailsPage.clickCompleteTaskButton();
      await taskPanelPage.openTask('priorityTest2');
      await taskDetailsPage.clickAssignToMeButton();
      await expect(
        taskDetailsPage.detailsPanel.getByText('medium'),
      ).toBeVisible();
      await taskDetailsPage.taskCompletedBanner.waitFor({state: 'hidden'});
      await taskDetailsPage.clickCompleteTaskButton();
      await taskPanelPage.openTask('priorityTest1');
      await taskDetailsPage.clickAssignToMeButton();
      await expect(taskDetailsPage.detailsPanel.getByText('low')).toBeVisible();
      await taskDetailsPage.taskCompletedBanner.waitFor({state: 'hidden'});
      await taskDetailsPage.clickCompleteTaskButton();

      await taskPanelPage.filterBy('Completed');
      await expect(page.getByRole('heading', {name: 'completed'})).toBeVisible({
        timeout: 45000,
      });
      await page.reload();
      await taskPanelPage.openTask('priorityTest4');
      await expect(
        page.getByText('Zeebe_User_Task_Process_With_Priority').first(),
      ).toBeVisible({timeout: 60000});
      await taskDetailsPage.taskAssertion(
        'Zeebe_User_Task_Process_With_Priority',
      );
      await taskDetailsPage.priorityAssertion('critical');
      await navigateToApp(page, 'operate');
      await operateLoginPage.login('demo', 'demo');
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await sleep(1000);
      await operateProcessesPage.clickProcessInstanceLink(
        'Zeebe_User_Task_Process_With_Priority',
      );
      await operateProcessInstancePage.completedIconAssertion();
    });
  });

  test('Zeebe User Task User Flow', async ({
    page,
    operateLoginPage,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    taskListLoginPage,
    taskDetailsPage,
    taskPanelPage,
  }) => {
    await test.step('View Process Instance in Operate, complete User Task in Tasklist', async () => {
      await navigateToApp(page, 'operate');
      await operateLoginPage.login('demo', 'demo');
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink(
        'Zeebe_User_Task_Process',
      );

      await operateProcessInstancePage.activeIconAssertion();

      await navigateToApp(page, 'tasklist');
      await taskListLoginPage.login('demo', 'demo');

      await taskPanelPage.openTask('Zeebe_User_Task_Process');
      await taskDetailsPage.clickAssignToMeButton();
      await taskDetailsPage.clickCompleteTaskButton();
      await expect(page.getByText('Task completed')).toBeVisible({
        timeout: 200000,
      });

      await navigateToApp(page, 'operate');
      await operateLoginPage.login('demo', 'demo');
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await sleep(1000);
      await operateProcessesPage.clickProcessInstanceLink(
        'Zeebe_User_Task_Process',
      );
      await operateProcessInstancePage.completedIconAssertion();
    });
  });
});
