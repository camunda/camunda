/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {createInstances, deploy} from 'utils/zeebeClient';
import {completeTaskWithRetry, navigateToApp} from '@pages/UtilitiesPage';
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
    './resources/Zeebe_Priority_User_Task_Process.bpmn',
  ]);
  await sleep(500);
  await createInstances('Job_Worker_Process', 1, 1);
  await createInstances('Zeebe_User_Task_Process', 1, 1);
  await createInstances('Variable_Process', 1, 1, {
    testVariable: 'testValue',
  });
  await createInstances('Form_User_Task', 1, 1);
  await createInstances('Start_Form_Process', 1, 1);
  await createInstances('Zeebe_Priority_User_Task_Process', 1, 1);
});

test.describe('HTO User Flow Tests', () => {
  test.beforeEach(async ({page, loginPage, taskPanelPage}) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login('demo', 'demo');
    await expect(taskPanelPage.taskListPageBanner).toBeVisible();
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
    loginPage,
  }) => {
    await test.step('View Process Instance in Operate, complete User Task in Tasklist & assert process complete in Operate', async () => {
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.operateBanner).toBeVisible();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.filterByProcessName('Job_Worker_Process');
      await operateProcessesPage.clickProcessInstanceLink();
      await operateProcessInstancePage.activeIconAssertion();

      await navigateToApp(page, 'tasklist');
      await loginPage.login('demo', 'demo');
      await completeTaskWithRetry(
        taskPanelPage,
        taskDetailsPage,
        'Job_Worker_Process',
        'medium',
      );

      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');

      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.filterByProcessName('Job_Worker_Process');
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await sleep(10000);
      await operateProcessesPage.clickProcessInstanceLink();
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
    loginPage,
  }) => {
    await test.step('View Process Instance in Operate, Edit the Variable & Assert the Variable is Updated in Tasklist', async () => {
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.filterByProcessName('Variable_Process');
      await operateProcessesPage.clickProcessInstanceLink();

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
      await loginPage.login('demo', 'demo');

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
      await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();
    });
  });

  test('Form.js Integration with User Task', async ({
    taskDetailsPage,
    taskPanelPage,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    page,
    loginPage,
  }) => {
    await test.step('View Process Instance in Operate and complete User Task in Tasklist', async () => {
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.filterByProcessName('Form_User_Task');
      await operateProcessesPage.clickProcessInstanceLink();

      await operateProcessInstancePage.activeIconAssertion();

      await navigateToApp(page, 'tasklist');
      await loginPage.login('demo', 'demo');
      await taskPanelPage.openTask('Form_User_Task');
      await taskDetailsPage.clickAssignToMeButton();
      await taskDetailsPage.fillTextInput('Name*', 'Test User');
      await taskDetailsPage.clickCompleteTaskButton();
      await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessActiveCheckbox();
      await sleep(1000);
      await operateProcessesPage.filterByProcessName('Form_User_Task');
      await operateProcessesPage.clickProcessInstanceLink();
      await operateProcessInstancePage.completedIconAssertion();
    });
  });

  test('Form.js Integration with Start form', async ({
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    loginPage,
    page,
  }) => {
    await test.step('View Process Instance in Operate and check if process is complete', async () => {
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await sleep(1000);
      await operateProcessesPage.filterByProcessName('Start_Form_Process');
      await operateProcessesPage.clickProcessInstanceLink();
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
    loginPage,
  }) => {
    await test.step('Complete User Task in Tasklist & assert process complete in Operate', async () => {
      await navigateToApp(page, 'tasklist');
      await loginPage.login('demo', 'demo');
      await expect(
        page.getByText('Zeebe_Priority_User_Task_Process').first(),
      ).toBeVisible({timeout: 60000});
      await completeTaskWithRetry(
        taskPanelPage,
        taskDetailsPage,
        'priorityTest4',
        'critical',
      );
      await completeTaskWithRetry(
        taskPanelPage,
        taskDetailsPage,
        'priorityTest3',
        'high',
      );
      await completeTaskWithRetry(
        taskPanelPage,
        taskDetailsPage,
        'priorityTest2',
        'medium',
      );
      await completeTaskWithRetry(
        taskPanelPage,
        taskDetailsPage,
        'priorityTest1',
        'low',
      );
      await taskPanelPage.filterBy('Completed');
      await taskPanelPage.assertCompletedHeadingVisible();
      await taskPanelPage.openTask('priorityTest4');
      await expect(
        page.getByText('Zeebe_Priority_User_Task_Process').first(),
      ).toBeVisible({timeout: 60000});
      await taskDetailsPage.taskAssertion('Zeebe_Priority_User_Task_Process');
      await taskDetailsPage.priorityAssertion('critical');
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await sleep(1000);
      await operateProcessesPage.filterByProcessName(
        'Zeebe_Priority_User_Task_Process',
      );
      await operateProcessesPage.clickProcessInstanceLink();
      await operateProcessInstancePage.completedIconAssertion();
    });
  });

  test('Zeebe User Task User Flow', async ({
    page,
    loginPage,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    taskDetailsPage,
    taskPanelPage,
  }) => {
    await test.step('View Process Instance in Operate, complete User Task in Tasklist', async () => {
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.filterByProcessName('Zeebe_User_Task_Process');
      await operateProcessesPage.clickProcessInstanceLink();

      await operateProcessInstancePage.activeIconAssertion();

      await navigateToApp(page, 'tasklist');
      await loginPage.login('demo', 'demo');
      await completeTaskWithRetry(
        taskPanelPage,
        taskDetailsPage,
        'Zeebe_User_Task_Process',
        'medium',
      );

      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await sleep(1000);
      await operateProcessesPage.filterByProcessName('Zeebe_User_Task_Process');
      await operateProcessesPage.clickProcessInstanceLink();
      await operateProcessInstancePage.completedIconAssertion();
    });
  });
});
