import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {test} from '@fixtures/8.7';

test.describe('Login Tests', () => {
  test.beforeEach(async ({page}) => {
    await page.goto('/');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Basic Login', async ({loginPage, homePage}) => {
    await loginPage.fillUsername(process.env.C8_USERNAME!);
    await expect(loginPage.usernameInput).toHaveValue(process.env.C8_USERNAME!);
    await expect(loginPage.loginMessage).toBeVisible();

    await loginPage.clickContinueButton();
    await loginPage.fillPassword(process.env.C8_PASSWORD!);
    await expect(loginPage.passwordHeading).toBeVisible();

    await loginPage.clickLoginButton();
    await expect(homePage.consoleBanner).toBeVisible({timeout: 120000});
  });
});
