import {expect} from '@playwright/test';
import {test} from '@fixtures/8.3';
import {
  captureScreenshot,
  captureFailureVideo,
  performBasicAuthPostRequest,
  performBearerTokenAuthPostRequest,
} from '@setup';
import {OperateProcessInstancePage} from '@pages/8.3/OperateProcessInstancePage';

test.describe('Connectors User Flow Tests', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await page.goto('/');
    await loginPage.login();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('REST Connector No Auth User Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    connectorSettingsPage,
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

    await test.step('Create New Project with a BPMN Diagram Template', async () => {
      await expect(modelerHomePage.createNewProjectButton).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickCreateNewProjectButton();
      await expect(modelerHomePage.projectNameInput).toBeVisible({
        timeout: 30000,
      });
      await modelerHomePage.enterNewProjectName('Connectors Project');
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with REST Connector and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName(
        'REST_Connector_No_Auth_Process',
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickRestConnectorOption();
      await connectorSettingsPage.clickUrlInput();
      await connectorSettingsPage.fillUrlInput(
        'https://camunda.proxy.beeceptor.com/pre-prod/no-auth-test',
      );
      await connectorSettingsPage.clickResultExpressionInput();
      await connectorSettingsPage.fillResultExpressionInput('body');
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(10000);
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();
    });

    await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 180000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();

      const operateTab = await page.waitForEvent('popup');
      const operateTabProcessInstancePage = new OperateProcessInstancePage(
        operateTab,
      );

      await operateTab.reload();

      await expect(operateTabProcessInstancePage.completedIcon).toBeVisible({
        timeout: 1800000,
      });

      await operateTab.reload();
      await expect(operateTab.getByTestId('variables-list')).toBeVisible({
        timeout: 90000,
      });

      await expect(
        (
          await operateTabProcessInstancePage.connectorResultVariableName(
            'status',
          )
        ).isVisible(),
      ).toBeTruthy();

      await expect(operateTab.getByText('"Awesome!"')).toBeVisible({
        timeout: 60000,
      });
    });
  });

  test('REST Connector Bearer Token Auth User Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    connectorSettingsPage,
  }) => {
    test.slow();
    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 180000,
      });
      await homePage.clickCamundaComponents();
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Open Web Modeler Project and Create a BPMN Diagram Template', async () => {
      await expect(modelerHomePage.connectorsProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickConnectorsProjectFolder();
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with REST Connector with Bearer Token Auth and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName(
        'REST_Connector_Bearer_Auth_Process',
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickRestConnectorOption();
      await connectorSettingsPage.selectAuthenticationType('bearer');
      await expect(connectorSettingsPage.bearerTokenInput).toBeVisible({
        timeout: 60000,
      });
      await connectorSettingsPage.clickBearerTokenInput();
      await connectorSettingsPage.fillBearerTokenInput('thisisabearertoken');
      await expect(connectorSettingsPage.methodTypeDropdown).toBeVisible({
        timeout: 30000,
      });
      await connectorSettingsPage.selectMethodType('POST');
      await connectorSettingsPage.clickUrlInput();
      await connectorSettingsPage.fillUrlInput(
        'https://camunda.proxy.beeceptor.com/pre-prod/bearer-auth-test',
      );
      await connectorSettingsPage.clickResultExpressionInput();
      await connectorSettingsPage.fillResultExpressionInput(
        '{message:response.body}',
      );
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(10000);
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();
      await performBearerTokenAuthPostRequest(
        'https://camunda.proxy.beeceptor.com/pre-prod/bearer-auth-test',
        'thisisabearertoken',
      );
    });

    await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 240000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();

      const operateTab = await page.waitForEvent('popup');
      const operateTabProcessInstancePage = new OperateProcessInstancePage(
        operateTab,
      );

      await expect(operateTabProcessInstancePage.completedIcon).toBeVisible({
        timeout: 180000,
      });

      await operateTab.reload();
      await expect(operateTab.getByTestId('variables-list')).toBeVisible({
        timeout: 30000,
      });

      await expect(
        (
          await operateTabProcessInstancePage.connectorResultVariableName(
            'message',
          )
        ).isVisible(),
      ).toBeTruthy();

      await expect(operateTab.getByText('"Awesome!"')).toBeVisible({
        timeout: 90000,
      });
    });
  });

  test('Start Event Webhook Connector No Auth User Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    request,
    operateHomePage,
    operateProcessInstancePage,
    operateProcessesPage,
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

    await test.step('Open Connectors Project and Create a BPMN Diagram Template', async () => {
      await expect(modelerHomePage.connectorsProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickConnectorsProjectFolder();
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with Start Event Webhook Connector and Deploy Diagram', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.enterDiagramName(
        'Start_Event_Webhook_Connector_No_Auth_Process',
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickChangeTypeButton();
      await expect(
        modelerCreatePage.webhookStartEventConnectorOption,
      ).toBeVisible({
        timeout: 30000,
      });
      await modelerCreatePage.clickWebhookStartEventConnectorOption();
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(10000);
      await expect(modelerCreatePage.deployMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickDeployMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 180000,
      });
      await modelerCreatePage.clickDeploySubButton();
    });

    await test.step('Assert Webhook Is Active In Cluster And Make Authorization Request', async () => {
      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickWebhookTab();
      await expect(modelerCreatePage.webhookIsActiveButton).toBeVisible({
        timeout: 90000,
      });
      await modelerCreatePage.clickWebhookIsActiveButton();

      const url: string =
        await modelerCreatePage.copyWebhookUrlToClipboardButton.innerText();
      const response = await request.post(url);
      await expect(response.status()).toBe(200);
    });

    await test.step('Assert Diagram Has Successfully Completed in Operate', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickOperateFilter();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await operateHomePage.clickProcessesTab();
      await page.reload();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'Start_Event_Webhook_Connector_No_Auth_Process',
      );
      await expect(operateProcessInstancePage.completedIcon).toBeVisible({
        timeout: 90000,
      });
    });
  });

  test('Connector Secrets User Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    connectorSettingsPage,
    clusterPage,
    clusterSecretsPage,
  }) => {
    test.slow();
    await test.step('Navigate to Cluster Secrets', async () => {
      await expect(homePage.clusterTab).toBeVisible();
      await homePage.clickClusters();
      await expect(page.getByText('Redirecting')).not.toBeVisible({
        timeout: 60000,
      });
      await clusterPage.clickTestClusterLink();
      const sleep = (ms: number | undefined) =>
        new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await clusterPage.clickConnectorSecretesTab();
    });

    await test.step('Delete Connector Secrets If Exist', async () => {
      await clusterSecretsPage.deleteConnectorSecretsIfExist();
    });

    await test.step('Create Cluster Secret', async () => {
      await clusterSecretsPage.clickCreateNewSecretButton();
      await clusterSecretsPage.clickKeyInput();
      await clusterSecretsPage.fillKeyInput('endpoint_url');
      await clusterSecretsPage.clickValueInput();
      await clusterSecretsPage.fillValueInput(
        'https://camunda.proxy.beeceptor.com/pre-prod/basic-auth-test',
      );
      await clusterSecretsPage.clickCreateButton();
      await expect(page.getByText('Creating...')).not.toBeVisible({
        timeout: 60000,
      });

      await expect(clusterSecretsPage.createNewSecretButton).toBeVisible({
        timeout: 90000,
      });
      await clusterSecretsPage.clickCreateNewSecretButton();
      await clusterSecretsPage.clickKeyInput();
      await clusterSecretsPage.fillKeyInput('username');
      await clusterSecretsPage.clickValueInput();
      await clusterSecretsPage.fillValueInput('username');
      await clusterSecretsPage.clickCreateButton();
      await expect(page.getByText('Creating...')).not.toBeVisible({
        timeout: 60000,
      });

      await expect(clusterSecretsPage.createNewSecretButton).toBeVisible({
        timeout: 90000,
      });
      await clusterSecretsPage.clickCreateNewSecretButton();
      await clusterSecretsPage.clickKeyInput();
      await clusterSecretsPage.fillKeyInput('password');
      await clusterSecretsPage.clickValueInput();
      await clusterSecretsPage.fillValueInput('password');
      await clusterSecretsPage.clickCreateButton();
      await expect(page.getByText('Creating...')).not.toBeVisible({
        timeout: 60000,
      });
    });

    await test.step('Navigate to Web Modeler', async () => {
      await expect(homePage.camundaComponentsButton).toBeVisible({
        timeout: 20000,
      });
      await homePage.clickCamundaComponents();
      await expect(appsPage.modelerLink).toBeVisible({timeout: 20000});
      await appsPage.clickModelerLink();
      await expect(modelerHomePage.modelerPageBanner).toBeVisible({
        timeout: 120000,
      });
    });

    await test.step('Open Connectors Project and Create a BPMN Diagram Template', async () => {
      await expect(modelerHomePage.connectorsProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickConnectorsProjectFolder();
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with REST Connector using secrets and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 90000,
      });
      await modelerCreatePage.enterDiagramName('REST_Connector_Process');
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickRestConnectorOption();
      await connectorSettingsPage.selectAuthenticationType('basic');
      await expect(connectorSettingsPage.usernameInput).toBeVisible({
        timeout: 60000,
      });
      await connectorSettingsPage.clickUsernameInput();
      await connectorSettingsPage.fillUsernameInput('secrets.username');
      await connectorSettingsPage.clickPasswordInput();
      await connectorSettingsPage.fillPasswordInput('secrets.password');
      await connectorSettingsPage.selectMethodType('POST');
      await connectorSettingsPage.clickUrlInput();
      await connectorSettingsPage.fillUrlInput('secrets.endpoint_url');
      await expect(connectorSettingsPage.resultVariableInput).toBeVisible({
        timeout: 60000,
      });
      await connectorSettingsPage.clickResultVariableInput();
      await connectorSettingsPage.fillResultVariableInput('result');
      await connectorSettingsPage.clickResultExpressionInput();
      await connectorSettingsPage.fillResultExpressionInput(
        '{message:response.body.message}',
      );
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(10000);
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 30000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();

      await performBasicAuthPostRequest(
        'https://camunda.proxy.beeceptor.com/pre-prod/basic-auth-test',
        'username',
        'password',
      );
    });

    await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
      await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
        timeout: 90000,
      });
      await modelerCreatePage.clickViewProcessInstanceLink();

      const operateTab = await page.waitForEvent('popup', {timeout: 90000});
      const operateTabProcessInstancePage = new OperateProcessInstancePage(
        operateTab,
      );

      await operateTab.reload();
      await expect(operateTabProcessInstancePage.completedIcon).toBeVisible({
        timeout: 180000,
      });

      await operateTab.reload();
      await expect(operateTab.getByTestId('variables-list')).toBeVisible({
        timeout: 90000,
      });
      await expect(
        operateTab
          .getByTestId('variable-message')
          .getByText('"Message from Mock!"'),
      ).toBeVisible({timeout: 60000});
    });
  });

  test('Marketplace Connector User Flow', async ({
    page,
    homePage,
    appsPage,
    modelerHomePage,
    modelerCreatePage,
    connectorMarketplacePage,
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

    await test.step('Navigate to Connectors project', async () => {
      await expect(modelerHomePage.connectorsProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickConnectorsProjectFolder();
    });

    await test.step('Add A BPMN Template To The Project', async () => {
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create Public Holiday Marketplace connector', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 90000,
      });
      await modelerCreatePage.enterDiagramName(
        'Public Holiday Marketplace Connector',
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();

      await expect(modelerCreatePage.marketPlaceButton).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickMarketPlaceButton();
      await connectorMarketplacePage.clickSearchForConnectorTextbox();
      await connectorMarketplacePage.fillSearchForConnectorTextbox(
        'Public Holiday Connector',
      );
      await sleep(10000);
      await connectorMarketplacePage.downloadConnectorToProject();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickPublicHolidayConnectorOption();
      await modelerCreatePage.clickPublicHolidayYearOption();
      await modelerCreatePage.fillPublicHolidayYearOption('2000');
      await modelerCreatePage.clickPublicHolidayCountryCodeOption();
      await modelerCreatePage.fillPublicHolidayCountryCodeOption('NL');

      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();
      await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
        await expect(modelerCreatePage.viewProcessInstanceLink).toBeVisible({
          timeout: 120000,
        });
        await modelerCreatePage.clickViewProcessInstanceLink();

        const operateTab = await page.waitForEvent('popup');
        const operateTabProcessInstancePage = new OperateProcessInstancePage(
          operateTab,
        );

        await expect(operateTabProcessInstancePage.completedIcon).toBeVisible({
          timeout: 1200000,
        });
        await operateTab.reload();
        await expect(operateTab.getByTestId('variables-list')).toBeVisible({
          timeout: 30000,
        });
        await expect(
          (
            await operateTabProcessInstancePage.connectorResultVariableName(
              'publicHolidayList',
            )
          ).isVisible(),
        ).toBeTruthy();

        await expect(operateTab.getByText('Christmas')).toBeVisible({
          timeout: 60000,
        });
      });
    });
  });

  test('Intermediate Event Webhook Connector No Auth User Flow', async ({
    page,
    homePage,
    modelerHomePage,
    appsPage,
    modelerCreatePage,
    request,
    operateHomePage,
    operateProcessInstancePage,
    operateProcessesPage,
  }) => {
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

    await test.step('Open Connectors Project and Create a BPMN Diagram Template', async () => {
      await expect(modelerHomePage.connectorsProjectFolder).toBeVisible({
        timeout: 120000,
      });
      await modelerHomePage.clickConnectorsProjectFolder();
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with Intermediate Event Webhook Connector and Deploy Diagram', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName(
        'Intermediate_Event_Webhook_Connector_No_Auth_Process',
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickIntermediateBoundaryEvent();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickIntermediateWebhookConnectorOption();
      await modelerCreatePage.clickCorrelationKeyProcessInput;
      await modelerCreatePage.fillCorrelationKeyProcessInput('"test"');
      await modelerCreatePage.clickCorrelationKeyPayloadInput;
      await modelerCreatePage.fillCorrelationKeyPayloadInput('"test"');
      await modelerCreatePage.clickAppendElementButton();
      await modelerCreatePage.clickAppendEndEventButton();

      await sleep(10000);
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await expect(page.getByText('Healthy', {exact: true})).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickStartInstanceSubButton();
      await sleep(10000);
    });

    await test.step('Assert Webhook Is Active In Cluster And Make Authorization Request', async () => {
      await modelerCreatePage.clickSecondPlacedElement();
      await modelerCreatePage.clickWebhookTab();
      await expect(modelerCreatePage.webhookIsActiveButton).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickWebhookIsActiveButton();

      const url: string =
        await modelerCreatePage.copyWebhookUrlToClipboardButton.innerText();
      const response = await request.post(url, {
        data: {
          test: 'test',
        },
      });
      await expect(response.status()).toBe(200);
    });

    await test.step('Assert Diagram Has Successfully Completed in Operate', async () => {
      await homePage.clickCamundaComponents();
      await appsPage.clickOperateFilter();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await operateHomePage.clickProcessesTab();
      await page.reload();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'Intermediate_Event_Webhook_Connector_No_Auth_Process',
      );
      await expect(operateProcessInstancePage.completedIcon).toBeVisible({
        timeout: 120000,
      });
    });
  });
});
