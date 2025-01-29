import {expect} from '@playwright/test';
import {test} from '@fixtures/8.6';
import {createInstances, deploy} from 'utils/zeebeClient';
import {navigateToApp} from '@pages/c8Run-8.6/UtilitiesPage';
import {sleep} from 'utils/sleep';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.beforeAll(async () => {
  await deploy('./resources/Job_Worker_Process.bpmn');
  await deploy('./resources/Variable_Process.bpmn');
  await deploy('./resources/Zeebe_User_Task.bpmn');
  await deploy('./resources/New Form.form');
  await deploy('./resources/User_Task_Process_With_Form.bpmn');
  await deploy('./resources/Start_Form_Process.bpmn');
  await deploy('./resources/Zeebe_User_Task_Process_With_Priority.bpmn');
  await createInstances('Job_Worker_Process', 1, 1);
  await createInstances('Zeebe_User_Task', 1, 1);
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

  test('User Task Most Common Flow', async ({
    operateLoginPage,
    operateHomePage,
    operateProcessesPage,
    taskDetailsPage,
    taskPanelPage,
    operateProcessInstancePage,
    taskListLoginPage,
    page,
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
      await operateProcessesPage.clickProcessActiveCheckbox();
      await page.reload();
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
    taskListLoginPage,
    operateLoginPage,
    taskDetailsPage,
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
    taskListLoginPage,
    operateLoginPage,
    page,
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
      await sleep(30000);
      await page.reload();
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
      await page.reload();
      await operateProcessesPage.clickProcessInstanceLink('Form_User_Task');
      await operateProcessInstancePage.completedIconAssertion();
    });
  });

  test('Form.js Integration with Start form', async ({
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    page,
    operateLoginPage,
  }) => {
    await test.step('View Process Instance in Operate and check if process is complete', async () => {
      await navigateToApp(page, 'operate');
      await operateLoginPage.login('demo', 'demo');
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink('Start_Form_Process');
      await operateProcessInstancePage.completedIconAssertion();
    });
  });
});
