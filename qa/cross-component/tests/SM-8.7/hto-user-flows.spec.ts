import {expect} from '@playwright/test';
import {test} from '@fixtures/SM-8.7';
import {
  captureScreenshot,
  captureFailureVideo,
  generateRandomStringAsync,
} from '@setup';

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

  test('Job Worker User Task Most Common Flow', async ({
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
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(20000);
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickIdInput();
      await modelerCreatePage.fillIdInput('User_Task_Process');
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.chooseImplementationOption('Job worker');
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.clickStartInstanceSubButton();
      await modelerCreatePage.instanceStartedAssertion();
    });

    await test.step('View Process Instance in Operate, complete User Task in Tasklist & assert process complete in Operate', async () => {
      await navigationPage.goToOperate();
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await page.reload();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
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

      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink('User_Task_Process');

      await operateProcessInstancePage.completedIconAssertion();
    });
  });

  test('Zeebe User Task User Flow', async ({
    page,
    modelerHomePage,
    navigationPage,
    modelerCreatePage,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    optimizeHomePage,
    optimizeCollectionsPage,
    optimizeReportPage,
    taskDetailsPage,
    taskPanelPage,
  }) => {
    test.slow();
    const reportName = await generateRandomStringAsync(5);
    const processId = await generateRandomStringAsync(3);

    await test.step('Create a BPMN Diagram Template', async () => {
      await modelerHomePage.openHTOProjectFolder('HTO Project');
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with Zeebe User Task and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName(
        'Zeebe_User_Task_Process' + processId,
      );
      await modelerCreatePage.selectStartEventElement();
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickIdInput();
      await modelerCreatePage.fillIdInput(
        'Zeebe_User_Task_Process' + processId,
      );
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.assertImplementationOption('zeebeUserTask');
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.clickStartInstanceSubButton();
      await expect(page.getByText('Instance started!')).toBeVisible({
        timeout: 220000,
      });
    });
    await test.step('View Process Instance in Operate, complete User Task in Tasklist & assert process complete in Operate and Assert Process has been successfully imported in Optimize', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await navigationPage.goToOperate();
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink(
        'Zeebe_User_Task_Process' + processId,
      );

      await operateProcessInstancePage.activeIconAssertion();

      await sleep(10000);
      await navigationPage.goToTasklist();

      await taskPanelPage.openTask('Zeebe_User_Task_Process' + processId);
      await taskDetailsPage.clickAssignToMeButton();
      await taskDetailsPage.clickCompleteTaskButton();
      await expect(page.getByText('Task completed')).toBeVisible({
        timeout: 200000,
      });

      await navigationPage.goToOperate();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink(
        'Zeebe_User_Task_Process' + processId,
      );

      await operateProcessInstancePage.completedIconAssertion();

      await navigationPage.goToOptimize();
      await sleep(120000);

      await page.reload();
      await optimizeHomePage.clickCollectionsLink();
      await optimizeCollectionsPage.clickCreateNewButton();
      await optimizeCollectionsPage.clickReportOption();
      await optimizeReportPage.clickProcessSelectionButton();
      await optimizeReportPage.clickUserTaskProcess(
        'Zeebe_User_Task_Process' + processId,
      );
      await expect(optimizeReportPage.versionSelection).toBeVisible({
        timeout: 30000,
      });
      await optimizeReportPage.clickVersionSelection();
      await optimizeReportPage.clickAlwaysDisplayLatestSelection();
      await optimizeReportPage.clickBlankReportButton();
      await optimizeReportPage.clickCreateReportLink();

      await expect(optimizeReportPage.selectDropdown).toBeVisible({
        timeout: 60000,
      });
      await optimizeReportPage.clickSelectDropdown();
      await optimizeReportPage.clickUserTaskOption();
      await optimizeReportPage.clickHeatMapButton();
      await expect(optimizeReportPage.tableOption).toBeVisible({
        timeout: 30000,
      });
      await optimizeReportPage.clickTableOption();
      await optimizeReportPage.clickReportName();
      await optimizeReportPage.clearReportName();
      await optimizeReportPage.fillReportName(reportName);
      await optimizeReportPage.clickSaveButton();
      await optimizeHomePage.clickCollectionsLink();
      await expect(page.getByText(reportName)).toBeVisible({timeout: 120000});
      await optimizeCollectionsPage.clickMostRecentProcessReport(reportName);
      await optimizeReportPage.waitUntilLocatorIsVisible(
        optimizeReportPage.oneUserTaskInstance,
        page,
      );
      await expect(optimizeReportPage.oneUserTaskInstance).toBeVisible({
        timeout: 90000,
      });
    });
  });

  test('Zeebe User Task With Priority', async ({
    page,
    modelerHomePage,
    navigationPage,
    modelerCreatePage,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    taskDetailsPage,
    taskPanelPage,
  }) => {
    const processName = await generateRandomStringAsync(3);
    await test.step('Create a BPMN Diagram Template', async () => {
      await modelerHomePage.openHTOProjectFolder('HTO Project');
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });
    await test.step('Create BPMN Diagram with multiple Zeebe User Tasks with Priority and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName(
        'Zeebe_User_Task_Process_With_Priority' + processName,
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.addParallelUserTasks(4, 'priorityTest');
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton('parallelGateway');
      await modelerCreatePage.setPriority(1, 4);
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.clickStartInstanceSubButton();
      await expect(page.getByText('Instance started!')).toBeVisible({
        timeout: 220000,
      });
    });
    await test.step('Complete User Task in Tasklist & assert process complete in Operate', async () => {
      await navigationPage.goToTasklist();
      await expect(
        page
          .getByText('Zeebe_User_Task_Process_With_Priority' + processName)
          .first(),
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
      await taskPanelPage.clickCollapseFilter();
      await expect(page.getByRole('heading', {name: 'completed'})).toBeVisible({
        timeout: 45000,
      });
      await page.reload();
      await taskPanelPage.openTask('priorityTest4');
      await expect(
        page
          .getByText('Zeebe_User_Task_Process_With_Priority' + processName)
          .first(),
      ).toBeVisible({timeout: 60000});
      await taskDetailsPage.taskAssertion(
        'Zeebe_User_Task_Process_With_Priority' + processName,
      );
      await taskDetailsPage.priorityAssertion('critical');
      await navigationPage.goToOperate();
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await page.reload();
      await operateProcessesPage.clickProcessInstanceLink(
        'Zeebe_User_Task_Process_With_Priority' + processName,
      );
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
      await modelerCreatePage.clickStartInstanceSubButton();
      await modelerCreatePage.instanceStartedAssertion();
    });

    await test.step('View Process Instance in Operate, Edit the Variable & Assert the Variable is Updated in Tasklist', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await navigationPage.goToOperate();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink('User_Task_Process');

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

      await navigationPage.goToTasklist();

      await taskPanelPage.openTask('User_Task_Process');
      await expect(page.getByText('testVariable')).toBeVisible();
      await expect(page.getByText('"updatedValue"')).toBeVisible();
      await expect(page.getByText('"testValue"')).not.toBeVisible();
    });
  });
});
