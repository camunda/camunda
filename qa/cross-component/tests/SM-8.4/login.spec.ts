import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {test} from '@fixtures/SM-8.4';

test.describe('Login Tests', () => {
  test.beforeEach(async ({page}) => {
    await page.goto('/identity');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Basic Login', async ({loginPage, identityPage}) => {
    await loginPage.fillUsername('demo');
    await expect(loginPage.usernameInput).toHaveValue('demo');
    await loginPage.fillPassword(
      process.env.DISTRO_QA_E2E_TESTS_IDENTITY_FIRSTUSER_PASSWORD!,
    );
    await loginPage.clickLoginButton();
    await expect(identityPage.identityBanner).toBeVisible({timeout: 120000});
  });
});
