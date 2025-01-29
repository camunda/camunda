import {expect} from '@playwright/test';
import {test} from '@fixtures/8.6';
import {
  captureScreenshot,
  captureFailureVideo,
  generateRandomStringAsync,
  performBasicAuthPostRequest,
} from '@setup';
import {TaskPanelPage} from '@pages/8.6/TaskPanelPage';
import {TaskDetailsPage} from '@pages/8.6/TaskDetailsPage';
import {OperateHomePage} from '@pages/8.6/OperateHomePage';
import {AppsPage} from '@pages/8.6/AppsPage';
import {OperateProcessesPage} from '@pages/8.6/OperateProcessesPage';
import {OperateProcessInstancePage} from '@pages/8.6/OperateProcessInstancePage';
import {HomePage} from '@pages/8.6/HomePage';
import {OptimizeHomePage} from '@pages/8.6/OptimizeHomePage';
import {OptimizeCollectionsPage} from '@pages/8.6/OptimizeCollectionsPage';
import {OptimizeReportPage} from '@pages/8.6/OptimizeReportPage';
import {sleep} from '../../utils/sleep';

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
    const processName = await generateRandomStringAsync(3);
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

    await test.step('Create a BPMN Diagram Template', async () => {
      await modelerHomePage.openHTOProjectFolder('Smoke Test Project');
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
        'Zeebe_User_Task_Process' + processName,
      );
      await sleep(10000);
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
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 180000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate, complete User Task in Tasklist & assert process complete in Operate and Assert Process has been successfully imported in Optimize', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();
      const operateTabPromise = page.waitForEvent('popup', {timeout: 60000});
      const operateTab = await operateTabPromise;
      const operateTabHomePage = new OperateHomePage(operateTab);
      const operateTabProcessesPage = new OperateProcessesPage(operateTab);
      const operateTabProcessInstancePage = new OperateProcessInstancePage(
        operateTab,
      );
      const operateTabAppsPage = new AppsPage(operateTab);
      const operateHomePage = new HomePage(operateTab);
      const operateTabTaskPanelPage = new TaskPanelPage(operateTab);
      const operateTabTaskDetailsPage = new TaskDetailsPage(operateTab);
      await operateTab.reload();
      await expect(operateTabProcessInstancePage.activeIcon).toBeVisible({
        timeout: 180000,
      });

      await operateHomePage.clickCamundaApps();
      await operateTabAppsPage.clickTasklistLink();

      await operateTabTaskPanelPage.openTask(
        'Zeebe_User_Task_Process' + processName,
      );
      await operateTabTaskDetailsPage.clickAssignToMeButton();
      await operateTabTaskDetailsPage.clickCompleteTaskButton();
      await operateTabTaskPanelPage.filterBy('Completed');
      await operateTabTaskPanelPage.openTask(
        'Zeebe_User_Task_Process' + processName,
      );
      await expect(
        operateTabTaskDetailsPage.detailsInfo.getByText(
          'Zeebe_User_Task_Process' + processName,
        ),
      ).toBeVisible({timeout: 60000});
      await operateHomePage.clickCamundaComponents();
      await operateTabAppsPage.clickOperateLink();

      await operateTabHomePage.clickProcessesTab();
      await sleep(10000);
      await operateTab.reload();
      await operateTabProcessesPage.clickProcessCompletedCheckbox();
      await operateTabProcessesPage.clickProcessInstanceLink(
        'Zeebe_User_Task_Process',
      );
      await expect(operateTabProcessInstancePage.completedIcon).toBeVisible({
        timeout: 120000,
      });
      await operateTabAppsPage.clickCamundaAppsLink();
      await operateTabAppsPage.clickOptimizeLink();

      const optimizeTabOptimizeCollectionsPage = new OptimizeCollectionsPage(
        operateTab,
      );
      const optimizeTabOptimizeReportPage = new OptimizeReportPage(operateTab);
      const optimizeTabOptimizeHomePage = new OptimizeHomePage(operateTab);
      await expect(
        operateTab.getByRole('link', {
          name: 'Zeebe_User_Task_Process' + processName,
        }),
      ).toBeVisible({timeout: 90000});
      await optimizeTabOptimizeHomePage.clickCollectionsLink();
      await optimizeTabOptimizeCollectionsPage.clickCreateNewButton();
      await optimizeTabOptimizeCollectionsPage.clickReportOption();
      await optimizeTabOptimizeReportPage.clickProcessSelectionButton();
      await optimizeTabOptimizeReportPage.clickUserTaskProcess(
        'Zeebe_User_Task_Process' + processName,
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
      await optimizeTabOptimizeHomePage.clickCollectionsLink();
      await expect(operateTab.getByText(reportName)).toBeVisible({
        timeout: 60000,
      });
      await optimizeTabOptimizeCollectionsPage.clickMostRecentProcessReport(
        reportName,
      );
      await optimizeTabOptimizeReportPage.waitUntilLocatorIsVisible(
        optimizeTabOptimizeReportPage.oneUserTaskInstance,
        operateTab,
      );
      await expect(
        optimizeTabOptimizeReportPage.oneUserTaskInstance,
      ).toBeVisible({timeout: 120000});
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

    await test.step('Open Connector Project and Create a BPMN Diagram Template', async () => {
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

      const operateTab = await page.waitForEvent('popup');
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
      ).toBeVisible({
        timeout: 60000,
      });
    });
  });
});
