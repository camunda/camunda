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
import {completeTaskWithRetryV1, navigateToApp} from '@pages/v1/UtilitiesPage';
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
  test.beforeEach(async ({page, loginPage, taskPanelPageV1}) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login('demo', 'demo');
    await expect(taskPanelPageV1.taskListPageBanner).toBeVisible();
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
    taskDetailsPageV1,
    taskPanelPageV1,
    operateProcessInstancePage,
    operateFiltersPanelPage,
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
      await completeTaskWithRetryV1(
        taskPanelPageV1,
        taskDetailsPageV1,
        'Job_Worker_Process',
        'medium',
      );

      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');

      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.filterByProcessName('Job_Worker_Process');
      await operateFiltersPanelPage.clickCompletedInstancesCheckbox();
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
    operateFiltersPanelPage,
    taskPanelPageV1,
    taskDetailsPageV1,
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

      await taskPanelPageV1.openTask('Variable_Process');
      await taskDetailsPageV1.clickAssignToMeButton();
      await expect(page.getByText('Assigning...')).not.toBeVisible({
        timeout: 90000,
      });
      await expect(page.getByText('testVariable', {exact: true})).toBeVisible();
      await taskDetailsPageV1.assertVariableValue(
        'testVariable',
        '"updatedValue"',
      );
      await taskDetailsPageV1.clickCompleteTaskButton();
      await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();
    });
  });

  test('Form.js Integration with User Task', async ({
    taskDetailsPageV1,
    taskPanelPageV1,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    operateFiltersPanelPage,
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
      await taskPanelPageV1.openTask('Form_User_Task');
      await taskDetailsPageV1.clickAssignToMeButton();
      await taskDetailsPageV1.fillTextInput('Name*', 'Test User');
      await taskDetailsPageV1.clickCompleteTaskButton();
      await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.clickCompletedInstancesCheckbox();
      await operateFiltersPanelPage.clickActiveInstancesCheckbox();
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
    operateFiltersPanelPage,
    loginPage,
    page,
  }) => {
    await test.step('View Process Instance in Operate and check if process is complete', async () => {
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.clickCompletedInstancesCheckbox();
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
    operateFiltersPanelPage,
    taskDetailsPageV1,
    taskPanelPageV1,
    loginPage,
  }) => {
    await test.step('Complete User Task in Tasklist & assert process complete in Operate', async () => {
      await navigateToApp(page, 'tasklist');
      await loginPage.login('demo', 'demo');
      await expect(
        page.getByText('Zeebe_Priority_User_Task_Process').first(),
      ).toBeVisible({timeout: 60000});
      await completeTaskWithRetryV1(
        taskPanelPageV1,
        taskDetailsPageV1,
        'priorityTest4',
        'critical',
      );
      await completeTaskWithRetryV1(
        taskPanelPageV1,
        taskDetailsPageV1,
        'priorityTest3',
        'high',
      );
      await completeTaskWithRetryV1(
        taskPanelPageV1,
        taskDetailsPageV1,
        'priorityTest2',
        'medium',
      );
      await completeTaskWithRetryV1(
        taskPanelPageV1,
        taskDetailsPageV1,
        'priorityTest1',
        'low',
      );
      await taskPanelPageV1.filterBy('Completed');
      await taskPanelPageV1.assertCompletedHeadingVisible();
      await taskPanelPageV1.openTask('priorityTest4');
      await expect(
        page.getByText('Zeebe_Priority_User_Task_Process').first(),
      ).toBeVisible({timeout: 60000});
      await taskDetailsPageV1.taskAssertion('Zeebe_Priority_User_Task_Process');
      await taskDetailsPageV1.priorityAssertion('critical');
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.clickCompletedInstancesCheckbox();
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
    operateFiltersPanelPage,
    taskDetailsPageV1,
    taskPanelPageV1,
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
      await completeTaskWithRetryV1(
        taskPanelPageV1,
        taskDetailsPageV1,
        'Zeebe_User_Task_Process',
        'medium',
      );

      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.clickCompletedInstancesCheckbox();
      await sleep(1000);
      await operateProcessesPage.filterByProcessName('Zeebe_User_Task_Process');
      await operateProcessesPage.clickProcessInstanceLink();
      await operateProcessInstancePage.completedIconAssertion();
    });
  });
});
