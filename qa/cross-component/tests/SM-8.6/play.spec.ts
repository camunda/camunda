import {expect} from '@playwright/test';
import {test} from '@fixtures/SM-8.6';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.describe('Deploy and run a process in Play', () => {
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

  test('User Tasks and Service Task', async ({
    modelerHomePage,
    modelerCreatePage,
    playPage,
  }) => {
    test.slow();
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

      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

      await sleep(20000);

      // Add a user task with Job worker implementation (default)
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.clickGeneralPropertiesPanel();
      await modelerCreatePage.clickElemendIdInput();
      await modelerCreatePage.fillElementIdInput('tasklist-user-task');

      // Add a user task with Zeebe user task implementation
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.chooseImplementationOption('zeebeUserTask');
      await modelerCreatePage.clickElemendIdInput();
      await modelerCreatePage.fillElementIdInput('zeebe-user-task');

      // Add a service task
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickServiceTaskOption();
      await modelerCreatePage.clickElemendIdInput();
      await modelerCreatePage.fillElementIdInput('service-task');
      await modelerCreatePage.clickTaskDefinitionPropertiesPanel();
      await modelerCreatePage.clickJobTypeInput();
      await modelerCreatePage.fillJobTypeInput('someJob');

      // Add end event
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
    });

    await test.step('Open Play', async () => {
      await modelerCreatePage.switchToPlay();
      await modelerCreatePage.completePlayConfiguration();
    });

    await test.step('Start and complete the process instance in Play', async () => {
      await playPage.dismissStartModal();
      await playPage.clickStartInstanceButton();
      await playPage.waitForInstanceDetailsToBeLoaded();

      // wait for and complete the "tasklist user task"
      await playPage.waitForNextElementToBeActive('tasklist-user-task');
      await playPage.waitForCompleteJobButtonToBeAvailable();
      await playPage.clickCompleteJobButton();

      // wait for and complete the "zeebe user task"
      await playPage.waitForNextElementToBeActive('zeebe-user-task');
      await playPage.waitForCompleteJobButtonToBeAvailable();
      await playPage.clickCompleteJobButton();

      // wait for and complete the "service task"
      await playPage.waitForNextElementToBeActive('service-task');
      await playPage.waitForCompleteJobButtonToBeAvailable();
      await playPage.clickCompleteJobButton();

      await playPage.waitForProcessToBeCompleted();
    });
  });
});
