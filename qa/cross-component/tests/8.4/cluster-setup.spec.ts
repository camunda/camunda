import {expect} from '@playwright/test';
import {test} from '@fixtures/8.4';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {sleep} from '../../utils/sleep';

test.describe('Cluster Setup Tests', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/');
    await loginPage.login();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Enable Alpha Features', async ({
    page,
    homePage,
    consoleOrganizationsPage,
  }) => {
    if (process.env.IS_PROD! === 'true') {
      test.skip(true, 'Skipping test because not required on PROD test org');
    }

    await homePage.clickOrganization();
    await expect(consoleOrganizationsPage.settingsTab).toBeVisible({
      timeout: 60000,
    });
    await consoleOrganizationsPage.clickSettingsTab();
    await consoleOrganizationsPage.optInToAlphaFeatures();
    await sleep(30000);

    await page.reload();
    await consoleOrganizationsPage.clickSettingsTab();
    await consoleOrganizationsPage.enableAlphaFeature('AI-powered features');
  });

  test('Create Default Cluster', async ({page, homePage, clusterPage}) => {
    await expect(page.getByText('Redirecting')).not.toBeVisible({
      timeout: 60000,
    });
    await expect(homePage.clusterTab).toBeVisible({timeout: 60000});
    await homePage.clickClusters();

    await expect(page.getByText('Redirecting')).not.toBeVisible({
      timeout: 60000,
    });
    await clusterPage.clickCreateNewClusterButton();
    await clusterPage.clickClusterNameInput();
    await clusterPage.fillClusterNameInput('Test Cluster');
    await clusterPage.clickReplicatedPerformanceClusterType();
    await clusterPage.clickGCPRegion();
    await clusterPage.clickClusterOption();
    await clusterPage.clickCreateClusterButton();
    await clusterPage.clickClustersBreadcrumb();

    await clusterPage.assertClusterHealthyStatusWithRetry(270000);
  });
});
