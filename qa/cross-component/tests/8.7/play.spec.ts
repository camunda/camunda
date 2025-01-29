import {expect} from '@playwright/test';
import {test} from '@fixtures/8.7';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {sleep} from 'utils/sleep';

test.describe('Deploy and run a process in Play', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/');
    await loginPage.login();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('User Tasks and Service Task', async ({
    modelerHomePage,
    modelerCreatePage,
    playPage,
    homePage,
    appsPage,
  }) => {
    test.slow();

    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 40000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Create A New Play Project', async () => {
      await expect(modelerHomePage.createNewProjectButton).toBeVisible({
        timeout: 180000,
      });
      await modelerHomePage.clickCreateNewProjectButton();
      await expect(modelerHomePage.projectNameInput).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.enterNewProjectName('Play Project');
    });

    await test.step('Add A BPMN Template To The Project', async () => {
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create a BPMN Diagram with user tasks and service task', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName('Play_Test_Process');
      await sleep(20000);

      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.chooseImplementationOption('Job worker');
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickIdInput();
      await modelerCreatePage.fillIdInput('tasklist-user-task');

      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.clickIdInput();
      await modelerCreatePage.fillIdInput('zeebe-user-task');

      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickServiceTaskOption();
      await modelerCreatePage.clickIdInput();
      await modelerCreatePage.fillIdInput('service-task');
      await modelerCreatePage.clickTaskDefinitionPropertiesPanel();
      await modelerCreatePage.clickJobTypeInput();
      await modelerCreatePage.fillJobTypeInput('someJob');

      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(60000);
    });

    await test.step('Open Play', async () => {
      await modelerCreatePage.switchToPlay();
      await modelerCreatePage.completePlayConfiguration();
    });

    await test.step('Start and complete the process instance in Play', async () => {
      await playPage.dismissStartModal();
      await playPage.clickStartInstanceButton();
      await playPage.waitForInstanceDetailsToBeLoaded();

      await playPage.waitForNextElementToBeActive('tasklist-user-task');
      await playPage.waitForCompleteJobButtonToBeAvailable();
      await playPage.clickCompleteJobButton();

      await playPage.waitForNextElementToBeActive('zeebe-user-task');
      await playPage.waitForCompleteJobButtonToBeAvailable();
      await playPage.clickCompleteJobButton();

      await playPage.waitForNextElementToBeActive('service-task');
      await playPage.waitForCompleteJobButtonToBeAvailable();
      await playPage.clickCompleteJobButton();

      await playPage.waitForProcessToBeCompleted();
    });
  });
});
