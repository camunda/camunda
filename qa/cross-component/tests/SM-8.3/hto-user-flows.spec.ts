import {expect} from '@playwright/test';
import {test} from '@fixtures/SM-8.3';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.describe('HTO User Flow Tests', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/modeler');
    await loginPage.login(
      'demo',
      process.env.DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD!,
    );
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('User Task Most Common Flow', async ({
    modelerHomePage,
    navigationPage,
    modelerCreatePage,
    operateHomePage,
    operateProcessesPage,
    taskDetailsPage,
    taskPanelPage,
    operateProcessInstancePage,
    page,
  }) => {
    test.slow();
    await test.step('Create New Project with a BPMN Diagram Template', async () => {
      await expect(modelerHomePage.createNewProjectButton).toBeVisible({
        timeout: 180000,
      });
      await modelerHomePage.clickCreateNewProjectButton();
      await expect(modelerHomePage.projectNameInput).toBeVisible({
        timeout: 90000,
      });
      await modelerHomePage.enterNewProjectName('HTO Project');
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with User Task and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName('User_Task_Process');
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickProcessIdInput();
      await modelerCreatePage.fillProcessIdInput('User_Task_Process');
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(30000);
      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.clickAppendEndEventButton();
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await modelerCreatePage.clickStartInstanceSubButton();
      await modelerCreatePage.instanceStartedAssertion();
    });

    await test.step('View Process Instance in Operate, complete User Task in Tasklist & assert process complete in Operate', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await navigationPage.goToOperate();
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink('User_Task_Process');

      await operateProcessInstancePage.activeIconAssertion();

      await sleep(10000);
      await navigationPage.goToTasklist();

      await taskPanelPage.openTask('User_Task_Process');
      await taskDetailsPage.clickAssignToMeButton();
      await taskDetailsPage.clickCompleteTaskButton();
      await expect(page.getByText('Task completed')).toBeVisible({
        timeout: 200000,
      });

      await navigationPage.goToOperate();

      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink('User_Task_Process');

      await operateProcessInstancePage.completedIconAssertion();
    });
  });

  test('User Task Editing Variables Flow', async ({
    page,
    operateHomePage,
    modelerHomePage,
    navigationPage,
    modelerCreatePage,
    operateProcessesPage,
    operateProcessInstancePage,
    taskPanelPage,
  }) => {
    test.slow();
    await test.step('Navigate to HTO User Flow BPMN Diagram in HTO project', async () => {
      await expect(modelerHomePage.htoProjectFolder).toBeVisible({
        timeout: 180000,
      });
      await modelerHomePage.clickHTOProjectFolder();
      await expect(modelerHomePage.htoUserFlowDiagram).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickHTOUserFlowDiagram();
    });

    await test.step('Start Process Instance with variables', async () => {
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.clickVariableInput();
      await modelerCreatePage.fillVariableInput('{"testVariable":"testValue"}');
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(30000);
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await sleep(30000);
      await modelerCreatePage.clickStartInstanceSubButton();
      await modelerCreatePage.instanceStartedAssertion();
    });

    await test.step('View Process Instance in Operate, Edit the Variable & Assert the Variable is Updated in Tasklist', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await navigationPage.goToOperate();
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink('User_Task_Process');

      await operateProcessInstancePage.activeIconAssertion();
      await expect(page.getByText('"testValue"')).toBeVisible({
        timeout: 90000,
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

      await navigationPage.goToTasklist();

      await taskPanelPage.openTask('User_Task_Process');
      await expect(page.getByText('testVariable')).toBeVisible();
      await expect(page.getByText('"updatedValue"')).toBeVisible();
      await expect(page.getByText('"testValue"')).not.toBeVisible();
    });
  });
});
