import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {test} from '@fixtures/SM-8.7';

test.describe('Navigation Tests', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/identity/');
    await loginPage.login(
      'demo',
      process.env.DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD!,
    );
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Basic Navigation', async ({
    navigationPage,
    identityPage,
    modelerHomePage,
    taskPanelPage,
    operateHomePage,
    optimizeHomePage,
  }) => {
    await expect(identityPage.identityBanner).toBeVisible({timeout: 120000});
    const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
    await sleep(10000);
    await navigationPage.goToModeler();
    await expect(modelerHomePage.modelerPageBanner).toBeVisible({
      timeout: 120000,
    });
    await sleep(10000);
    await navigationPage.goToTasklist();
    await expect(taskPanelPage.tasklistBanner).toBeVisible({timeout: 120000});
    await sleep(10000);
    await navigationPage.goToOperate();
    await sleep(10000);
    await expect(operateHomePage.operateBanner).toBeVisible({timeout: 120000});
    await sleep(10000);
    await navigationPage.goToOptimize();
    await expect(optimizeHomePage.optimizeBanner).toBeVisible({
      timeout: 120000,
    });
  });
});
