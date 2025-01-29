import {expect} from '@playwright/test';
import {test} from '@fixtures/SM-8.4';
import {
  captureScreenshot,
  captureFailureVideo,
  generateRandomStringAsync,
} from '@setup';

test.describe('Optimize User Flow Tests', () => {
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

  test('Process Import Flow', async ({
    page,
    operateHomePage,
    modelerHomePage,
    navigationPage,
    modelerCreatePage,
    operateProcessesPage,
    operateProcessInstancePage,
    optimizeHomePage,
  }) => {
    test.slow();

    await test.step('Open Optimize Project Folder & User Task Flow BPMN Diagram', async () => {
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 180000,
      });
      await modelerHomePage.clickCreateNewProjectButton();
      await expect(modelerHomePage.projectNameInput).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.enterNewProjectName('Optimize Project');
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Update BPMN Diagram Name and ID and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 180000,
      });
      await modelerCreatePage.enterDiagramName('Optimize User Task Flow');
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await sleep(10000);
      await modelerCreatePage.clickAppendEndEventButton();
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await modelerCreatePage.clickStartInstanceSubButton();
      await modelerCreatePage.instanceStartedAssertion();
    });

    await test.step('View Process Instance in Operate and Assert Process has been successfully imported in Optimize', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await navigationPage.goToOperate();
      await operateHomePage.clickProcessesTab();
      await page.reload();
      await operateProcessesPage.clickProcessInstanceLink(
        'Optimize User Task Flow',
      );
      await operateProcessInstancePage.activeIconAssertion();

      await navigationPage.goToOptimize();
      await optimizeHomePage.clickDashboardLink();
      await sleep(60000);
      await page.reload();
      await expect(
        page.getByRole('link', {name: 'Optimize User Task Flow'}).first(),
      ).toBeVisible({
        timeout: 180000,
      });
      await expect(
        page.getByRole('link', {name: 'Optimize User Task Flow'}).first(),
      ).toContainText('QA Camunda', {timeout: 90000});
    });
  });

  test('User Task User Flow', async ({
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
  }) => {
    test.slow();
    const reportName = await generateRandomStringAsync(5);
    const processId = await generateRandomStringAsync(6);

    await test.step('Open Optimize Project Folder & User Task Flow BPMN Diagram', async () => {
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
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
        timeout: 120000,
      });
      await modelerCreatePage.clickDiagramBreadcrumb();
      await modelerCreatePage.clickEditDiagramNameButton();
      await modelerCreatePage.enterDiagramName(
        'Optimize User Task Flow Updated',
      );
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickNameInput();
      await modelerCreatePage.fillNameInput('Optimize User Task Flow Updated');
      await modelerCreatePage.clickIdInput();
      await modelerCreatePage.fillIdInput(processId);
      const sleep = (ms: number | undefined) =>
        new Promise((r) => setTimeout(r, ms));
      await expect(modelerCreatePage.idInput).toHaveValue(processId, {
        timeout: 20000,
      });
      await sleep(20000);
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 90000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await sleep(10000);
      await modelerCreatePage.clickStartInstanceSubButton();
      await modelerCreatePage.instanceStartedAssertion();
    });

    await test.step('View Process Instance in Operate, Create User Task Report in Optimize, Start Another Process Instance in Modeler & Assert the Report Updates', async () => {
      await navigationPage.goToOperate();
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await page.reload();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await page.reload();
      await operateProcessesPage.clickProcessInstanceLink(
        'Optimize User Task Flow Updated',
        processId,
      );
      await operateProcessInstancePage.activeIconAssertion();

      await navigationPage.goToOptimize();
      await sleep(120000);

      await page.reload();
      await optimizeHomePage.clickCollectionsLink();
      await optimizeCollectionsPage.clickCreateNewButton();
      await optimizeCollectionsPage.clickReportOption();
      await optimizeReportPage.clickProcessSelectionButton();
      await optimizeReportPage.clickUserTaskProcess(
        'Optimize User Task Flow Updated',
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

      await navigationPage.goToModeler();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 180000,
      });
      await expect(modelerHomePage.optimizeProjectFolder).toBeVisible({
        timeout: 180000,
      });
      await modelerHomePage.clickOptimizeProjectFolder();
      await expect(modelerHomePage.optimizeUserTaskFlowDiagram).toBeVisible({
        timeout: 20000,
      });
      await modelerHomePage.clickOptimizeUserTaskFlowDiagram();
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 30000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await modelerCreatePage.clickStartInstanceSubButton();
      await sleep(10000);
      await navigationPage.goToOperate();
      await sleep(60000);
      await page.reload();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 180000});
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink(
        'Optimize User Task Flow Updated',
        processId,
      );
      await operateProcessInstancePage.activeIconAssertion();

      await navigationPage.goToOptimize();
      await sleep(120000);

      await optimizeHomePage.clickCollectionsLink();
      await expect(page.getByText(reportName)).toBeVisible({timeout: 90000});
      await optimizeCollectionsPage.clickMostRecentProcessReport(reportName);
      await optimizeReportPage.waitUntilLocatorIsVisible(
        optimizeReportPage.twoUserTaskInstance,
        page,
      );
      await expect(optimizeReportPage.twoUserTaskInstance).toBeVisible({
        timeout: 60000,
      });
      await expect(optimizeReportPage.oneUserTaskInstance).not.toBeVisible();
    });
  });

  // Test will be skipped until Flaky CI behaviour is investigated
  test.skip('New Instances Updated Flow', async ({
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
  }) => {
    test.slow();
    const reportName = await generateRandomStringAsync(5);
    const processId = await generateRandomStringAsync(6);

    await test.step('Open Optimize Project Folder & User Task Flow BPMN Diagram', async () => {
      await navigationPage.goToModeler();
      await expect(modelerHomePage.optimizeProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickOptimizeProjectFolder();
      await expect(modelerHomePage.optimizeUserTaskFlowDiagram).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickOptimizeUserTaskFlowDiagram();
    });

    await test.step('Update BPMN Diagram Name and ID and Deploy Process', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickIdInput();
      await modelerCreatePage.fillIdInput(processId);
      const sleep = (ms: number | undefined) =>
        new Promise((r) => setTimeout(r, ms));
      await expect(modelerCreatePage.idInput).toHaveValue(processId, {
        timeout: 20000,
      });
      await sleep(20000);
      await expect(modelerCreatePage.deployMainButton).toBeVisible({
        timeout: 30000,
      });

      await modelerCreatePage.clickDeployMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await sleep(20000);
      await modelerCreatePage.clickDeploySubButton();
    });

    await test.step('Create Process Instance Count Report in Optimize, Start A Process Instance in Modeler & Assert the Report Updates', async () => {
      await navigationPage.goToOptimize();
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(120000);

      await page.reload();
      await optimizeHomePage.clickCollectionsLink();
      await optimizeCollectionsPage.clickCreateNewButton();
      await optimizeCollectionsPage.clickReportOption();
      await optimizeReportPage.clickProcessSelectionButton();
      await optimizeReportPage.clickUserTaskProcess(processId);
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
      await optimizeCollectionsPage.clickMostRecentProcessReport(reportName);

      await page.reload();
      await expect(
        page.getByText('Displaying data from 0 instances.'),
      ).toBeVisible({timeout: 30000});
      await navigationPage.goToModeler();

      await expect(modelerHomePage.optimizeProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickOptimizeProjectFolder();
      await expect(modelerHomePage.optimizeUserTaskFlowDiagram).toBeVisible({
        timeout: 20000,
      });
      await modelerHomePage.clickOptimizeUserTaskFlowDiagram();
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 60000,
      });
      await expect(modelerCreatePage.idInput).toHaveValue(processId, {
        timeout: 20000,
      });
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await modelerCreatePage.clickStartInstanceSubButton();
      await sleep(20000);
      await navigationPage.goToOperate();
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessInstanceLink(processId);
      await expect(operateProcessInstancePage.activeIcon).toBeVisible({
        timeout: 1200000,
      });

      await navigationPage.goToOptimize();

      await sleep(30000);
      await page.reload();
      await optimizeHomePage.clickCollectionsLink();
      await optimizeCollectionsPage.clickMostRecentProcessReport(reportName);
      await expect(
        page.getByText('Displaying data from 1 instance.'),
      ).toBeVisible({timeout: 60000});
    });
  });
});
