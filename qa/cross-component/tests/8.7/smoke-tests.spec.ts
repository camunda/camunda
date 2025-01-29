import {expect} from '@playwright/test';
import {test} from '@fixtures/8.7';
import {
  captureScreenshot,
  captureFailureVideo,
  generateRandomStringAsync,
  performBasicAuthPostRequest,
} from '@setup';
import {TaskPanelPage} from '@pages/8.7/TaskPanelPage';
import {TaskDetailsPage} from '@pages/8.7/TaskDetailsPage';
import {OperateHomePage} from '@pages/8.7/OperateHomePage';
import {AppsPage} from '@pages/8.7/AppsPage';
import {OperateProcessesPage} from '@pages/8.7/OperateProcessesPage';
import {OperateProcessInstancePage} from '@pages/8.7/OperateProcessInstancePage';
import {HomePage} from '@pages/8.7/HomePage';
import {
  assertLocatorVisibleWithRetry,
  completeTaskWithRetry,
} from '@pages/8.7/UtilitiesPage';
import {OptimizeHomePage} from '@pages/8.7/OptimizeHomePage';
import {OptimizeCollectionsPage} from '@pages/8.7/OptimizeCollectionsPage';
import {OptimizeReportPage} from '@pages/8.7/OptimizeReportPage';
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
    const randomString = await generateRandomStringAsync(3);
    const formName = 'New form' + randomString;
    const processName = 'Zeebe_User_Task_Process' + randomString;
    const userTaskName = 'zeebeUserTaskWithForm' + randomString;
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

    await test.step('Create Form To Be Linked For Zeebe User Task', async () => {
      await modelerHomePage.openHTOProjectFolder('HTO Project');
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickFormOption();
      await modelerHomePage.enterFormName(formName);
      await sleep(10000);
    });

    await test.step('Add A BPMN Template To The Project', async () => {
      await modelerHomePage.clickProjectBreadcrumb();
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with Two Zeebe User Tasks and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName(processName);
      await sleep(10000);
      await modelerCreatePage.addParallelUserTasks(2, userTaskName);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton('parallelGateway');
      await modelerCreatePage.clickUserTask(`${userTaskName}2`);
      await modelerCreatePage.clickEmbedFormButton();
      await modelerCreatePage.clickForm(formName);
      await modelerCreatePage.clickEmbedButton();
      await sleep(10000);
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
      await expect(operateTabProcessInstancePage.activeIcon).toBeVisible({
        timeout: 1200000,
      });

      await operateHomePage.clickCamundaApps();
      await operateTabAppsPage.clickTasklistLink();
      await completeTaskWithRetry(
        operateTabTaskPanelPage,
        operateTabTaskDetailsPage,
        `${userTaskName}1`,
        'Medium',
      );
      await completeTaskWithRetry(
        operateTabTaskPanelPage,
        operateTabTaskDetailsPage,
        `${userTaskName}2`,
        'Medium',
      );
      await operateTabTaskPanelPage.filterBy('Completed');
      await operateTabTaskPanelPage.openTask(`${userTaskName}1`);
      await expect(
        operateTabTaskDetailsPage.detailsInfo.getByText(`${userTaskName}1`),
      ).toBeVisible({timeout: 60000});
      await operateTabTaskPanelPage.openTask(`${userTaskName}2`);
      await expect(
        operateTabTaskDetailsPage.detailsInfo.getByText(`${userTaskName}2`),
      ).toBeVisible({timeout: 60000});
      await operateHomePage.clickCamundaComponents();
      await operateTabAppsPage.clickOperateLink();

      await operateTabHomePage.clickProcessesTab();
      await sleep(10000);
      await operateTab.reload();
      await operateTabProcessesPage.clickProcessCompletedCheckbox();
      await operateTabProcessesPage.clickProcessInstanceLink(processName);
      await assertLocatorVisibleWithRetry(
        operateTab,
        operateTabProcessInstancePage.completedIcon,
        'completed icon in Operate',
        60000,
      );
      await operateTabAppsPage.clickCamundaAppsLink();
      await operateTabAppsPage.clickOptimizeLink();

      const optimizeTabOptimizeCollectionsPage = new OptimizeCollectionsPage(
        operateTab,
      );
      const optimizeTabOptimizeReportPage = new OptimizeReportPage(operateTab);
      const optimizeTabOptimizeHomePage = new OptimizeHomePage(operateTab);
      await expect(
        operateTab.getByRole('link', {
          name: processName,
        }),
      ).toBeVisible({timeout: 90000});
      await optimizeTabOptimizeHomePage.clickCollectionsLink();
      await optimizeTabOptimizeCollectionsPage.clickCreateNewButton();
      await optimizeTabOptimizeCollectionsPage.clickReportOption();
      await optimizeTabOptimizeReportPage.clickProcessSelectionButton();
      await optimizeTabOptimizeReportPage.clickUserTaskProcess(processName);
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
        optimizeTabOptimizeReportPage.oneUserTaskInstance.first(),
        operateTab,
      );
      await expect(
        optimizeTabOptimizeReportPage.oneUserTaskInstance,
      ).toHaveCount(2, {timeout: 120000});
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
      await modelerHomePage.openConnectorsProjectFolder('Connectors Project');
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
