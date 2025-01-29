import {expect} from '@playwright/test';
import {test} from '@fixtures/SM-8.6';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.describe('Web Modeler User Flow Tests', () => {
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

  test('User Roles User Flow', async ({
    page,
    navigationPage,
    taskPanelPage,
    operateHomePage,
    optimizeHomePage,
    identityPage,
    modelerHomePage,
    consoleHomePage,
  }) => {
    test.slow();
    await navigationPage.goToIdentity();
    await identityPage.clickUsersLink();
    await identityPage.clickDemoUser();
    await identityPage.clickAssignedRolesTab();

    await identityPage.clickDeleteAccessButton('Grants full access to Operate');
    await identityPage.clickConfirmDeleteButton();

    await identityPage.clickDeleteAccessButton(
      'Grants full access to Optimize',
    );
    await identityPage.clickConfirmDeleteButton();

    await identityPage.clickDeleteAccessButton(
      'Grants full access to Tasklist',
    );
    await identityPage.clickConfirmDeleteButton();

    await identityPage.clickDeleteAccessButton(
      'Grants full access to Web Modeler',
    );
    await identityPage.clickConfirmDeleteButton();

    await identityPage.clickDeleteAccessButton('Grants full access to Console');
    await identityPage.clickConfirmDeleteButton();

    const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

    await sleep(50000);
    await navigationPage.goToOptimize();
    await expect(
      page.getByText(
        'User has no authorization to access Optimize. Please check your Identity configuration',
      ),
    ).toBeVisible({timeout: 120000});
    await expect(optimizeHomePage.optimizeBanner).not.toBeVisible({
      timeout: 60000,
    });

    await sleep(50000);
    await navigationPage.goToTasklist();
    await expect(
      page.getByText(
        'No permission for Tasklist - Please check your configuration.',
      ),
    ).toBeVisible({
      timeout: 60000,
    });
    await expect(taskPanelPage.tasklistBanner).not.toBeVisible({
      timeout: 60000,
    });

    await sleep(60000);
    await navigationPage.goToOperate();
    await expect(page.getByText('Something went wrong')).toBeVisible({
      timeout: 120000,
    });
    await expect(operateHomePage.operateBanner).not.toBeVisible({
      timeout: 60000,
    });

    await sleep(60000);
    await navigationPage.goToConsole();
    await expect(page.getByText('Something went wrong')).toBeVisible({
      timeout: 60000,
    });
    await expect(consoleHomePage.consoleBanner).not.toBeVisible({
      timeout: 60000,
    });

    await sleep(50000);
    await navigationPage.goToModeler();
    await expect(modelerHomePage.modelerPageBanner).not.toBeVisible({
      timeout: 60000,
    });

    await navigationPage.goToIdentity();

    await identityPage.clickUsersLink();
    await identityPage.clickDemoUser();
    await identityPage.clickAssignedRolesTab();

    await identityPage.clickAssignRolesButton();
    await identityPage.clickOperateCheckbox();
    await identityPage.clickTasklistCheckbox();
    await identityPage.clickOptimizeCheckbox();
    await identityPage.clickModelerCheckbox();
    await identityPage.clickConsoleCheckbox();
    await identityPage.clickAddButton();

    await sleep(60000);
    await navigationPage.goToTasklist();
    await expect(taskPanelPage.tasklistBanner).toBeVisible({timeout: 180000});

    await sleep(50000);

    await navigationPage.goToOperate();
    await expect(operateHomePage.operateBanner).toBeVisible({timeout: 180000});

    await sleep(50000);
    await navigationPage.goToOptimize();
    await expect(optimizeHomePage.optimizeBanner).toBeVisible({
      timeout: 180000,
    });

    await sleep(50000);
    await navigationPage.goToModeler();
    await expect(modelerHomePage.modelerPageBanner).toBeVisible({
      timeout: 180000,
    });

    await navigationPage.goToConsole();
    await sleep(60000);
    await page.reload();
    try {
      await expect(consoleHomePage.consoleBanner).toBeVisible({
        timeout: 180000,
      });
    } catch {
      page.reload();
      await expect(consoleHomePage.consoleBanner).toBeVisible({
        timeout: 180000,
      });
    }
  });
});
