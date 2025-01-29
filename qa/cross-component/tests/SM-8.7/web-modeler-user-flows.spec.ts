import {expect} from '@playwright/test';
import {test} from '@fixtures/SM-8.7';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.describe('Web Modeler User Flow Tests', () => {
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

  test('Form.js Integration with User Task', async ({
    taskDetailsPage,
    taskPanelPage,
    modelerHomePage,
    navigationPage,
    modelerCreatePage,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    page,
  }) => {
    test.slow();
    await test.step('Create A New Web Modeler Project', async () => {
      await expect(modelerHomePage.createNewProjectButton).toBeVisible({
        timeout: 180000,
      });
      await modelerHomePage.clickCreateNewProjectButton();
      await expect(modelerHomePage.projectNameInput).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.enterNewProjectName('Web Modeler Project');
    });

    await test.step('Create a simple Form', async () => {
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickFormOption();
    });

    await test.step('Add A BPMN Template To The Project', async () => {
      await modelerHomePage.clickProjectBreadcrumb();
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with a User Task with an embedded Form and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName('User_Task_Process_With_Form');
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(20000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.assertImplementationOption('zeebeUserTask');
      await modelerCreatePage.clickEmbedFormButton();
      await modelerCreatePage.clickNewForm();
      await modelerCreatePage.clickEmbedButton();
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(20000);
      await modelerCreatePage.clickStartInstanceMainButton();
      await sleep(20000);
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate and complete User Task in Tasklist', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(90000);
      await navigationPage.goToOperate();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink(
        'User_Task_Process_With_Form',
      );

      await operateProcessInstancePage.activeIconAssertion();

      await navigationPage.goToTasklist();
      await sleep(30000);
      await page.reload();
      await taskPanelPage.openTask('User_Task_Process_With_Form');
      await taskDetailsPage.clickAssignToMeButton();
      await taskDetailsPage.clickCompleteTaskButton();
      await expect(page.getByText('Task completed')).toBeVisible({
        timeout: 200000,
      });
    });
  });

  test('Form.js Integration with Start form', async ({
    modelerHomePage,
    modelerCreatePage,
    navigationPage,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
  }) => {
    test.slow();
    await test.step('Navigate to Web Modeler project', async () => {
      await modelerHomePage.clickWebModelerProjectFolder();
    });

    await test.step('Add A BPMN Template To The Project', async () => {
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with a start event with an linked Form and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName('Start_Form_Process');
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(20000);
      await modelerCreatePage.hoverOnStartEvent();
      await modelerCreatePage.clickEmbedFormButton();
      await modelerCreatePage.clickNewForm();
      await modelerCreatePage.clickEmbedButton();
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(20000);
      await modelerCreatePage.clickStartInstanceMainButton();
      await sleep(20000);
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate and check if process is complete', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await navigationPage.goToOperate();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink('Start_Form_Process');
      await operateProcessInstancePage.completedIconAssertion();
    });
  });
});
