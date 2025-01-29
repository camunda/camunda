import {expect} from '@playwright/test';
import {test} from '@fixtures/8.3';
import {
  captureScreenshot,
  captureFailureVideo,
  generateRandomStringAsync,
} from '@setup';
import {AppsPage} from '@pages/8.3/AppsPage';
import {OptimizeCollectionsPage} from '@pages/8.3/OptimizeCollectionsPage';
import {OperateProcessInstancePage} from '@pages/8.3/OperateProcessInstancePage';
import {OptimizeHomePage} from '@pages/8.3/OptimizeHomePage';
import {HomePage} from '@pages/8.3/HomePage';
import {ModelerCreatePage} from '@pages/8.3/ModelerCreatePage';
import {ModelerHomePage} from '@pages/8.3/ModelerHomePage';
import {OptimizeReportPage} from '@pages/8.3/OptimizeReportPage';
import {OptimizeDashboardPage} from '@pages/8.3/OptimizeDashboardPage';

test.describe('Optimize User Flow Tests', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/');
    await loginPage.login();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('User Task User Flow', async ({
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
        timeout: 60000,
      });
      await modelerHomePage.clickCreateNewProjectButton();
      await expect(modelerHomePage.projectNameInput).toBeVisible({
        timeout: 30000,
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
      await modelerCreatePage.enterDiagramName('User Task Diagram');
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
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
      const operateTab = await page.waitForEvent('popup', {timeout: 90000});
      const operateTabAppsPage = new AppsPage(operateTab);
      const operateTabHomePage = new HomePage(operateTab);
      const operateTabOperateProcessInstancePage =
        new OperateProcessInstancePage(operateTab);

      await expect(operateTabOperateProcessInstancePage.activeIcon).toBeVisible(
        {timeout: 240000},
      );
      await operateTabHomePage.clickCamundaApps();
      await expect(operateTabAppsPage.optimizeLink).toBeVisible({
        timeout: 30000,
      });
      await operateTabAppsPage.clickOptimizeLink();

      const optimizeTab = await operateTab.waitForEvent('popup', {
        timeout: 60000,
      });
      const optimizeTabOptimizeCollectionsPage = new OptimizeCollectionsPage(
        optimizeTab,
      );
      const optimizeTabOptimizeReportPage = new OptimizeReportPage(optimizeTab);
      const optimizeTabHomePage = new HomePage(optimizeTab);
      const optimizeTabAppsPage = new AppsPage(optimizeTab);
      const modelerTabModelerCreatePage = new ModelerCreatePage(optimizeTab);
      const modelerTabModelerHomePage = new ModelerHomePage(optimizeTab);
      const optimizeHomePage = new OptimizeHomePage(optimizeTab);

      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(120000);

      await optimizeTab.reload();
      await optimizeTabOptimizeCollectionsPage.clickCreateNewButton();
      await optimizeTabOptimizeCollectionsPage.clickReportOption();
      await optimizeTabOptimizeReportPage.clickProcessSelectionButton();
      await optimizeTabOptimizeReportPage.clickUserTaskProcess(
        'User Task Diagram',
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

      await optimizeTabOptimizeReportPage.userTaskAssertion(
        optimizeTabOptimizeReportPage.oneUserTaskInstance,
      );
      await optimizeTabHomePage.clickCamundaApps();
      await optimizeTabAppsPage.clickModelerLink();
      await expect(modelerTabModelerHomePage.optimizeProjectFolder).toBeVisible(
        {timeout: 60000},
      );
      await modelerTabModelerHomePage.clickOptimizeProjectFolder();
      await expect(
        modelerTabModelerHomePage.optimizeUserTaskFlowDiagram,
      ).toBeVisible({
        timeout: 90000,
      });
      await modelerTabModelerHomePage.clickOptimizeUserTaskFlowDiagram();
      await expect(
        modelerTabModelerCreatePage.startInstanceMainButton,
      ).toBeVisible({timeout: 30000});
      await modelerTabModelerCreatePage.clickStartInstanceMainButton();
      await expect(optimizeTab.getByText('Healthy', {exact: true})).toBeVisible(
        {
          timeout: 120000,
        },
      );
      await modelerTabModelerCreatePage.clickStartInstanceSubButton();
      await expect(
        modelerTabModelerCreatePage.viewProcessInstanceLink,
      ).toBeVisible({timeout: 60000});
      await modelerTabModelerCreatePage.clickViewProcessInstanceLink();
      const operateTabPromise = optimizeTab.waitForEvent('popup', {
        timeout: 60000,
      });
      const newOperateTab = await operateTabPromise;

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
      await expect(newOperateTabAppsPage.operateLink).toBeVisible({
        timeout: 30000,
      });
      await newOperateTabAppsPage.clickOptimizeLink();

      const newOptimizeTab = await newOperateTab.waitForEvent('popup', {
        timeout: 60000,
      });
      const newOptimizeTabOptimizeCollectionsPage = new OptimizeCollectionsPage(
        newOptimizeTab,
      );
      const newOptimizeTabOptimizeReportPage = new OptimizeReportPage(
        newOptimizeTab,
      );
      const newOptimizeHomePage = new OptimizeHomePage(newOptimizeTab);

      await newOptimizeHomePage.clickCollectionsLink();
      await expect(newOptimizeTab.getByText(reportName)).toBeVisible();
      await newOptimizeTabOptimizeCollectionsPage.clickMostRecentProcessReport();
      await newOptimizeTabOptimizeReportPage.waitUntilLocatorIsVisible(
        newOptimizeTabOptimizeReportPage.twoUserTaskInstance,
        newOptimizeTab,
      );
      await expect(
        newOptimizeTabOptimizeReportPage.twoUserTaskInstance,
      ).toBeVisible();
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
        timeout: 60000,
      });
      await modelerHomePage.clickOptimizeUserTaskFlowDiagram();
    });

    await test.step('Update BPMN Diagram Name and ID and Deploy Process', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickNameInput();
      await modelerCreatePage.fillNamedInput(processName);
      await modelerCreatePage.clickProcessIdInput();
      await modelerCreatePage.fillProcessIdInput(processId);
      const sleep = (ms: number | undefined) =>
        new Promise((r) => setTimeout(r, ms));
      await sleep(20000);
      await expect(modelerCreatePage.processIdInput).toHaveValue(processId, {
        timeout: 20000,
      });
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

      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
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
      await optimizeCollectionsPage.clickMostRecentProcessReport();

      await page.reload();
      await expect(
        page.getByText('Displaying data from 0 instances.'),
      ).toBeVisible({timeout: 30000});
      await homePage.clickCamundaApps();
      await appsPage.clickModelerLink();

      await expect(modelerHomePage.optimizeProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickOptimizeProjectFolder();
      await expect(modelerHomePage.optimizeUserTaskFlowDiagram).toBeVisible({
        timeout: 120000,
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
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();

      const newOperateTab = await page.waitForEvent('popup');
      const newOperateTabAppsPage = new AppsPage(newOperateTab);
      const newOperateTabHomePage = new HomePage(newOperateTab);
      const newOperateTabOperateProcessInstancePage =
        new OperateProcessInstancePage(newOperateTab);

      await expect(
        newOperateTabOperateProcessInstancePage.diagramSpinner,
      ).not.toBeVisible({
        timeout: 120000,
      });

      await expect(
        newOperateTabOperateProcessInstancePage.activeIcon,
      ).toBeVisible({timeout: 180000});

      await newOperateTabHomePage.clickCamundaApps();
      await expect(newOperateTabAppsPage.optimizeLink).toBeVisible({
        timeout: 30000,
      });
      await newOperateTabAppsPage.clickOptimizeLink();

      const newOptimizeTab = await newOperateTab.waitForEvent('popup', {
        timeout: 30000,
      });
      const newOptimizeTabOptimizeCollectionsPage = new OptimizeCollectionsPage(
        newOptimizeTab,
      );
      const newOptimizeHomePage = new OptimizeHomePage(newOptimizeTab);

      await sleep(30000);
      await newOptimizeTab.reload();
      await newOptimizeHomePage.clickCollectionsLink();
      await expect(newOptimizeTab.getByText(reportName)).toBeVisible();
      await newOptimizeTabOptimizeCollectionsPage.clickMostRecentProcessReport();
      await expect(
        newOptimizeTab.getByText('Displaying data from 1 instance.'),
      ).toBeVisible({timeout: 180000});
    });
  });

  test('Process Import Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
  }) => {
    const processName = await generateRandomStringAsync(6);
    const processId = await generateRandomStringAsync(6);

    await test.step('Navigate to Web Modeler', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
    });

    await test.step('Open Optimize Project Folder & User Task Flow BPMN Diagram', async () => {
      await expect(modelerHomePage.optimizeProjectFolder).toBeVisible({
        timeout: 180000,
      });
      await modelerHomePage.clickOptimizeProjectFolder();
      await expect(modelerHomePage.optimizeUserTaskFlowDiagram).toBeVisible({
        timeout: 90000,
      });
      await modelerHomePage.clickOptimizeUserTaskFlowDiagram();
    });

    await test.step('Update BPMN Diagram Name and ID and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 90000,
      });
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickProcessIdInput();
      await modelerCreatePage.fillProcessIdInput(processId);
      const sleep = (ms: number | undefined) =>
        new Promise((r) => setTimeout(r, ms));
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
        timeout: 90000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();
      const operateTab = await page.waitForEvent('popup', {timeout: 60000});
      const operateTabAppsPage = new AppsPage(operateTab);
      const operateTabOperateProcessInstancePage =
        new OperateProcessInstancePage(operateTab);

      await expect(operateTabOperateProcessInstancePage.activeIcon).toBeVisible(
        {timeout: 120000},
      );
      await operateTabAppsPage.clickCamundaAppsLink();
      await expect(operateTabAppsPage.optimizeLink).toBeVisible();
      await operateTabAppsPage.clickOptimizeLink();

      const optimizeTab = await operateTab.waitForEvent('popup', {
        timeout: 60000,
      });
      const optimizeTabOptimizeHomePage = new OptimizeHomePage(optimizeTab);
      const optimizeDashboardPage = new OptimizeDashboardPage(optimizeTab);

      await optimizeTabOptimizeHomePage.clickDashboardLink();
      const sleep = (ms: number | undefined) =>
        new Promise((r) => setTimeout(r, ms));
      await sleep(120000);

      await optimizeDashboardPage.processLinkAssertion(processName, 3);
      await optimizeDashboardPage.processOwnerNameAssertion(processName);
    });
  });
});
