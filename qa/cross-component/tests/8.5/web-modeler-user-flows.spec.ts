import {expect} from '@playwright/test';
import {test} from '@fixtures/8.5';
import {TaskPanelPage} from '@pages/8.5/TaskPanelPage';
import {TaskDetailsPage} from '@pages/8.5/TaskDetailsPage';
import {AppsPage} from '@pages/8.5/AppsPage';
import {OperateProcessInstancePage} from '@pages/8.5/OperateProcessInstancePage';
import {LoginPage} from '@pages/8.5/LoginPage';
import {ModelerHomePage} from '@pages/8.5/ModelerHomePage';
import {ConsoleOrganizationPage} from '@pages/8.5/ConsoleOrganizationPage';
import {HomePage} from '@pages/8.5/HomePage';
import {SettingsPage} from '@pages/8.5/SettingsPage';
import {ModelerCreatePage} from '@pages/8.5/ModelerCreatePage';
import {MailSlurp} from 'mailslurp-client';
import {clickInvitationLinkInEmail} from '@pages/8.5/UtilitiesPage';
import {captureFailureVideo, captureScreenshot} from '@setup';
import {sleep} from '../../utils/sleep';
import {deleteOrganization} from '../../utils/deleteOrg';

test.describe('Web Modeler User Flow Tests', async () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/');
    await loginPage.login();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Form.js Integration with User Task and AI Generated Form', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    formJsPage,
  }) => {
    test.slow();
    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 120000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Create A New Web Modeler Project', async () => {
      await expect(modelerHomePage.createNewProjectButton).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickCreateNewProjectButton();
      await expect(modelerHomePage.projectNameInput).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.enterNewProjectName('Web Modeler Project');
    });

    await test.step('Create an AI Generated Form', async () => {
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickFormOption();
      await expect(formJsPage.aiFormGeneratorButton).toBeVisible({
        timeout: 90000,
      });
      await formJsPage.clickAIFormGeneratorButton();
      await formJsPage.clickFormRequestInput();
      await formJsPage.fillFormRequestInput(
        'Create a form with the following fields: 1. A text field with the label "Full Name" 2. A number with the label "Count" 3. A date input with the label "Date of birth"4. A Checkbox with the label "Agree"',
      );
      await formJsPage.clickGenerateFormButton();
      await expect(page.getByText('Generating...')).toBeVisible();
      await expect(
        page.getByText('Successfully generated smart form.'),
      ).toBeVisible({timeout: 300000});
      await sleep(60000);
    });

    await test.step('Add A BPMN Template To The Project', async () => {
      await modelerHomePage.clickProjectBreadcrumb();
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with a User Task with an embedded AI Generated Form and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 90000,
      });
      await modelerCreatePage.enterDiagramName(
        'User_Task_Process_With_AI_Form',
      );
      await sleep(10000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.assertImplementationOption('zeebeUserTask');
      await modelerCreatePage.clickEmbedFormButton();
      await modelerCreatePage.clickNewForm();
      await modelerCreatePage.clickEmbedButton();
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(20000);
      await modelerCreatePage.clickStartInstanceMainButton();
      await sleep(20000);
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText(
          'The following form will be automatically deployed with the diagram:',
        ),
      ).toBeVisible({
        timeout: 90000,
      });
      await sleep(20000);
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate and complete User Task in Tasklist', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();
      const operateTab = await page.waitForEvent('popup', {timeout: 60000});
      const operateTabAppsPage = new AppsPage(operateTab);
      const operateTabOperateProcessInstancePage =
        new OperateProcessInstancePage(operateTab);
      const operateTabTaskPanelPage = new TaskPanelPage(operateTab);
      const operateTabTaskDetailsPage = new TaskDetailsPage(operateTab);

      await expect(operateTabOperateProcessInstancePage.activeIcon).toBeVisible(
        {timeout: 180000},
      );
      await operateTabAppsPage.clickAppSwitcherButton();
      await operateTabAppsPage.clickTasklistLink();

      await operateTabTaskPanelPage.openTask('User_Task_Process_With_AI_Form');
      await operateTabTaskDetailsPage.clickAssignToMeButton();
      await expect(operateTabTaskDetailsPage.assignedToMeText).toBeVisible({
        timeout: 60000,
      });
      await operateTabTaskDetailsPage.fillName('test user');
      await operateTabTaskDetailsPage.checkCheckbox();
      await operateTabTaskDetailsPage.fillDate('11/11/1999');
      await operateTabTaskDetailsPage.fillNumber('10');
      await operateTabTaskDetailsPage.clickCompleteTaskButton();
      await operateTabTaskPanelPage.filterBy('Completed');
      await operateTabTaskPanelPage.openTask('User_Task_Process_With_AI_Form');
      await expect(
        operateTabTaskDetailsPage.detailsInfo.getByText(
          'User_Task_Process_With_AI_Form',
        ),
      ).toBeVisible();
    });
  });

  // Test will be skipped until flaky CI behaviour is investigated
  test.skip('User Roles User Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
  }) => {
    test.slow();

    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 120000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 180000,
      });
    });

    await test.step('Change User Roles and Assert This Updates in Web Modeler', async () => {
      await expect(modelerHomePage.openOrganizationsButton).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickOpenOrganizationsButton();
      await expect(modelerHomePage.manageButton).toBeVisible({
        timeout: 60000,
      });
      const consoleTabPromise = page.waitForEvent('popup', {timeout: 90000});
      await modelerHomePage.clickManageButton();
      const consoleTab = await consoleTabPromise;
      const consoleTabLoginPage = new LoginPage(consoleTab);
      const consoleTabModelerHomePage = new ModelerHomePage(consoleTab);
      const consoleTabConsoleOrganizationsPage = new ConsoleOrganizationPage(
        consoleTab,
      );
      const consoleTabHomePage = new HomePage(consoleTab);
      const consoleTabAppsPage = new AppsPage(consoleTab);
      const consoleTabSettingsPage = new SettingsPage(consoleTab);
      const consoleTabModelerCreatePage = new ModelerCreatePage(consoleTab);

      await consoleTabConsoleOrganizationsPage.clickUsersTab();
      await consoleTabConsoleOrganizationsPage.clickOptionsButton();
      await consoleTabConsoleOrganizationsPage.clickEditUserMenuItem();
      await consoleTabConsoleOrganizationsPage.checkAnalystCheckbox();
      await consoleTabConsoleOrganizationsPage.uncheckDeveloperCheckbox();
      await consoleTabConsoleOrganizationsPage.clickConfirmButton();
      await expect(consoleTab.getByText('Updating...')).not.toBeVisible({
        timeout: 90000,
      });

      await expect(consoleTabSettingsPage.openSettingsButton).toBeVisible({
        timeout: 90000,
      });
      await consoleTabSettingsPage.clickOpenSettingsButton();
      await consoleTabSettingsPage.clickLogoutButton();

      await consoleTabLoginPage.loginWithTestUser();
      await expect(consoleTabHomePage.camundaComponentsButton).toBeVisible({
        timeout: 60000,
      });
      await sleep(30000);
      await consoleTabHomePage.clickCamundaComponents();
      await expect(consoleTabAppsPage.modelerLink).toBeVisible({
        timeout: 30000,
      });
      await expect(consoleTabAppsPage.consoleLink).toBeVisible();
      await expect(consoleTabAppsPage.operateLink).not.toBeVisible();
      await expect(consoleTabAppsPage.tasklistLink).not.toBeVisible();
      await expect(consoleTabAppsPage.modelerLink).toBeVisible();
      await consoleTabAppsPage.clickModelerLink();

      await expect(
        consoleTabModelerHomePage.webModelerProjectFolder,
      ).toBeVisible({timeout: 120000});
      await consoleTabModelerHomePage.clickWebModelerProjectFolder();
      await expect(
        consoleTabModelerHomePage.webModelerUserFlowDiagram,
      ).toBeVisible({timeout: 120000});
      await consoleTabModelerHomePage.clickWebModelerUserFlowDiagram();
      await expect(consoleTabModelerCreatePage.deployMainButton).toBeVisible({
        timeout: 120000,
      });
      await consoleTabModelerCreatePage.clickDeployMainButtonWithRetry();
      await expect(
        consoleTab.getByText(
          'The permissions to access a cluster or to create a process on a cluster are miss',
        ),
      ).toBeVisible({timeout: 60000});
      await consoleTabModelerCreatePage.clickCancelButton();

      await expect(consoleTabSettingsPage.openSettingsButton).toBeVisible({
        timeout: 60000,
      });
      await consoleTabSettingsPage.clickOpenSettingsButton();
      await consoleTabSettingsPage.clickLogoutButton();

      await consoleTabLoginPage.login();
      await sleep(30000);
      await expect(consoleTabModelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 90000,
      });
      await expect(
        consoleTabModelerHomePage.openOrganizationsButton,
      ).toBeVisible({timeout: 120000});
      await consoleTabModelerHomePage.clickOpenOrganizationsButton();
      await expect(consoleTabModelerHomePage.manageButton).toBeVisible({
        timeout: 60000,
      });
      const newConsoleTabPromise = consoleTab.waitForEvent('popup');
      await consoleTabModelerHomePage.clickManageButton();

      const newConsoleTab = await newConsoleTabPromise;
      const newConsoleTabLoginPage = new LoginPage(newConsoleTab);
      const newConsoleTabModelerHomePage = new ModelerHomePage(newConsoleTab);
      const newConsoleTabConsoleOrganizationsPage = new ConsoleOrganizationPage(
        newConsoleTab,
      );
      const newConsoleTabHomePage = new HomePage(newConsoleTab);
      const newConsoleTabAppsPage = new AppsPage(newConsoleTab);
      const newConsoleTabSettingsPage = new SettingsPage(newConsoleTab);
      const newConsoleTabModelerCreatePage = new ModelerCreatePage(
        newConsoleTab,
      );

      await newConsoleTabConsoleOrganizationsPage.clickUsersTab();
      await newConsoleTab.reload();
      await expect(
        newConsoleTabConsoleOrganizationsPage.optionsButton,
      ).toBeVisible({timeout: 120000});
      await newConsoleTabConsoleOrganizationsPage.clickOptionsButton();
      await newConsoleTabConsoleOrganizationsPage.clickEditUserMenuItem();
      await newConsoleTabConsoleOrganizationsPage.uncheckAnalystCheckbox();
      await newConsoleTabConsoleOrganizationsPage.checkDeveloperCheckbox();
      await newConsoleTabConsoleOrganizationsPage.clickConfirmButton();
      await expect(newConsoleTab.getByText('Updating...')).not.toBeVisible({
        timeout: 90000,
      });

      await expect(newConsoleTabSettingsPage.openSettingsButton).toBeVisible({
        timeout: 120000,
      });
      await newConsoleTabSettingsPage.clickOpenSettingsButton();
      await newConsoleTabSettingsPage.clickLogoutButton();

      await newConsoleTabLoginPage.loginWithTestUser();
      await expect(newConsoleTabHomePage.camundaComponentsButton).toBeVisible({
        timeout: 120000,
      });
      await sleep(30000);
      await newConsoleTabHomePage.clickCamundaComponents();
      await expect(newConsoleTabAppsPage.consoleLink).toBeVisible({
        timeout: 30000,
      });
      await expect(newConsoleTabAppsPage.operateLink).toBeVisible({
        timeout: 120000,
      });
      await expect(newConsoleTabAppsPage.tasklistLink).toBeVisible({
        timeout: 120000,
      });
      await expect(newConsoleTabAppsPage.modelerLink).toBeVisible();
      await expect(newConsoleTabAppsPage.optimizeLink).not.toBeVisible();
      await newConsoleTabAppsPage.clickModelerLink();

      await expect(
        newConsoleTabModelerHomePage.webModelerProjectFolder,
      ).toBeVisible({timeout: 120000});
      await newConsoleTabModelerHomePage.clickWebModelerProjectFolder();
      await expect(
        newConsoleTabModelerHomePage.webModelerUserFlowDiagram,
      ).toBeVisible({timeout: 120000});
      await newConsoleTabModelerHomePage.clickWebModelerUserFlowDiagram();
      await expect(
        newConsoleTabModelerCreatePage.startInstanceMainButton,
      ).toBeVisible({
        timeout: 60000,
      });
      await newConsoleTabModelerCreatePage.clickStartInstanceMainButton();
      await expect(
        consoleTab.getByText(
          'The permissions to access a cluster or to create a process on a cluster are miss',
        ),
      ).not.toBeVisible({timeout: 60000});

      await expect(newConsoleTabSettingsPage.openSettingsButton).toBeVisible({
        timeout: 60000,
      });
      await newConsoleTabSettingsPage.clickOpenSettingsButton();
      await newConsoleTabSettingsPage.clickLogoutButton();

      await newConsoleTabLoginPage.login();
      await sleep(20000);
      await expect(newConsoleTabModelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 60000,
      });
      await expect(
        newConsoleTabModelerHomePage.openOrganizationsButton,
      ).toBeVisible({timeout: 60000});
      await newConsoleTabModelerHomePage.clickOpenOrganizationsButton();
      await expect(newConsoleTabModelerHomePage.manageButton).toBeVisible({
        timeout: 60000,
      });
      const lastConsoleTabPromise = newConsoleTab.waitForEvent('popup');
      await newConsoleTabModelerHomePage.clickManageButton();

      const lastConsoleTab = await lastConsoleTabPromise;
      const lastConsoleTabLoginPage = new LoginPage(lastConsoleTab);
      const lastConsoleTabModelerHomePage = new ModelerHomePage(lastConsoleTab);
      const lastConsoleTabConsoleOrganizationsPage =
        new ConsoleOrganizationPage(lastConsoleTab);
      const lastConsoleTabHomePage = new HomePage(lastConsoleTab);
      const lastConsoleTabAppsPage = new AppsPage(lastConsoleTab);
      const lastConsoleTabSettingsPage = new SettingsPage(lastConsoleTab);
      const lastConsoleTabModelerCreatePage = new ModelerCreatePage(
        lastConsoleTab,
      );

      await lastConsoleTabConsoleOrganizationsPage.clickUsersTab();
      await lastConsoleTab.reload();
      await expect(
        lastConsoleTabConsoleOrganizationsPage.optionsButton,
      ).toBeVisible({timeout: 120000});
      await lastConsoleTabConsoleOrganizationsPage.clickOptionsButton();
      await lastConsoleTabConsoleOrganizationsPage.clickEditUserMenuItem();
      await lastConsoleTabConsoleOrganizationsPage.checkAnalystCheckbox();
      await lastConsoleTabConsoleOrganizationsPage.clickConfirmButton();
      await expect(lastConsoleTab.getByText('Updating...')).not.toBeVisible({
        timeout: 90000,
      });

      await expect(lastConsoleTabSettingsPage.openSettingsButton).toBeVisible({
        timeout: 60000,
      });
      await lastConsoleTabSettingsPage.clickOpenSettingsButton();
      await lastConsoleTabSettingsPage.clickLogoutButton();

      await lastConsoleTabLoginPage.loginWithTestUser();
      await expect(lastConsoleTabHomePage.camundaComponentsButton).toBeVisible({
        timeout: 120000,
      });
      await sleep(30000);
      await lastConsoleTabHomePage.clickCamundaComponents();
      await expect(lastConsoleTabAppsPage.consoleLink).toBeVisible({
        timeout: 20000,
      });
      await expect(lastConsoleTabAppsPage.optimizeLink).toBeVisible();
      await expect(lastConsoleTabAppsPage.operateLink).toBeVisible();
      await expect(lastConsoleTabAppsPage.tasklistLink).toBeVisible();
      await expect(lastConsoleTabAppsPage.modelerLink).toBeVisible();
      await lastConsoleTabAppsPage.clickModelerLink();

      await expect(
        lastConsoleTabModelerHomePage.webModelerProjectFolder,
      ).toBeVisible({timeout: 300000});
      await lastConsoleTabModelerHomePage.clickWebModelerProjectFolder();
      await expect(
        lastConsoleTabModelerHomePage.webModelerUserFlowDiagram,
      ).toBeVisible({timeout: 240000});
      await lastConsoleTabModelerHomePage.clickWebModelerUserFlowDiagram();
      await expect(
        lastConsoleTabModelerCreatePage.startInstanceMainButton,
      ).toBeVisible({
        timeout: 60000,
      });
      await lastConsoleTabModelerCreatePage.clickStartInstanceMainButton();
      await expect(
        consoleTab.getByText(
          'The permissions to access a cluster or to create a process on a cluster are miss',
        ),
      ).not.toBeVisible({timeout: 60000});
    });
  });

  test('Form.js Integration with AI generated Start form', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
  }) => {
    test.slow();
    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 120000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Navigate to Web Modeler project', async () => {
      await expect(modelerHomePage.webModelerProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickWebModelerProjectFolder();
    });

    await test.step('Add A BPMN Template To The Project', async () => {
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with a AI generated Start Form and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 90000,
      });
      await modelerCreatePage.enterDiagramName('AI-generated-Start-form');
      await sleep(10000);
      await modelerCreatePage.clickEmbedFormButton();
      await modelerCreatePage.clickNewForm();
      await modelerCreatePage.clickEmbedButton();
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(20000);

      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.fillStartFormName('test user');
      await modelerCreatePage.checkStartFormCheckbox();
      await modelerCreatePage.fillStartFormDate('11/11/1999');
      await modelerCreatePage.fillStartFormNumber('10');
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate and check if process is complete', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();
      const operateTab = await page.waitForEvent('popup', {timeout: 60000});
      const operateTabOperateProcessInstancePage =
        new OperateProcessInstancePage(operateTab);
      await operateTab.reload();

      await expect(
        operateTabOperateProcessInstancePage.completedIcon,
      ).toBeVisible({
        timeout: 1800000,
      });
    });
  });

  test('Invite New C8 User To Web Modeler Flow', async ({
    page,
    homePage,
    appsPage,
    modelerHomePage,
    signUpPage,
    modelerUserInvitePage,
    loginPage,
  }) => {
    test.slow();

    const apiKey = process.env.MAIL_SLURP_API_KEY!;
    const mailslurp = new MailSlurp({apiKey: apiKey});
    const inbox = await mailslurp.createInbox();
    const id = inbox.id;
    const emailAddress = inbox.emailAddress;

    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 120000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Navigate to Web Modeler project', async () => {
      await expect(modelerHomePage.webModelerProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickWebModelerProjectFolder();
      await modelerUserInvitePage.clickAddUser();
      await modelerUserInvitePage.clickUserEmailInput();
    });

    await test.step('Add collaborator to Web Modeler Project', async () => {
      await modelerUserInvitePage.fillUserEmailInput(emailAddress);
      await modelerUserInvitePage.clickSendInviteButton();
      await modelerUserInvitePage.checkPendingInviteText();
      await modelerUserInvitePage.clickSettings();
      await modelerUserInvitePage.clickLogout();
      await clickInvitationLinkInEmail(page, id, mailslurp);
    });

    await test.step('Signup new collaborator to Web Modeler Project', async () => {
      await signUpPage.clickFirstNameInput();
      await signUpPage.fillFirstNameInput('QA');
      await signUpPage.clickLastNameInput();
      await signUpPage.fillLastNameInput('Camunda');
      await signUpPage.clickPasswordInputInvitedUser();
      await signUpPage.fillPasswordInputInvitedUser(process.env.C8_PASSWORD!);
      await signUpPage.clickSignupButton();
      await loginPage.login({
        username: emailAddress,
        password: process.env.C8_PASSWORD!,
      });
      await expect(page.getByText('Personalize your experience')).toBeVisible({
        timeout: 180000,
      });
      await modelerUserInvitePage.clickSkipCustomization();
      await modelerUserInvitePage.clickCloseHelpCenter();
    });

    await test.step('Navigate to Web Modeler project as Collaborator', async () => {
      await expect(modelerHomePage.webModelerProjectFolder).toBeVisible({
        timeout: 120000,
      });

      await modelerUserInvitePage.clickSettings();
      await modelerUserInvitePage.clickLogout();
    });
    // login as Project Admin

    await expect(loginPage.loginMessage).toBeVisible({
      timeout: 60000,
    });
    await page.goto('/');
    await loginPage.login();

    await test.step('Navigate to Web Modeler as Project Admin', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 120000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });
    await test.step('Assert Collaborator presence as Project Admin', async () => {
      await expect(modelerHomePage.webModelerProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickWebModelerProjectFolder();
      await expect(page.getByText(emailAddress)).toBeVisible({
        timeout: 120000,
      });
    });
  });

  test('Invite Existing C8 member to Web Modeler flow', async ({
    page,
    loginPage,
    signUpPage,
    homePage,
    modelerUserInvitePage,
    appsPage,
    modelerHomePage,
    settingsPage,
  }) => {
    test.slow();

    const apiKey = process.env.MAIL_SLURP_API_KEY!;
    const mailslurp = new MailSlurp({apiKey: apiKey});
    const {id, emailAddress} = await mailslurp.createInbox();

    // Signup new user for C8 and complete the onboarding process
    await page.goto('https://accounts.cloud.ultrawombat.com/signup');
    await signUpPage.clickFirstNameInput();
    await signUpPage.fillFirstNameInput('QA');
    await signUpPage.clickLastNameInput();
    await signUpPage.fillLastNameInput('Camunda');
    await signUpPage.clickEmailInput();
    await signUpPage.fillEmailInput(emailAddress);
    await signUpPage.clickCompanyInput();
    await signUpPage.fillCompanyInput('Camunda Company');
    await signUpPage.clickCreateAccount();
    await expect(signUpPage.backgroundPageHeading).toBeVisible();
    await signUpPage.selectBackground('Work');
    await signUpPage.selectWhatDoYouDo('Software Developer');
    await expect(signUpPage.APIClientDropdownList).toBeVisible({
      timeout: 40000,
    });
    await signUpPage.selectAPIClient('Java');
    await signUpPage.selectLevelOfExperience('Basic process design');
    await signUpPage.clickNext();
    await expect(signUpPage.useCasePageHeading).toBeVisible();
    await signUpPage.selectRunProcessRadioButton();
    await signUpPage.selectEndpointsToAutomate('Microservices/Service tasks');
    await signUpPage.clickNext();
    await expect(signUpPage.goalPageHeading).toBeVisible();
    await signUpPage.selectWhyEvaluatingCamunda(
      'Creating process models and BPMN diagrams',
    );
    await signUpPage.clickNext();
    await expect(signUpPage.camundaPlansPageHeading).toBeVisible();
    await signUpPage.clickConfirmSaaSTrial();
    await expect(signUpPage.setPasswordPageHeading).toBeVisible();
    await signUpPage.clickPasswordInput();
    await signUpPage.fillPasswordInput(process.env.C8_PASSWORD!);
    await signUpPage.clickLoginToCamunda();
    await expect(signUpPage.signUpCompletedPageHeading).toBeVisible({
      timeout: 40000,
    });
    await clickInvitationLinkInEmail(page, id, mailslurp);
    await settingsPage.clickOpenSettingsButton();
    await settingsPage.clickLogoutButton();
    await loginPage.loginWithoutOrgAssertion({
      username: emailAddress,
      password: process.env.C8_PASSWORD!,
    });
    await expect(homePage.gettingStartedHeading).toBeVisible({
      timeout: 40000,
    });
    await homePage.clickCamundaComponents();
    await appsPage.clickConsoleLink();
    await expect(homePage.consoleBanner).toBeVisible({
      timeout: 120000,
    });
    const newOrganizationUuid = homePage.organizationUuid();

    // Login as Admin
    await settingsPage.clickOpenSettingsButton();
    await settingsPage.clickLogoutButton();
    await loginPage.login();

    // Navigate to Web Modeler
    await homePage.clickCamundaComponents();
    await appsPage.clickModelerLink();
    await expect(modelerHomePage.modelerPageBanner).toBeVisible({
      timeout: 120000,
    });

    // Navigate to Web Modeler project
    await expect(modelerHomePage.webModelerProjectFolder).toBeVisible({
      timeout: 120000,
    });
    await modelerHomePage.clickWebModelerProjectFolder();
    await modelerUserInvitePage.clickAddUser();
    await modelerUserInvitePage.clickUserEmailInput();

    // Add collaborator to Web Modeler Project
    await modelerUserInvitePage.fillUserEmailInput(emailAddress);
    await modelerUserInvitePage.clickSendInviteButton();
    await modelerUserInvitePage.checkPendingInviteText();
    await settingsPage.clickOpenSettingsButton();
    await settingsPage.clickLogoutButton();
    await sleep(20000);
    await clickInvitationLinkInEmail(page, id, mailslurp);

    // Navigate to Web Modeler project as Collaborator
    await loginPage.login({
      username: emailAddress,
      password: process.env.C8_PASSWORD!,
    });
    await sleep(10000);
    await page.reload();
    await expect(modelerHomePage.webModelerProjectFolder).toBeVisible({
      timeout: 180000,
    });
    await settingsPage.clickOpenSettingsButton();
    await settingsPage.clickLogoutButton();

    await loginPage.login();

    // Navigate to Web Modeler as Project Admin
    await homePage.clickCamundaComponents();
    await appsPage.clickModelerLink();
    await expect(modelerHomePage.modelerPageBanner).toBeVisible({
      timeout: 120000,
    });

    // Assert Collaborator presence as Project Admin
    await expect(modelerHomePage.webModelerProjectFolder).toBeVisible({
      timeout: 120000,
    });
    await modelerHomePage.clickWebModelerProjectFolder();
    await expect(page.getByText(emailAddress)).toBeVisible({
      timeout: 120000,
    });

    //Delete trial organization
    await deleteOrganization(page, newOrganizationUuid);
  });

  test('Form.js Integration with User Task and Embedded Form', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
  }) => {
    test.slow();

    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 120000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Navigate to Web Modeler project', async () => {
      await expect(modelerHomePage.webModelerProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickWebModelerProjectFolder();
    });

    await test.step('Add A BPMN Template To The Project', async () => {
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with a embedded form and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 90000,
      });

      await modelerCreatePage.enterDiagramName(
        'User_Task_Process_With_embedded_form',
      );
      await sleep(10000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickUserTaskOption();
      await modelerCreatePage.chooseImplementationOption('jobWorker');
      await modelerCreatePage.chooseFormLinkingTypeOption(
        'Camunda Form (embedded)',
      );
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(20000);

      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await expect(
        page.getByText('The diagram contains 1 embedded form.'),
      ).toBeVisible({timeout: 30000});
      await sleep(30000);
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate and check if process is complete', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 180000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();
      const operateTab = await page.waitForEvent('popup', {timeout: 60000});
      const operateTabAppsPage = new AppsPage(operateTab);
      const operateTabOperateProcessInstancePage =
        new OperateProcessInstancePage(operateTab);
      const operateTabTaskPanelPage = new TaskPanelPage(operateTab);
      const operateTabTaskDetailsPage = new TaskDetailsPage(operateTab);
      const operateHomePage = new HomePage(operateTab);

      await expect(operateTabOperateProcessInstancePage.activeIcon).toBeVisible(
        {timeout: 180000},
      );
      await operateHomePage.clickCamundaApps();
      await operateTabAppsPage.clickTasklistLink();

      await operateTabTaskPanelPage.openTask(
        'User_Task_Process_With_embedded_form',
      );
      await operateTabTaskDetailsPage.clickAssignToMeButton();
      await expect(operateTabTaskDetailsPage.assignedToMeText).toBeVisible({
        timeout: 60000,
      });
      await operateTabTaskDetailsPage.fillName('test user');
      await operateTabTaskDetailsPage.clickCompleteTaskButton();
      await expect(operateTab.getByText('Task completed')).toBeVisible({
        timeout: 200000,
      });
    });
  });
});
