import {expect} from '@playwright/test';
import {test} from '@fixtures/8.3';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {TaskPanelPage} from '@pages/8.3/TaskPanelPage';
import {HomePage} from '@pages/8.3/HomePage';
import {AppsPage} from '@pages/8.3/AppsPage';
import {OptimizeHomePage} from '@pages/8.3/OptimizeHomePage';

test.describe('Navigation Tests', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/');
    await loginPage.login();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Basic Navigation', async ({
    homePage,
    appsPage,
    modelerHomePage,
    operateHomePage,
    page,
  }) => {
    await test.step('Assert Web Modeler Navigation', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Assert Console Navigation', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickConsoleLink();
      await expect(homePage.consoleBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Assert Operate Navigation', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickOperateLink();
      await expect(operateHomePage.operateBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Assert Tasklist and Optimize Navigation', async () => {
      await homePage.clickCamundaApps();
      await appsPage.clickTasklistLink();

      const tasklistTabPromise = page.waitForEvent('popup');
      const tasklistTab = await tasklistTabPromise;
      const tasklistTabTaskPanelPage = new TaskPanelPage(tasklistTab);
      const tasklistTabHomePage = new HomePage(tasklistTab);
      const tasklistTabAppsPage = new AppsPage(tasklistTab);

      await expect(tasklistTabTaskPanelPage.taskListPageBanner).toBeVisible({
        timeout: 120000,
      });
      await tasklistTabHomePage.clickCamundaApps();
      await tasklistTabAppsPage.clickOptimizeLink();

      const optimizeTabPromise = tasklistTab.waitForEvent('popup');
      const optimizeTab = await optimizeTabPromise;
      const optimizeTabOptimizeHomePage = new OptimizeHomePage(optimizeTab);
      await expect(optimizeTabOptimizeHomePage.optimizeBanner).toBeVisible({
        timeout: 120000,
      });
    });
  });
});
