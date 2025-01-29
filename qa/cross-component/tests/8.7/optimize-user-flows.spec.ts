import {expect} from '@playwright/test';
import {test} from '@fixtures/8.7';
import {
  captureScreenshot,
  captureFailureVideo,
  generateRandomStringAsync,
} from '@setup';
import {AppsPage} from '@pages/8.7/AppsPage';
import {OptimizeCollectionsPage} from '@pages/8.7/OptimizeCollectionsPage';
import {OperateProcessInstancePage} from '@pages/8.7/OperateProcessInstancePage';
import {OptimizeHomePage} from '@pages/8.7/OptimizeHomePage';
import {HomePage} from '@pages/8.7/HomePage';
import {ModelerCreatePage} from '@pages/8.7/ModelerCreatePage';
import {ModelerHomePage} from '@pages/8.7/ModelerHomePage';
import {OptimizeReportPage} from '@pages/8.7/OptimizeReportPage';
import {OptimizeDashboardPage} from '@pages/8.7/OptimizeDashboardPage';
import {sleep} from '../../utils/sleep';
import {assertLocatorVisibleWithRetry} from '@pages/8.7/UtilitiesPage';

test.describe('Optimize User Flow Tests', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/');
    await loginPage.login();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Job Worker User Task User Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
  }) => {
    test.slow();
    const reportName = await generateRandomStringAsync(5);
    const diagramName = 'Optimize Job Worker User Task Diagram' + reportName;

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
        timeout: 60000,
      });
      await modelerHomePage.clickCreateNewProjectButton();
      await expect(modelerHomePage.projectNameInput).toBeVisible({
        timeout: 90000,
      });
      await modelerHomePage.enterNewProjectName('Optimize Project');
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
      await modelerCreatePage.enterDiagramName(diagramName);
      await sleep(10000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.chooseImplementationOption('jobWorker');
      await modelerCreatePage.assertImplementationOption('jobWorker');
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(20000);
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate, Create User Task Report in Optimize, Start Another Process Instance in Modeler & Assert the Report Updates', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();
      const operateTabPromise = page.waitForEvent('popup', {timeout: 60000});
      const operateTab = await operateTabPromise;
      const operateTabAppsPage = new AppsPage(operateTab);
      const operateTabHomePage = new HomePage(operateTab);
      const operateTabOperateProcessInstancePage =
        new OperateProcessInstancePage(operateTab);

      await expect(operateTabOperateProcessInstancePage.activeIcon).toBeVisible(
        {timeout: 120000},
      );
      await operateTabHomePage.clickCamundaApps();
      await operateTabAppsPage.clickOptimizeLink();

      const optimizeTabOptimizeCollectionsPage = new OptimizeCollectionsPage(
        operateTab,
      );
      const optimizeTabOptimizeReportPage = new OptimizeReportPage(operateTab);
      const optimizeTabHomePage = new HomePage(operateTab);
      const optimizeTabAppsPage = new AppsPage(operateTab);
      const modelerTabModelerCreatePage = new ModelerCreatePage(operateTab);
      const modelerTabModelerHomePage = new ModelerHomePage(operateTab);
      const optimizeHomePage = new OptimizeHomePage(operateTab);
      await sleep(120000);

      await operateTab.reload();
      await optimizeTabOptimizeCollectionsPage.clickCreateNewButton();
      await optimizeTabOptimizeCollectionsPage.clickReportOption();
      await optimizeTabOptimizeReportPage.clickProcessSelectionButton();
      await optimizeTabOptimizeReportPage.clickUserTaskProcess(diagramName);
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
        timeout: 90000,
      });
      await optimizeTabOptimizeReportPage.clickTableOption();
      await optimizeTabOptimizeReportPage.clickReportName();
      await optimizeTabOptimizeReportPage.clearReportName();
      await optimizeTabOptimizeReportPage.fillReportName(reportName);
      await optimizeTabOptimizeReportPage.clickSaveButton();
      await optimizeHomePage.clickCollectionsLink();
      await expect(operateTab.getByText(reportName)).toBeVisible({
        timeout: 60000,
      });
      await optimizeTabOptimizeCollectionsPage.clickMostRecentProcessReport(
        reportName,
      );
      await assertLocatorVisibleWithRetry(
        operateTab,
        optimizeTabOptimizeReportPage.oneUserTaskInstance,
        'One user task count',
        90000,
      );

      await optimizeTabHomePage.clickCamundaApps();
      await optimizeTabAppsPage.clickModelerLink();
      await expect(modelerTabModelerHomePage.optimizeProjectFolder).toBeVisible(
        {timeout: 120000},
      );
      await modelerTabModelerHomePage.clickOptimizeProjectFolder();
      await modelerTabModelerHomePage.clickProcessDiagram(diagramName);
      await expect(
        modelerTabModelerCreatePage.startInstanceMainButton,
      ).toBeVisible({timeout: 90000});
      await modelerTabModelerCreatePage.clickStartInstanceMainButton();
      await expect(operateTab.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerTabModelerCreatePage.clickStartInstanceSubButton();
      await expect(
        modelerTabModelerCreatePage.viewProcessInstanceLink,
      ).toBeVisible({timeout: 120000});
      await modelerTabModelerCreatePage.clickViewProcessInstanceLink();
      const newOperateTabPromise = operateTab.waitForEvent('popup', {
        timeout: 60000,
      });
      const newOperateTab = await newOperateTabPromise;
      const newOperateTabAppsPage = new AppsPage(newOperateTab);
      const newOperateTabHomePage = new HomePage(newOperateTab);
      const newOperateTabOperateProcessInstancePage =
        new OperateProcessInstancePage(newOperateTab);

      await expect(
        newOperateTabOperateProcessInstancePage.diagramSpinner,
      ).not.toBeVisible({
        timeout: 60000,
      });

      await expect(
        newOperateTabOperateProcessInstancePage.activeIcon,
      ).toBeVisible({timeout: 60000});

      await newOperateTabHomePage.clickCamundaApps();
      await newOperateTabAppsPage.clickOptimizeLink();

      const newOptimizeTabOptimizeCollectionsPage = new OptimizeCollectionsPage(
        newOperateTab,
      );
      const newOptimizeTabOptimizeReportPage = new OptimizeReportPage(
        newOperateTab,
      );
      const newOptimizeHomePage = new OptimizeHomePage(newOperateTab);

      await newOptimizeHomePage.clickCollectionsLink();
      await expect(newOperateTab.getByText(reportName)).toBeVisible();
      await newOptimizeTabOptimizeCollectionsPage.clickMostRecentProcessReport(
        reportName,
      );

      await assertLocatorVisibleWithRetry(
        newOperateTab,
        newOptimizeTabOptimizeReportPage.twoUserTaskInstance,
        'Two user tasks count',
        90000,
      );
      await expect(
        newOptimizeTabOptimizeReportPage.oneUserTaskInstance,
      ).not.toBeVisible();
    });
  });

  test('New Instances Updated Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    optimizeCollectionsPage,
    optimizeReportPage,
    optimizeHomePage,
  }) => {
    test.slow();
    const reportName = await generateRandomStringAsync(5);
    const processName = await generateRandomStringAsync(6);
    const processId = await generateRandomStringAsync(6);

    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 120000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 200000,
      });
    });

    await test.step('Open Optimize Project Folder & User Task Flow BPMN Diagram', async () => {
      await expect(modelerHomePage.optimizeProjectFolder).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickOptimizeProjectFolder();
      await expect(modelerHomePage.optimizeUserTaskFlowDiagram).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickOptimizeUserTaskFlowDiagram();
    });

    await test.step('Update BPMN Diagram Name and ID and Deploy Process', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 180000,
      });
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickNameInput();
      await modelerCreatePage.fillNamedInput(processName);
      await modelerCreatePage.clickProcessIdInput();
      await modelerCreatePage.fillProcessIdInput(processId);
      await modelerCreatePage.clickCanvas();
      await expect(modelerCreatePage.processIdInput).toHaveValue(processId, {
        timeout: 20000,
      });
      await modelerCreatePage.clickCanvas();
      await modelerCreatePage.clickSecondElement();
      await modelerCreatePage.chooseImplementationOption('zeebeUserTask');
      await modelerCreatePage.assertImplementationOption('zeebeUserTask');
      await sleep(20000);

      await expect(modelerCreatePage.deployMainButton).toBeVisible({
        timeout: 30000,
      });
      await modelerCreatePage.clickDeployMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickDeploySubButton();
    });

    await test.step('Create Process Instance Count Report in Optimize, Start A Process Instance in Modeler & Assert the Report Updates', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickOptimizeFilter();
      await sleep(120000);

      await page.reload();
      await optimizeCollectionsPage.clickCreateNewButton();
      await optimizeCollectionsPage.clickReportOption();
      await optimizeReportPage.clickProcessSelectionButton();
      await optimizeReportPage.clickUserTaskProcess(processName);
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
      await expect(page.getByText(reportName)).toBeVisible({
        timeout: 20000,
      });
      await optimizeCollectionsPage.clickMostRecentProcessReport(reportName);

      await page.reload();
      await expect(
        page.getByText('Displaying data from 0 instances.'),
      ).toBeVisible({timeout: 90000});
      await homePage.clickCamundaApps();
      await appsPage.clickModelerLink();

      await expect(modelerHomePage.optimizeProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickOptimizeProjectFolder();
      await expect(modelerHomePage.optimizeUserTaskFlowDiagram).toBeVisible({
        timeout: 20000,
      });
      await modelerHomePage.clickOptimizeUserTaskFlowDiagram();
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();
      await modelerCreatePage.clickViewProcessInstanceLink();

      const newOperateTab = await page.waitForEvent('popup');
      const newOperateTabAppsPage = new AppsPage(newOperateTab);
      const newOperateTabHomePage = new HomePage(newOperateTab);
      const newOperateTabOperateProcessInstancePage =
        new OperateProcessInstancePage(newOperateTab);

      await expect(
        newOperateTabOperateProcessInstancePage.diagramSpinner,
      ).not.toBeVisible({
        timeout: 60000,
      });

      await expect(
        newOperateTabOperateProcessInstancePage.activeIcon,
      ).toBeVisible({timeout: 60000});

      await newOperateTabHomePage.clickCamundaApps();
      await newOperateTabAppsPage.clickOptimizeLink();

      const newOptimizeTabOptimizeCollectionsPage = new OptimizeCollectionsPage(
        newOperateTab,
      );
      const newOptimizeHomePage = new OptimizeHomePage(newOperateTab);

      await sleep(30000);
      await newOperateTab.reload();
      await newOptimizeHomePage.clickCollectionsLink();
      await expect(newOperateTab.getByText(reportName)).toBeVisible();
      await newOptimizeTabOptimizeCollectionsPage.clickMostRecentProcessReport(
        reportName,
      );
      await expect(
        newOperateTab.getByText('Displaying data from 1 instance.'),
      ).toBeVisible({timeout: 90000});
    });
  });

  test('Process Import Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
  }) => {
    test.slow();
    const processName = await generateRandomStringAsync(6);
    const processId = await generateRandomStringAsync(6);

    await test.step('Navigate to Web Modeler', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
    });

    await test.step('Open Optimize Project Folder & User Task Flow BPMN Diagram', async () => {
      await expect(modelerHomePage.optimizeProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickOptimizeProjectFolder();
      await expect(modelerHomePage.optimizeUserTaskFlowDiagram).toBeVisible({
        timeout: 90000,
      });
      await modelerHomePage.clickOptimizeUserTaskFlowDiagram();
    });

    await test.step('Update BPMN Diagram Name and ID and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 180000,
      });
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickProcessIdInput();
      await modelerCreatePage.fillProcessIdInput(processId);
      await modelerCreatePage.clickCanvas();
      await sleep(20000);
      await modelerCreatePage.clickNameInput();
      await modelerCreatePage.fillNamedInput(processName);
      await expect(modelerCreatePage.processIdInput).toHaveValue(processId, {
        timeout: 20000,
      });
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 30000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate and Assert Process has been successfully imported in Optimize', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();
      const operateTab = await page.waitForEvent('popup');
      const operateTabAppsPage = new AppsPage(operateTab);
      const operateTabOperateProcessInstancePage =
        new OperateProcessInstancePage(operateTab);
      const operateTabOptimizeDashboardPage = new OptimizeDashboardPage(
        operateTab,
      );

      await expect(operateTabOperateProcessInstancePage.activeIcon).toBeVisible(
        {timeout: 120000},
      );
      await operateTabAppsPage.clickCamundaAppsLink();
      await operateTabAppsPage.clickOptimizeLink();
      const optimizeTabOptimizeHomePage = new OptimizeHomePage(operateTab);
      await optimizeTabOptimizeHomePage.clickDashboardLink();
      await sleep(120000);

      await operateTabOptimizeDashboardPage.processLinkAssertion(
        processName,
        6,
      );
      await operateTabOptimizeDashboardPage.processOwnerNameAssertion(
        processName,
      );
    });
  });
});
