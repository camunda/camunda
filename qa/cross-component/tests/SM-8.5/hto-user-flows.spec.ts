import {expect} from '@playwright/test';
import {test} from '@fixtures/SM-8.5';
import {deleteAllUserGroups} from '@pages/SM-8.5/UtilitiesPage';
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

  test.afterEach(async ({page, navigationPage, identityPage}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
    if (testInfo.title.includes('Candidate Group')) {
      await deleteAllUserGroups(navigationPage, identityPage);
    }
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
      await modelerCreatePage.clickIdInput();
      await modelerCreatePage.fillIdInput('User_Task_Process');
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
      await expect(page.getByText('Instance started!')).toBeVisible({
        timeout: 220000,
      });
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

      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.chooseImplementationOption('zeebeUserTask');
      await modelerCreatePage.clickAppendEndEventButton();
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await modelerCreatePage.clickStartInstanceSubButton();
      await expect(page.getByText('Instance started!')).toBeVisible({
        timeout: 220000,
      });
    });
    await test.step('View Process Instance in Operate, Create User Task Report in Optimize, Start Another Process Instance in Modeler & Assert the Report Updates', async () => {
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
      await expect(page.getByText('Instance started!')).toBeVisible({
        timeout: 180000,
      });
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

  test('User Task Restrictions Enabled Flow - Candidate User', async ({
    page,
    modelerHomePage,
    navigationPage,
    modelerCreatePage,
    operateHomePage,
    operateProcessInstancePage,
    taskDetailsPage,
    taskPanelPage,
    settingsPage,
    loginPage,
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

    await test.step('Create BPMN Diagram with Multiple User Tasks', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName(
        'User_Task_Process_Candidate_User' + processName,
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.addParallelUserTasks(
        3,
        'candidateUserTask' + processName,
      );
      await modelerCreatePage.clickAppendEndEventButton('parallelGateway');
    });

    await test.step('Assign User tasks to Candidate Users and Start Process Instance', async () => {
      await modelerCreatePage.selectUserTask(
        'candidateUserTask' + processName + '1',
      );

      await modelerCreatePage.expandAssignmentSection();
      await modelerCreatePage.clickCandidateUsersInput();
      await modelerCreatePage.fillCandidateUsersInput('lisa');
      await modelerCreatePage.clickAssigneeInput();
      await modelerCreatePage.fillAssigneeInput('demo');

      await modelerCreatePage.clickCanvas();
      await modelerCreatePage.selectUserTask(
        'candidateUserTask' + processName + '2',
      );
      await modelerCreatePage.expandAssignmentSection();
      await modelerCreatePage.clickCandidateUsersInput();
      await modelerCreatePage.fillCandidateUsersInput('demo');

      await modelerCreatePage.clickCanvas();
      await modelerCreatePage.selectUserTask(
        'candidateUserTask' + processName + '3',
      );

      await modelerCreatePage.expandAssignmentSection();
      await modelerCreatePage.clickCandidateUsersInput();
      await modelerCreatePage.fillCandidateUsersInput('lisa');
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await modelerCreatePage.clickStartInstanceSubButton();
      await expect(page.getByText('Instance started!')).toBeVisible({
        timeout: 220000,
      });
    });

    await test.step('View User Tasks in Tasklist', async () => {
      await navigationPage.goToTasklist();

      await expect(
        page.getByText('candidateUserTask' + processName + '1'),
      ).toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText('candidateUserTask' + processName + '2'),
      ).toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText('candidateUserTask' + processName + '3'),
      ).not.toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Logout and Login with "lisa" User and Check Tasks in Tasklist', async () => {
      await settingsPage.clickOpenSettingsButton();
      await settingsPage.clickLogoutButton();

      await loginPage.fillUsername('lisa');
      await expect(loginPage.usernameInput).toHaveValue('lisa');
      await loginPage.fillPassword(
        process.env.DISTRO_QA_E2E_TESTS_IDENTITY_SECONDUSER_PASSWORD!,
      );

      await loginPage.clickLoginButton();
      await expect(taskPanelPage.tasklistBanner).toBeVisible({timeout: 120000});
    });

    await test.step('Navigate to Tasklist and Assert User Tasks ', async () => {
      await expect(
        page.getByText('candidateUserTask' + processName + '1'),
      ).toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText('candidateUserTask' + processName + '2'),
      ).not.toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText('candidateUserTask' + processName + '3'),
      ).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Complete a userTask', async () => {
      await taskPanelPage.openTask('candidateUserTask' + processName + '3');
      await taskDetailsPage.clickAssignToMeButton();
      await taskDetailsPage.clickCompleteTaskButton();
      await expect(page.getByText('Task completed')).toBeVisible({
        timeout: 200000,
      });
    });

    await test.step('Navigate to Operate and Assert Process Instance', async () => {
      await navigationPage.goToOperate();
      await operateHomePage.openProcess(
        'User_Task_Process_Candidate_User' + processName,
      );
      await operateProcessInstancePage.taskActiveIconAssertion(2);
    });
  });

  test('User Task Restrictions Enabled Flow - Candidate Group', async ({
    page,
    modelerHomePage,
    navigationPage,
    modelerCreatePage,
    operateHomePage,
    operateProcessInstancePage,
    taskDetailsPage,
    taskPanelPage,
    settingsPage,
    loginPage,
    identityPage,
  }) => {
    const processName = await generateRandomStringAsync(3);

    await test.step('Create Two User Groups in Identity', async () => {
      await navigationPage.goToIdentity();
      await identityPage.clickGroupsTab();
      await identityPage.clickCreateGroupButton();
      await identityPage.fillGroupNameInput('Single User');
      await identityPage.clickCreateGroupSubButton();
      await expect(identityPage.createGroupDialoge).not.toBeVisible({
        timeout: 60000,
      });

      await identityPage.clickCreateGroupButton();
      await identityPage.fillGroupNameInput('Multiple Users');
      await identityPage.clickCreateGroupSubButton();
      await expect(identityPage.createGroupDialoge).not.toBeVisible({
        timeout: 60000,
      });
    });

    await test.step('Assign Users to the Created Groups in Identity', async () => {
      await identityPage.clickUserGroup('Single User');
      await identityPage.clickAssignMembers();
      await identityPage.fillAssignMembers(['demo']);
      await identityPage.clickAssignSubButton();
      await identityPage.clickGroupsTab();
      await identityPage.clickUserGroup('Multiple Users');
      await identityPage.clickAssignMembers();
      await identityPage.fillAssignMembers(['bart', 'lisa']);
      await identityPage.clickAssignSubButton();
    });

    await test.step('Create a BPMN Diagram Template', async () => {
      await navigationPage.goToModeler();
      await modelerHomePage.openHTOProjectFolder('HTO Project');
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with Multiple User Tasks', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName(
        'User_Task_Process_Candidate_Group' + processName,
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.addParallelUserTasks(
        2,
        'candidateGroupTask' + processName,
      );
      await modelerCreatePage.clickAppendEndEventButton('parallelGateway');
    });

    await test.step('Assign User tasks to Candidate Groups and Start Process Instance', async () => {
      await modelerCreatePage.selectUserTask(
        'candidateGroupTask' + processName + '1',
      );
      await modelerCreatePage.expandAssignmentSection();
      await modelerCreatePage.clickCandidateGroupsInput();
      await modelerCreatePage.fillCandidateGroupsInput('Single User');
      await modelerCreatePage.clickAssigneeInput();
      await modelerCreatePage.fillAssigneeInput('lisa');
      await modelerCreatePage.clickCanvas();
      await modelerCreatePage.selectUserTask(
        'candidateGroupTask' + processName + '2',
      );
      await modelerCreatePage.expandAssignmentSection();
      await modelerCreatePage.clickCandidateGroupsInput();
      await modelerCreatePage.fillCandidateGroupsInput('Multiple Users');
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });

      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await modelerCreatePage.clickStartInstanceSubButton();
      await expect(page.getByText('Instance started!')).toBeVisible({
        timeout: 220000,
      });
    });

    await test.step('View User Tasks in Tasklist', async () => {
      await navigationPage.goToTasklist();
      await expect(
        page.getByText('candidateGroupTask' + processName + '1'),
      ).toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText('candidateGroupTask' + processName + '2'),
      ).not.toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Logout and Login with "lisa" User and Check Tasks in Tasklist', async () => {
      await settingsPage.clickOpenSettingsButton();
      await settingsPage.clickLogoutButton();
      await loginPage.fillUsername('lisa');
      await expect(loginPage.usernameInput).toHaveValue('lisa');
      await loginPage.fillPassword(
        process.env.DISTRO_QA_E2E_TESTS_IDENTITY_SECONDUSER_PASSWORD!,
      );
      await loginPage.clickLoginButton();
      await expect(taskPanelPage.tasklistBanner).toBeVisible({timeout: 120000});
    });

    await test.step('Navigate to Tasklist and Assert User Tasks ', async () => {
      await expect(
        page.getByText('candidateGroupTask' + processName + '1'),
      ).toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText('candidateGroupTask' + processName + '2'),
      ).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Logout and Login with "bart" User and Check Tasks in Tasklist', async () => {
      await settingsPage.clickOpenSettingsButton();
      await settingsPage.clickLogoutButton();
      await loginPage.fillUsername('bart');
      await expect(loginPage.usernameInput).toHaveValue('bart');
      await loginPage.fillPassword(
        process.env.DISTRO_QA_E2E_TESTS_IDENTITY_THIRDUSER_PASSWORD!,
      );
      await loginPage.clickLoginButton();
      await expect(taskPanelPage.tasklistBanner).toBeVisible({timeout: 120000});
    });

    await test.step('Navigate to Tasklist and Assert User Tasks ', async () => {
      await expect(
        page.getByText('candidateGroupTask' + processName + '1'),
      ).not.toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText('candidateGroupTask' + processName + '2'),
      ).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Complete a userTask', async () => {
      await taskPanelPage.openTask('candidateGroupTask' + processName + '2');
      await taskDetailsPage.clickAssignToMeButton();
      await taskDetailsPage.clickCompleteTaskButton();
      await expect(page.getByText('Task completed')).toBeVisible({
        timeout: 200000,
      });
    });

    await test.step('Navigate to Operate and Assert Process Instance', async () => {
      await navigationPage.goToOperate();
      await operateHomePage.openProcess(
        'User_Task_Process_Candidate_Group' + processName,
      );
      await operateProcessInstancePage.taskActiveIconAssertion(1);
    });
  });
});
