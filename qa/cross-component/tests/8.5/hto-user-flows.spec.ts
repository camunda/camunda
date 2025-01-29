import {expect} from '@playwright/test';
import {test} from '@fixtures/8.5';
import {
  captureScreenshot,
  captureFailureVideo,
  generateRandomStringAsync,
} from '@setup';
import {TaskPanelPage} from '@pages/8.5/TaskPanelPage';
import {TaskDetailsPage} from '@pages/8.5/TaskDetailsPage';
import {OperateHomePage} from '@pages/8.5/OperateHomePage';
import {AppsPage} from '@pages/8.5/AppsPage';
import {OperateProcessesPage} from '@pages/8.5/OperateProcessesPage';
import {OperateProcessInstancePage} from '@pages/8.5/OperateProcessInstancePage';
import {HomePage} from '@pages/8.5/HomePage';
import {
  deployMultipleProcesses,
  disableRBA,
  runExistingDiagramOrCreate,
  enableRBA,
  assertPageTextWithRetry,
  deleteAllUserGroups,
} from '@pages/8.5/UtilitiesPage';
import {LoginPage} from '@pages/8.5/LoginPage';
import {sleep} from '../../utils/sleep';

test.describe('HTO User Flow Tests', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/');
    await loginPage.login();
  });

  test.afterEach(
    async (
      {
        page,
        homePage,
        clusterPage,
        clusterDetailsPage,
        appsPage,
        consoleOrganizationsPage,
      },
      testInfo,
    ) => {
      await captureScreenshot(page, testInfo);
      await captureFailureVideo(page, testInfo);
      if (testInfo.title.includes('RBA On')) {
        await disableRBA(homePage, clusterPage, clusterDetailsPage, appsPage);
      }
      if (testInfo.title.includes('Candidate Group')) {
        await deleteAllUserGroups(homePage, consoleOrganizationsPage, appsPage);
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
        timeout: 120000,
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
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.chooseImplementationOption('jobWorker');
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
      await expect(operateTabHomePage.processesTab).toBeVisible({
        timeout: 120000,
      });
      await operateTabHomePage.clickProcessesTab();
      await expect(operateTabProcessesPage.processPageHeading).toBeVisible({
        timeout: 60000,
      });
      await operateTabProcessesPage.clickRunningProcessInstancesCheckbox();
      await operateTabProcessesPage.clickProcessActiveCheckbox();
      await expect(operateTabProcessesPage.processResultCount).not.toBe(0);
      await operateTabProcessesPage.clickProcessActiveCheckbox();
      await operateTabProcessesPage.clickProcessIncidentsCheckbox();
      await expect(
        operateTabProcessesPage.noMatchingInstancesMessage,
      ).toBeVisible({timeout: 90000});

      await operateHomePage.clickCamundaApps();
      await operateTabAppsPage.clickTasklistLink();

      await operateTabTaskPanelPage.openTask('User_Task_Process');
      await operateTabTaskDetailsPage.clickAssignToMeButton();
      await operateTabTaskDetailsPage.clickCompleteTaskButton();
      await operateTabTaskPanelPage.filterBy('Completed');
      await operateTabTaskPanelPage.openTask('User_Task_Process');
      await expect(
        operateTabTaskDetailsPage.detailsInfo.getByText('User_Task_Process'),
      ).toBeVisible();
      await operateHomePage.clickCamundaComponents();
      await operateTabAppsPage.clickOperateLink();

      await operateTabHomePage.clickProcessesTab();
      await operateTabProcessesPage.clickRunningProcessInstancesCheckbox();
      await expect(
        operateTabProcessesPage.noMatchingInstancesMessage,
      ).toBeVisible({timeout: 60000});
      await operateTabProcessesPage.clickProcessCompletedCheckbox();
      await expect(operateTabProcessesPage.processResultCount).not.toBe(0);
    });
  });

  test('User Task Editing Variables Flow', async ({
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
        timeout: 120000,
      });
    });

    await test.step('Navigate to HTO User Flow BPMN Diagram in HTO project', async () => {
      await expect(modelerHomePage.htoProjectFolder).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickHTOProjectFolder();
      await expect(modelerHomePage.htoUserFlowDiagram).toBeVisible({
        timeout: 60000,
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
      const operateTab = await page.waitForEvent('popup', {timeout: 60000});
      const operateTabHomePage = new OperateHomePage(operateTab);
      const operateTabAppsPage = new AppsPage(operateTab);
      const operateHomePage = new HomePage(operateTab);
      const operateTabProcessInstancePage = new OperateProcessInstancePage(
        operateTab,
      );
      const operateTabTaskPanelPage = new TaskPanelPage(operateTab);

      await operateTab.reload();
      await expect(operateTabProcessInstancePage.activeIcon).toBeVisible({
        timeout: 1200000,
      });
      await expect(operateTab.getByText('"testValue"')).toBeVisible({
        timeout: 30000,
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
        timeout: 120000,
      });

      await expect(operateTab.getByText('"testValue"')).not.toBeVisible({
        timeout: 60000,
      });
      await expect(operateTab.getByText('"updatedValue"')).toBeVisible();

      await operateHomePage.clickCamundaApps();
      await operateTabAppsPage.clickTasklistLink();

      await operateTabTaskPanelPage.openTask('User_Task_Process');
      await expect(operateTab.getByText('testVariable')).toBeVisible();
      await expect(operateTab.getByText('"updatedValue"')).toBeVisible();
      await expect(operateTab.getByText('"testValue"')).not.toBeVisible();
    });
  });

  test('User Task Restrictions Enabled Flow - Candidate Group', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    browser,
    taskPanelPage,
    consoleOrganizationsPage,
  }) => {
    const processName = await generateRandomStringAsync(3);

    await test.step('Delete All Groups If Exist', async () => {
      await homePage.clickOrganization();
      await consoleOrganizationsPage.clickGroupsTab();
      await consoleOrganizationsPage.deleteAllGroups();
    });

    await test.step('Create Two User Groups', async () => {
      await consoleOrganizationsPage.clickCreateGroupButton();
      await consoleOrganizationsPage.fillGroupNameInput('Single User');
      await consoleOrganizationsPage.clickCreateGroupSubButton();
      await expect(consoleOrganizationsPage.createGroupDialoge).not.toBeVisible(
        {
          timeout: 60000,
        },
      );

      await consoleOrganizationsPage.clickCreateGroupButton();
      await consoleOrganizationsPage.fillGroupNameInput('Multiple Users');
      await consoleOrganizationsPage.clickCreateGroupSubButton();
      await expect(consoleOrganizationsPage.createGroupDialoge).not.toBeVisible(
        {
          timeout: 60000,
        },
      );
    });

    await test.step('Assign Users to the Created Groups in Identity', async () => {
      await consoleOrganizationsPage.clickUserGroup('Single User');
      await consoleOrganizationsPage.clickAssignMembers();
      await consoleOrganizationsPage.fillAssignMembers([
        process.env.C8_USERNAME!,
      ]);
      await consoleOrganizationsPage.clickAssignSubButton();
      await consoleOrganizationsPage.clickOrganizationManagementLink();
      await consoleOrganizationsPage.clickGroupsTab();
      await consoleOrganizationsPage.clickUserGroup('Multiple Users');
      await consoleOrganizationsPage.clickAssignMembers();
      await consoleOrganizationsPage.fillAssignMembers([
        process.env.C8_USERNAME_TEST!,
        process.env.C8_USERNAME_TEST_2!,
      ]);
      await consoleOrganizationsPage.clickAssignSubButton();
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
        'User_Task_Process_Candidate_Group' + processName,
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.addParallelUserTasks(
        2,
        'candidateGroupTask' + processName,
      );
      await modelerCreatePage.clickAppendElementButton();
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
      await modelerCreatePage.fillAssigneeInput(process.env.C8_USERNAME_TEST!);

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
        'candidateGroupTask' + processName + '1',
      );

      await assertPageTextWithRetry(
        page,
        'candidateGroupTask' + processName + '2',
        true,
      );
    });

    await test.step('Clear cookies and reset session', async () => {
      const context = browser.contexts()[0];
      await context.clearCookies();
      await page.reload();
    });

    await test.step('Login with Test User and Check Tasks in Tasklist ', async () => {
      const loginPage = new LoginPage(page);
      await loginPage.loginWithTestUser({}, true);
      await homePage.clickCamundaComponents();
      await appsPage.clickTasklistLink();
      await assertPageTextWithRetry(
        page,
        'candidateGroupTask' + processName + '1',
      );
      await assertPageTextWithRetry(
        page,
        'candidateGroupTask' + processName + '2',
      );
    });

    await test.step('Clear cookies and reset session', async () => {
      const context = browser.contexts()[0];
      await context.clearCookies();
      await page.reload();
    });

    await test.step('Login with Second Test User and Check Tasks in Tasklist ', async () => {
      const loginPage = new LoginPage(page);
      await loginPage.loginWithTestUser(
        {username: process.env.C8_USERNAME_TEST_2},
        true,
      );
      await homePage.clickCamundaComponents();
      await appsPage.clickTasklistLink();
      await assertPageTextWithRetry(
        page,
        'candidateGroupTask' + processName + '1',
        true,
      );
      await assertPageTextWithRetry(
        page,
        'candidateGroupTask' + processName + '2',
      );
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
        timeout: 60000,
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
    const processIdBeforeEnablingRBA = 'Process_BEFORERBA' + randomString;

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
      await homePage.clickCamundaComponents();
      await appsPage.clickConsoleLink();
      await expect(homePage.consoleBanner).toBeVisible({timeout: 120000});
      await homePage.clickClusters();
      await clusterPage.clickTestClusterLink();
      await expect(clusterDetailsPage.settingsTab).toBeVisible({
        timeout: 90000,
      });
      await clusterDetailsPage.clickSettingsTab();
      await clusterDetailsPage.enableRBA();
      await homePage.clickClusters();
      await page.reload();
      await clusterPage.assertClusterHealthyStatusWithRetry(140000);
      await sleep(30000);
    });

    await test.step('Navigate to Users and Make Sure that No Authorized Resources Are Created', async () => {
      await homePage.clickOrganization();
      await consoleOrganizationsPage.clickUsersTab();
      await consoleOrganizationsPage.clickMainUser();
      await consoleOrganizationsPage.clickAuthorizedResources();

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
      await consoleOrganizationsPage.clickAuthorizedResources();
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
      await homePage.clickCamundaComponents();
      await appsPage.clickConsoleLink();
      await expect(homePage.consoleBanner).toBeVisible({timeout: 120000});
      await homePage.clickClusters();
      await clusterPage.clickTestClusterLink();
      await expect(clusterDetailsPage.settingsTab).toBeVisible({
        timeout: 90000,
      });
      await clusterDetailsPage.clickSettingsTab();
      await clusterDetailsPage.enableRBA();
      await homePage.clickClusters();
      await page.reload();
      await clusterPage.assertClusterHealthyStatusWithRetry(120000);
      await sleep(30000);
    });

    await test.step('Navigate to Users and Create Authorized Resource For Process 1', async () => {
      await homePage.clickOrganization();
      await consoleOrganizationsPage.clickUsersTab();
      await consoleOrganizationsPage.clickMainUser();
      await consoleOrganizationsPage.clickAuthorizedResources();
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

      await expect(page.getByText(processName1).first()).toBeVisible({
        timeout: 120000,
      });
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

      await expect(page.getByText(processName1).first()).toBeVisible({
        timeout: 120000,
      });
      await expect(page.getByText(processName2)).not.toBeVisible({
        timeout: 120000,
      });
    });
  });

  test('RBA On User Flow - Permission for All Processes', async ({
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
    const processName1 = 'AFTER_RBA_' + randomString + '_1';
    const processName2 = 'AFTER_RBA_' + randomString + '_2';
    const processId1 = 'AFTERRBA' + randomString + '1';
    const processId2 = 'AFTERRBA' + randomString + '2';

    await test.step('Navigate to Console and Enable RBA', async () => {
      await enableRBA(homePage, clusterPage, clusterDetailsPage, appsPage);
      await sleep(60000);
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

    await test.step('Create Two BPMN Diagrams with User Task and Start Process Instance', async () => {
      await deployMultipleProcesses(
        page,
        modelerHomePage,
        modelerCreatePage,
        2,
        'AFTER_RBA_' + randomString + '_',
        'AFTERRBA' + randomString,
        true,
        true,
      );
    });

    await test.step('Navigate to Users and Assert Authorized Resources Are Created For Both Processes', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickConsoleLink();
      await homePage.clickOrganization();
      await consoleOrganizationsPage.clickUsersTab();
      await consoleOrganizationsPage.clickMainUser();
      await consoleOrganizationsPage.clickAuthorizedResources();

      await consoleOrganizationsPage.authorizedResourceAssertion(processId1);
      await consoleOrganizationsPage.authorizedResourceAssertion(processId2);
    });

    await test.step('Navigate to Tasklist and Assert Both Processes Are Accessible', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickTasklistLink();
      await expect(taskPanelPage.taskListPageBanner).toBeVisible({
        timeout: 120000,
      });
      await taskPanelPage.clickProcessesTab();
      await taskProcessesPage.clickpopupContinueButton();

      await assertPageTextWithRetry(page, processName1);
      await assertPageTextWithRetry(page, processName2);
    });

    await test.step('Assert Processes Can Be Started From Tasklist', async () => {
      await taskPanelPage.clickTasksTab();
      const firstProcessTaskCount = await taskPanelPage.taskCount(processName1);
      const secondProcessTaskCount =
        await taskPanelPage.taskCount(processName2);

      //Start first process
      await taskPanelPage.clickProcessesTab();
      await taskProcessesPage.startProcess(processName1);
      //Start second process
      await taskProcessesPage.startProcess(processName2);

      await taskPanelPage.clickTasksTab();
      const updatedSecondProcessTaskCount =
        await taskPanelPage.taskCount(processName2);
      const updatedFirstProcessTaskCount =
        await taskPanelPage.taskCount(processName1);
      expect(updatedSecondProcessTaskCount).toBe(secondProcessTaskCount + 1);
      expect(updatedFirstProcessTaskCount).toBe(firstProcessTaskCount + 1);
    });

    await test.step('Navigate to Operate and Assert Both Processes Are Accessible', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickOperateLink();
      await expect(operateHomePage.operateBanner).toBeVisible({
        timeout: 120000,
      });
      await operateHomePage.clickProcessesTab();

      await assertPageTextWithRetry(page, processName1);
      await assertPageTextWithRetry(page, processName2);
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
    });

    await test.step('Login with Test User and Check Tasks in Tasklist ', async () => {
      const loginPage = new LoginPage(page);
      await loginPage.loginWithTestUser({}, true);
      await homePage.clickCamundaComponents();
      await appsPage.clickTasklistLink();
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
