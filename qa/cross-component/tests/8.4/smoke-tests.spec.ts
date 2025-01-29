import {expect} from '@playwright/test';
import {test} from '@fixtures/8.4';
import {
  captureScreenshot,
  captureFailureVideo,
  generateRandomStringAsync,
  performBasicAuthPostRequest,
} from '@setup';
import {TaskPanelPage} from '@pages/8.4/TaskPanelPage';
import {TaskDetailsPage} from '@pages/8.4/TaskDetailsPage';
import {OperateHomePage} from '@pages/8.4/OperateHomePage';
import {AppsPage} from '@pages/8.4/AppsPage';
import {OperateProcessesPage} from '@pages/8.4/OperateProcessesPage';
import {OperateProcessInstancePage} from '@pages/8.4/OperateProcessInstancePage';
import {HomePage} from '@pages/8.4/HomePage';
import {sleep} from '../../utils/sleep';
import {OptimizeCollectionsPage} from '@pages/8.4/OptimizeCollectionsPage';
import {OptimizeReportPage} from '@pages/8.4/OptimizeReportPage';
import {OptimizeHomePage} from '@pages/8.4/OptimizeHomePage';

test.describe('Smoke Tests', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/');
    await loginPage.login();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Most Common Flow User Flow With All Apps', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
  }) => {
    test.slow();
    const reportName = await generateRandomStringAsync(5);
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
      await modelerHomePage.enterNewProjectName('Smoke Test Project');
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
      await modelerCreatePage.enterDiagramName('All_Apps_Most_Common_Flow');
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
        timeout: 180000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();
      const operateTabPromise = page.waitForEvent('popup');
      const operateTab = await operateTabPromise;
      const operateTabProcessInstancePage = new OperateProcessInstancePage(
        operateTab,
      );
      const operateTabAppsPage = new AppsPage(operateTab);
      const operateHomePage = new HomePage(operateTab);
      await operateTab.reload();
      await expect(operateTabProcessInstancePage.activeIcon).toBeVisible({
        timeout: 180000,
      });
      await operateHomePage.clickCamundaApps();
      await operateTabAppsPage.clickTasklistLink();

      const tasklistTabPromise = operateTab.waitForEvent('popup');
      const tasklistTab = await tasklistTabPromise;
      const tasklistTabTaskPanelPage = new TaskPanelPage(tasklistTab);
      const tasklistTabTaskDetailsPage = new TaskDetailsPage(tasklistTab);
      const tasklistTabAppsPage = new AppsPage(tasklistTab);
      const tasklistTabHomePage = new HomePage(tasklistTab);

      await tasklistTabTaskPanelPage.openTask('All_Apps_Most_Common_Flow');
      await tasklistTabTaskDetailsPage.clickAssignToMeButton();
      await tasklistTabTaskDetailsPage.clickCompleteTaskButton();
      await tasklistTabTaskPanelPage.filterBy('Completed');
      await tasklistTabTaskPanelPage.openTask('All_Apps_Most_Common_Flow');
      await expect(
        tasklistTabTaskDetailsPage.detailsInfo.getByText(
          'All_Apps_Most_Common_Flow',
        ),
      ).toBeVisible();
      await tasklistTabHomePage.clickCamundaApps();
      await tasklistTabAppsPage.clickOperateLink();

      const newOperateTab = await tasklistTab.waitForEvent('popup');
      const newOperateTabOperateHomePage = new OperateHomePage(newOperateTab);
      const newOperateTabProcessesPage = new OperateProcessesPage(
        newOperateTab,
      );
      const newOperateTabHomePage = new HomePage(newOperateTab);
      const newOperateTabAppsPage = new AppsPage(newOperateTab);

      await newOperateTabOperateHomePage.clickProcessesTab();
      await sleep(10000);
      await newOperateTab.reload();
      await newOperateTabProcessesPage.clickProcessCompletedCheckbox();
      await newOperateTabProcessesPage.clickProcessInstanceLink(
        'All_Apps_Most_Common_Flow',
      );
      await expect(operateTabProcessInstancePage.completedIcon).toBeVisible({
        timeout: 120000,
      });
      await newOperateTabHomePage.clickCamundaApps();
      await newOperateTabAppsPage.clickOptimizeLink();
      const optimizeTab = await newOperateTab.waitForEvent('popup', {
        timeout: 60000,
      });

      const optimizeTabOptimizeCollectionsPage = new OptimizeCollectionsPage(
        optimizeTab,
      );
      const optimizeTabOptimizeReportPage = new OptimizeReportPage(optimizeTab);
      const optimizeHomePage = new OptimizeHomePage(optimizeTab);

      await sleep(120000);

      await optimizeTab.reload();
      await optimizeTabOptimizeCollectionsPage.clickCreateNewButton();
      await optimizeTabOptimizeCollectionsPage.clickReportOption();
      await optimizeTabOptimizeReportPage.clickProcessSelectionButton();
      await optimizeTabOptimizeReportPage.clickUserTaskProcess(
        'All_Apps_Most_Common_Flow',
      );
      await expect(optimizeTabOptimizeReportPage.versionSelection).toBeVisible({
        timeout: 30000,
      });
      await optimizeTabOptimizeReportPage.clickVersionSelection();
      await optimizeTabOptimizeReportPage.clickAlwaysDisplayLatestSelection();
      await optimizeTabOptimizeReportPage.clickBlankReportButton();
      await optimizeTabOptimizeReportPage.clickCreateReportLink();

      await optimizeTabOptimizeReportPage.clickSelectDropdown();
      await optimizeTabOptimizeReportPage.clickUserTaskOption();
      await optimizeTabOptimizeReportPage.clickHeatMapButton();
      await expect(optimizeTabOptimizeReportPage.tableOption).toBeVisible({
        timeout: 30000,
      });
      await optimizeTabOptimizeReportPage.clickTableOption();
      await optimizeTabOptimizeReportPage.clickReportName();
      await optimizeTabOptimizeReportPage.clearReportName();
      await optimizeTabOptimizeReportPage.fillReportName(reportName);
      await optimizeTabOptimizeReportPage.clickSaveButton();
      await optimizeHomePage.clickCollectionsLink();
      await expect(optimizeTab.getByText(reportName)).toBeVisible({
        timeout: 60000,
      });
      await optimizeTabOptimizeCollectionsPage.clickMostRecentProcessReport();
      await optimizeTabOptimizeReportPage.waitUntilLocatorIsVisible(
        optimizeTabOptimizeReportPage.oneUserTaskInstance,
        optimizeTab,
      );
      await expect(
        optimizeTabOptimizeReportPage.oneUserTaskInstance,
      ).toBeVisible({timeout: 90000});
    });
  });

  test('Most Common REST Connector User Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    connectorSettingsPage,
  }) => {
    test.slow();
    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 20000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Open Web Modeler Project and Create a BPMN Diagram Template', async () => {
      await modelerHomePage.openConnectorsProjectFolder('Smoke Test Project');
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with REST Connector with Basic Auth and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName(
        'REST_Connector_Basic_Auth_Process',
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickRestConnectorOption();
      await connectorSettingsPage.selectAuthenticationType('basic');
      await expect(connectorSettingsPage.usernameInput).toBeVisible({
        timeout: 60000,
      });
      await connectorSettingsPage.clickUsernameInput();
      await connectorSettingsPage.fillUsernameInput('username');
      await connectorSettingsPage.clickPasswordInput();
      await connectorSettingsPage.fillPasswordInput('password');
      await connectorSettingsPage.selectMethodType('POST');
      await connectorSettingsPage.clickUrlInput();
      await connectorSettingsPage.fillUrlInput(
        'https://camunda.proxy.beeceptor.com/pre-prod/basic-auth-test',
      );
      await expect(connectorSettingsPage.resultVariableInput).toBeVisible({
        timeout: 60000,
      });
      await connectorSettingsPage.clickResultVariableInput();
      await connectorSettingsPage.fillResultVariableInput('result');
      await connectorSettingsPage.clickResultExpressionInput();
      await connectorSettingsPage.fillResultExpressionInput(
        '{message:response.body.message}',
      );

      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(10000);
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();

      await performBasicAuthPostRequest(
        'https://camunda.proxy.beeceptor.com/pre-prod/basic-auth-test',
        'username',
        'password',
      );
    });

    await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();

      const operateTab = await page.waitForEvent('popup', {timeout: 60000});
      const operateTabProcessInstancePage = new OperateProcessInstancePage(
        operateTab,
      );

      await expect(operateTabProcessInstancePage.completedIcon).toBeVisible({
        timeout: 1200000,
      });

      await operateTab.reload();
      await expect(operateTab.getByTestId('variables-list')).toBeVisible({
        timeout: 30000,
      });

      await expect(
        (
          await operateTabProcessInstancePage.connectorResultVariableName(
            'message',
          )
        ).isVisible(),
      ).toBeTruthy();

      await expect(
        operateTab
          .getByTestId('variable-message')
          .getByText('"Message from Mock!"'),
      ).toBeVisible({timeout: 60000});
    });
  });
});
