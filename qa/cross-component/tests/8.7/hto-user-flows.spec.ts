import {expect} from '@playwright/test';
import {test} from '@fixtures/8.7';
import {
  captureScreenshot,
  captureFailureVideo,
  generateRandomStringAsync,
} from '@setup';
import {TaskPanelPage} from '@pages/8.7/TaskPanelPage';
import {TaskDetailsPage} from '@pages/8.7/TaskDetailsPage';
import {OperateHomePage} from '@pages/8.7/OperateHomePage';
import {AppsPage} from '@pages/8.7/AppsPage';
import {OperateProcessesPage} from '@pages/8.7/OperateProcessesPage';
import {OperateProcessInstancePage} from '@pages/8.7/OperateProcessInstancePage';
import {HomePage} from '@pages/8.7/HomePage';
import {LoginPage} from '@pages/8.7/LoginPage';
import {
  deployMultipleProcesses,
  disableRBA,
  enableRBA,
  runExistingDiagramOrCreate,
  assertPageTextWithRetry,
} from '@pages/8.7/UtilitiesPage';
import {sleep} from '../../utils/sleep';

test.describe('HTO User Flow Tests', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/');
    await loginPage.login();
  });

  test.afterEach(
    async (
      {page, homePage, clusterPage, clusterDetailsPage, appsPage},
      testInfo,
    ) => {
      await captureScreenshot(page, testInfo);
      await captureFailureVideo(page, testInfo);
      if (testInfo.title.includes('RBA On')) {
        await disableRBA(homePage, clusterPage, clusterDetailsPage, appsPage);
      }
    },
  );

  test('Job Worker User Task Most Common Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
  }) => {
    test.slow();
    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 120000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 180000,
      });
    });

    await test.step('Create New Project with a BPMN Diagram Template', async () => {
      await expect(modelerHomePage.createNewProjectButton).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickCreateNewProjectButton();
      await expect(modelerHomePage.projectNameInput).toBeVisible({
        timeout: 60000,
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
      await sleep(10000);
      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickIdInput();
      await modelerCreatePage.fillIdInput('User_Task_Process');
      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.chooseImplementationOption('jobWorker');
      await modelerCreatePage.assertImplementationOption('jobWorker');
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate, complete User Task in Tasklist & assert process complete in Operate', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();
      const operateTabPromise = page.waitForEvent('popup', {timeout: 60000});
      const operateTab = await operateTabPromise;
      const operateTabHomePage = new OperateHomePage(operateTab);
      const operateTabProcessesPage = new OperateProcessesPage(operateTab);
      const operateTabAppsPage = new AppsPage(operateTab);
      const operateHomePage = new HomePage(operateTab);
      const operateTabTaskPanelPage = new TaskPanelPage(operateTab);
      const operateTabTaskDetailsPage = new TaskDetailsPage(operateTab);
      const operateTabProcessInstancePage = new OperateProcessInstancePage(
        operateTab,
      );

      await expect(operateTabProcessInstancePage.activeIcon).toBeVisible({
        timeout: 1200000,
      });

      await operateHomePage.clickCamundaApps();
      await operateTabAppsPage.clickTasklistLink();

      await operateTabTaskPanelPage.openTask('User_Task_Process');
      await operateTabTaskDetailsPage.clickAssignToMeButton();
      await operateTabTaskDetailsPage.clickCompleteTaskButton();
      await expect(operateTab.getByText('Task completed')).toBeVisible({
        timeout: 200000,
      });
      await operateHomePage.clickCamundaComponents();
      await operateTabAppsPage.clickOperateLink();

      await operateTabHomePage.clickProcessesTab();
      await sleep(10000);
      await operateTab.reload();
      await operateTabProcessesPage.clickProcessCompletedCheckbox();
      await operateTabProcessesPage.clickProcessInstanceLink(
        'User_Task_Process',
      );
      await operateTab.reload();
      await expect(operateTabProcessInstancePage.completedIcon).toBeVisible({
        timeout: 180000,
      });
    });
  });

  test('User Task Editing Variables Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
  }) => {
    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 120000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Navigate to HTO User Flow BPMN Diagram in HTO project', async () => {
      await expect(modelerHomePage.htoProjectFolder).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickHTOProjectFolder();
      await expect(modelerHomePage.htoUserFlowDiagram).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickHTOUserFlowDiagram();
    });

    await test.step('Start Process Instance with variables', async () => {
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(modelerCreatePage.variableInput).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickVariableInput();
      await modelerCreatePage.fillVariableInput('{"testVariable":"testValue"}');
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate, Edit the Variable & Assert the Variable is Updated in Tasklist', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();
      const operateTab = await page.waitForEvent('popup');
      const operateTabHomePage = new OperateHomePage(operateTab);
      const operateTabAppsPage = new AppsPage(operateTab);
      const operateHomePage = new HomePage(operateTab);
      const operateTabProcessInstancePage = new OperateProcessInstancePage(
        operateTab,
      );
      const operateTabTaskPanelPage = new TaskPanelPage(operateTab);

      await expect(operateTabProcessInstancePage.activeIcon).toBeVisible({
        timeout: 1200000,
      });
      await expect(operateTab.getByText('"testValue"')).toBeVisible({
        timeout: 90000,
      });
      await expect(
        operateTab.getByText('testVariable', {exact: true}),
      ).toBeVisible();
      await operateTabHomePage.clickEditVariableButton('testVariable');
      await operateTabHomePage.clickVariableValueInput();
      await operateTabHomePage.clearVariableValueInput();
      await operateTabHomePage.fillVariableValueInput('"updatedValue"');
      await operateTabHomePage.clickSaveVariableButton();

      await expect(operateTabHomePage.editVariableSpinner).not.toBeVisible({
        timeout: 180000,
      });

      await expect(operateTab.getByText('"testValue"')).not.toBeVisible({
        timeout: 60000,
      });
      await expect(operateTab.getByText('"updatedValue"')).toBeVisible();

      await operateHomePage.clickCamundaApps();
      await operateTabAppsPage.clickTasklistLink();

      await operateTabTaskPanelPage.openTask('User_Task_Process');
      await expect(operateTab.getByText('testVariable')).toBeVisible({
        timeout: 60000,
      });
      await expect(operateTab.getByText('"updatedValue"')).toBeVisible();
      await expect(operateTab.getByText('"testValue"')).not.toBeVisible();
    });
  });

  test('RBA Off User Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    clusterPage,
    clusterDetailsPage,
    taskPanelPage,
    taskProcessesPage,
  }) => {
    test.slow();
    await test.step('Navigate to Console', async () => {
      await disableRBA(homePage, clusterPage, clusterDetailsPage, appsPage);
    });

    await test.step('Navigate to Web Modeler', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Navigate to HTO User Flow BPMN Diagram in HTO project', async () => {
      await expect(modelerHomePage.htoProjectFolder).toBeVisible({
        timeout: 90000,
      });
      await modelerHomePage.clickHTOProjectFolder();
    });

    await test.step('Create Two BPMN Diagrams with User Task and Start Process Instance', async () => {
      await deployMultipleProcesses(
        page,
        modelerHomePage,
        modelerCreatePage,
        2,
        'User_Task_Process_RBA_OFF_',
      );
    });

    await test.step('Navigate to Takslist and Make Sure that the Two Deployed Processes Are Accessible', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickTasklistFilter();
      await expect(taskPanelPage.taskListPageBanner).toBeVisible({
        timeout: 120000,
      });
      await taskPanelPage.clickProcessesTab();
      await taskProcessesPage.clickpopupContinueButton();

      await expect(
        page.getByText('User_Task_Process_RBA_OFF_1').last(),
      ).toBeVisible({
        timeout: 120000,
      });

      await expect(
        page.getByText('User_Task_Process_RBA_OFF_2').last(),
      ).toBeVisible({
        timeout: 120000,
      });
    });
  });

  test('RBA On User Flow - No User Permission', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    clusterPage,
    clusterDetailsPage,
    taskPanelPage,
    taskProcessesPage,
    operateHomePage,
    consoleOrganizationsPage,
  }) => {
    test.slow();
    const randomString = await generateRandomStringAsync(3);
    const processNameBeforeEnablingRBA = 'BEFORE_RBA_' + randomString + '_';
    const processIdBeforeEnablingRBA = 'Process_BEFORE' + randomString;

    await test.step('Navigate to Console and Ensure RBA is Disabled', async () => {
      await disableRBA(homePage, clusterPage, clusterDetailsPage, appsPage);
    });

    await test.step('Navigate to Web Modeler', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Navigate to HTO User Flow BPMN Diagram in HTO project', async () => {
      await expect(modelerHomePage.htoProjectFolder).toBeVisible({
        timeout: 90000,
      });
      await modelerHomePage.clickHTOProjectFolder();
    });

    await test.step('Create Two BPMN Diagrams with User Task and Start Process Instance', async () => {
      await deployMultipleProcesses(
        page,
        modelerHomePage,
        modelerCreatePage,
        2,
        processNameBeforeEnablingRBA,
        processIdBeforeEnablingRBA,
        true,
      );
    });

    await test.step('Navigate back to Console and Enable RBA', async () => {
      await enableRBA(homePage, clusterPage, clusterDetailsPage, appsPage);
      await sleep(30000);
    });

    await test.step('Navigate to Users and Make Sure that No Authorized Resources Are Created', async () => {
      await homePage.clickOrganization();
      await consoleOrganizationsPage.clickUsersTab();
      await consoleOrganizationsPage.clickMainUser();
      await consoleOrganizationsPage.clickAuthorizations();

      await expect(
        consoleOrganizationsPage.noAuthorizedResourceMessage,
      ).toBeVisible({timeout: 30000});
      await expect(
        page.getByText(processIdBeforeEnablingRBA + '1', {exact: true}),
      ).not.toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText(processIdBeforeEnablingRBA + '2', {exact: true}),
      ).not.toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Navigate to Tasklist and Make Sure that the Two Deployed Processes Are Not Accessible', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickTasklistLink();
      await expect(taskPanelPage.taskListPageBanner).toBeVisible({
        timeout: 120000,
      });
      await taskPanelPage.clickProcessesTab();
      await taskProcessesPage.clickpopupContinueButton();

      await expect(
        page.getByText(processNameBeforeEnablingRBA + '1'),
      ).not.toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText(processNameBeforeEnablingRBA + '2'),
      ).not.toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Navigate to Operate and Make Sure that the Two Deployed Processes Are Not Accessible', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickOperateLink();
      await expect(operateHomePage.operateBanner).toBeVisible({
        timeout: 120000,
      });
      await operateHomePage.clickProcessesTab();
      await sleep(20000);
      await page.reload();

      await expect(
        page.getByText(processNameBeforeEnablingRBA + '1'),
      ).not.toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText(processNameBeforeEnablingRBA + '2'),
      ).not.toBeVisible({
        timeout: 120000,
      });
    });
  });

  test('RBA On User Flow - Permission for One Process', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    clusterPage,
    clusterDetailsPage,
    taskPanelPage,
    taskProcessesPage,
    operateHomePage,
    consoleOrganizationsPage,
  }) => {
    test.slow();
    const randomString = await generateRandomStringAsync(3);
    let processName1 = 'BEFORE_RBA_' + randomString + '_1';
    let processName2 = 'BEFORE_RBA_' + randomString + '_2';
    let processId1 = 'Process_BEFORERBA' + randomString + '1';
    let processId2 = 'Process_BEFORERBA' + randomString + '2';

    await test.step('Navigate to Console and Ensure RBA is Disabled', async () => {
      await disableRBA(homePage, clusterPage, clusterDetailsPage, appsPage);
    });

    await test.step('Delete Authorised Resources If Exist', async () => {
      await homePage.clickOrganization();
      await consoleOrganizationsPage.clickUsersTab();
      await consoleOrganizationsPage.clickMainUser();
      await consoleOrganizationsPage.clickAuthorizations();
      await consoleOrganizationsPage.deleteAuthorisedResourcesIfExist();
    });

    await test.step('Navigate to Web Modeler', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Navigate to HTO User Flow BPMN Diagram in HTO project', async () => {
      await expect(modelerHomePage.htoProjectFolder).toBeVisible({
        timeout: 90000,
      });
      await modelerHomePage.clickHTOProjectFolder();
      await sleep(5000);
    });

    await test.step('Run existing Two BPMN Diagrams or Create New Diagrams', async () => {
      const projects = await modelerHomePage.getProjectNames('BEFORE_RBA_');
      if (projects.length >= 2) {
        processName1 = projects[1];
        processName2 = projects[0];
        processId1 = 'Process_' + processName1.replace(/_/g, '');
        processId2 = 'Process_' + processName2.replace(/_/g, '');
      }
      await runExistingDiagramOrCreate(
        page,
        modelerHomePage,
        modelerCreatePage,
        processName1,
        processId1,
        false,
      );
      await runExistingDiagramOrCreate(
        page,
        modelerHomePage,
        modelerCreatePage,
        processName2,
        processId2,
        false,
      );
    });

    await test.step('Navigate back to Console and Enable RBA', async () => {
      await enableRBA(homePage, clusterPage, clusterDetailsPage, appsPage);
      await sleep(90000);
    });

    await test.step('Navigate to Users and Create Authorized Resource For Process 1', async () => {
      await homePage.clickOrganization();
      await consoleOrganizationsPage.clickUsersTab();
      await consoleOrganizationsPage.clickMainUser();
      await consoleOrganizationsPage.clickAuthorizations();
      await expect(page.getByText(processId1, {exact: true})).not.toBeVisible({
        timeout: 60000,
      });
      await consoleOrganizationsPage.clickCreateResourceAuthorizationButton();
      await expect(
        consoleOrganizationsPage.createResourceAuthorizationDialog,
      ).toBeVisible({timeout: 60000});
      await consoleOrganizationsPage.clickNextButton();
      await consoleOrganizationsPage.clickProcessIdInput();
      await consoleOrganizationsPage.fillProcessIdInput(processId1);
      await consoleOrganizationsPage.clickNextButton();
      await consoleOrganizationsPage.checkReadPermissionCheckbox();
      await consoleOrganizationsPage.checkStartInstancePermissionCheckbox();
      await consoleOrganizationsPage.clickCreateAuthorizedResourceButton();

      await consoleOrganizationsPage.processIdResourceAssertion(processId1);
    });

    await test.step('Navigate to Tasklist and Only First Process Is Accessible', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickTasklistLink();
      await expect(taskPanelPage.taskListPageBanner).toBeVisible({
        timeout: 120000,
      });
      await taskPanelPage.clickProcessesTab();
      await taskProcessesPage.clickpopupContinueButton();

      await assertPageTextWithRetry(page, processName1);
      await expect(page.getByText(processName2)).not.toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Navigate to Operate and Only First Process Is Accessible', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickOperateLink();
      await expect(operateHomePage.operateBanner).toBeVisible({
        timeout: 120000,
      });
      await operateHomePage.clickProcessesTab();
      await sleep(20000);

      await assertPageTextWithRetry(page, processName1);
      await expect(page.getByText(processName2)).not.toBeVisible({
        timeout: 120000,
      });
    });
  });

  test('User Task Restrictions Enabled Flow - Candidate User', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    browser,
    clusterDetailsPage,
    clusterPage,
    taskPanelPage,
  }) => {
    const processName = await generateRandomStringAsync(3);
    await test.step('Navigate to Console and Ensure User Task Resctrictions is Enabled ', async () => {
      await expect(page.getByText('Redirecting')).not.toBeVisible({
        timeout: 60000,
      });
      await expect(homePage.clusterTab).toBeVisible({timeout: 120000});
      await homePage.clickClusters();
      await clusterPage.clickTestClusterLink();
      await expect(clusterDetailsPage.settingsTab).toBeVisible({
        timeout: 90000,
      });
      await clusterDetailsPage.clickSettingsTab();
      await clusterDetailsPage.userTaskEnabledAssertion();
    });

    await test.step('Navigate to Web Modeler', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Create HTO User Flow BPMN Diagram in HTO project', async () => {
      await expect(modelerHomePage.htoProjectFolder).toBeVisible({
        timeout: 90000,
      });
      await modelerHomePage.clickHTOProjectFolder();
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with Multiple User Tasks', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 200000,
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
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton('parallelGateway');
    });

    await test.step('Assign User tasks to Candidate Users and Start Process Instance', async () => {
      await modelerCreatePage.selectUserTask(
        'candidateUserTask' + processName + '1',
      );

      await modelerCreatePage.expandAssignmentSection();
      await modelerCreatePage.clickCandidateUsersInput();
      await modelerCreatePage.fillCandidateUsersInput(
        process.env.C8_USERNAME_TEST!,
      );
      await modelerCreatePage.clickAssigneeInput();
      await modelerCreatePage.fillAssigneeInput(process.env.C8_USERNAME!);

      await modelerCreatePage.clickCanvas();
      await modelerCreatePage.selectUserTask(
        'candidateUserTask' + processName + '2',
      );
      await modelerCreatePage.expandAssignmentSection();
      await modelerCreatePage.clickCandidateUsersInput();
      await modelerCreatePage.fillCandidateUsersInput(process.env.C8_USERNAME!);

      await modelerCreatePage.clickCanvas();
      await modelerCreatePage.selectUserTask(
        'candidateUserTask' + processName + '3',
      );

      await modelerCreatePage.expandAssignmentSection();
      await modelerCreatePage.clickCandidateUsersInput();
      await modelerCreatePage.fillCandidateUsersInput(
        process.env.C8_USERNAME_TEST!,
      );

      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View User Tasks in Tasklist', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 120000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickTasklistFilter();
      await expect(taskPanelPage.taskListPageBanner).toBeVisible({
        timeout: 60000,
      });
      await assertPageTextWithRetry(
        page,
        'candidateUserTask' + processName + '1',
      );

      await assertPageTextWithRetry(
        page,
        'candidateUserTask' + processName + '2',
      );

      await assertPageTextWithRetry(
        page,
        'candidateUserTask' + processName + '3',
        true,
      );
    });

    await test.step('Clear cookies and reset session', async () => {
      const context = browser.contexts()[0];
      await context.clearCookies();
      await sleep(10000);
      await page.reload();
      await page.goto('/');
    });

    await test.step('Login with Test User and Check Tasks in Tasklist ', async () => {
      const loginPage = new LoginPage(page);
      await loginPage.loginWithTestUser();
      await homePage.clickCamundaComponents();
      await appsPage.clickTasklistLink();
      await expect(taskPanelPage.taskListPageBanner).toBeVisible({
        timeout: 120000,
      });
      await assertPageTextWithRetry(
        page,
        'candidateUserTask' + processName + '1',
      );
      await assertPageTextWithRetry(
        page,
        'candidateUserTask' + processName + '2',
        true,
      );
      await assertPageTextWithRetry(
        page,
        'candidateUserTask' + processName + '3',
      );
    });
  });
});
