import {expect} from '@playwright/test';
import {test} from '@fixtures/8.7';
import {captureScreenshot, captureFailureVideo} from '@setup';

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
    optimizeHomePage,
    taskPanelPage,
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

    await test.step('Assert Optimize Navigation', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickOptimizeLink();
      await expect(optimizeHomePage.optimizeBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Assert Tasklist Navigation', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickTasklistLink();
      await expect(taskPanelPage.taskListPageBanner).toBeVisible({
        timeout: 120000,
      });
    });
  });
});
