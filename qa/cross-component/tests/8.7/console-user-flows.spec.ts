import {expect} from '@playwright/test';
import {test} from '@fixtures/8.7';
import {
  captureScreenshot,
  captureFailureVideo,
  generateRandomStringAsync,
} from '@setup';
import {OperateProcessInstancePage} from '@pages/8.6/OperateProcessInstancePage';

test.describe('Console User Flow Tests', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/');
    await loginPage.login();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Console Most Common Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    clusterPage,
    clusterDetailsPage,
  }) => {
    const clientName = await generateRandomStringAsync(6);

    await test.step('Add API Client to Cluster', async () => {
      await expect(page.getByText('Redirecting')).not.toBeVisible({
        timeout: 60000,
      });
      await expect(homePage.clusterTab).toBeVisible({timeout: 30000});
      const sleep = (ms: number | undefined) =>
        new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await homePage.clickClusters();
      await expect(page.getByText('Redirecting')).not.toBeVisible({
        timeout: 60000,
      });
      await sleep(10000);
      await clusterPage.clickTestClusterLink();
      await expect(clusterDetailsPage.apiTab).toBeVisible({timeout: 60000});
      await clusterDetailsPage.clickAPITab();
      await clusterDetailsPage.deleteAPIClientsIfExist();
      await clusterDetailsPage.clickCreateFirstClientButton();
      await expect(
        clusterDetailsPage.createClientCredentialsDialog,
      ).toBeVisible({timeout: 60000});
      await clusterDetailsPage.clickClientNameTextbox();
      await clusterDetailsPage.fillClientNameTextbox(clientName);
      await clusterDetailsPage.checkSecretsCheckbox();
      await clusterDetailsPage.clickCreateButton();
      await expect(clusterDetailsPage.clientCredentialsDialog).toBeVisible({
        timeout: 20000,
      });
      await expect(
        clusterDetailsPage.clientCredentialsDialog
          .getByText('The Client Secret will not be shown again.')
          .first(),
      ).toBeVisible({timeout: 60000});
      await clusterDetailsPage.clickCloseModalButton();
    });

    await test.step('Navigate to Web Modeler', async () => {
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
      await modelerHomePage.enterNewProjectName('Console Test Project');
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with User Task and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName('Console_Test_Process');
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate, and assert it completes', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();
      const operateTabPromise = page.waitForEvent('popup', {timeout: 60000});
      const operateTab = await operateTabPromise;
      const operateTabProcessInstancePage = new OperateProcessInstancePage(
        operateTab,
      );

      await page.reload();
      await expect(operateTabProcessInstancePage.completedIcon).toBeVisible({
        timeout: 180000,
      });
    });
  });
});
