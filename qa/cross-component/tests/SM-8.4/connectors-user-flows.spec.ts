import {expect} from '@playwright/test';
import {test} from '@fixtures/SM-8.4';
import {
  captureScreenshot,
  captureFailureVideo,
  performBasicAuthPostRequest,
  performBearerTokenAuthPostRequest,
} from '@setup';

test.describe('Connectors User Flow Tests', () => {
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

  test('REST Connector No Auth User Flow', async ({
    page,
    operateHomePage,
    modelerHomePage,
    operateProcessInstancePage,
    modelerCreatePage,
    connectorSettingsPage,
    operateProcessesPage,
    connectorMarketplacePage,
    navigationPage,
  }) => {
    test.slow();
    await test.step('Create New Project with a BPMN Diagram Template', async () => {
      await expect(modelerHomePage.createNewProjectButton).toBeVisible({
        timeout: 200000,
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
        timeout: 180000,
      });
      await modelerCreatePage.enterDiagramName(
        'REST_Connector_No_Auth_Process',
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();

      await expect(modelerCreatePage.marketPlaceButton).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.clickMarketPlaceButton();
      await connectorMarketplacePage.clickSearchForConnectorTextbox();
      await connectorMarketplacePage.fillSearchForConnectorTextbox(
        'REST Connector',
      );
      await sleep(10000);
      await connectorMarketplacePage.downloadConnectorToProject();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickRestConnectorOption();
      await connectorSettingsPage.clickUrlInput();
      await connectorSettingsPage.fillUrlInput(
        'https://camunda.proxy.beeceptor.com/pre-prod/no-auth-test',
      );
      await connectorSettingsPage.clickResultExpressionInput();
      await connectorSettingsPage.fillResultExpressionInput('body');
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(10000);
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await sleep(10000);
      await modelerCreatePage.clickStartInstanceSubButton();
      await modelerCreatePage.instanceStartedAssertion();
    });

    await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(90000);
      await navigationPage.goToOperate();
      await operateHomePage.clickProcessesTab();
      await page.reload();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'REST_Connector_No_Auth_Process',
      );
      await operateProcessInstancePage.completedIconAssertion();

      await expect(page.getByTestId('variables-list')).toBeVisible({
        timeout: 30000,
      });

      await expect(
        (
          await operateProcessInstancePage.connectorResultVariableName('status')
        ).isVisible(),
      ).toBeTruthy();

      await expect(page.getByText('"Awesome!"')).toBeVisible({
        timeout: 60000,
      });
    });
  });

  test('REST Connector Basic Auth User Flow', async ({
    page,
    operateHomePage,
    modelerHomePage,
    modelerCreatePage,
    connectorSettingsPage,
    navigationPage,
    operateProcessesPage,
    operateProcessInstancePage,
  }) => {
    test.slow();
    await test.step('Open Web Modeler Project and Create a BPMN Diagram Template', async () => {
      await modelerHomePage.clickConnectorsProjectFolder();
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with REST Connector with Basic Auth and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName(
        'REST_Connector_Basic_Auth_Process',
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickRestConnectorOption();
      await connectorSettingsPage.selectAuthenticationType('basic');
      await connectorSettingsPage.clickUsernameInput();
      await connectorSettingsPage.fillUsernameInput('username');
      await connectorSettingsPage.clickPasswordInput();
      await connectorSettingsPage.fillPasswordInput('password');
      await expect(connectorSettingsPage.methodTypeDropdown).toBeVisible({
        timeout: 30000,
      });
      await connectorSettingsPage.selectMethodType('POST');
      await connectorSettingsPage.clickUrlInput();
      await connectorSettingsPage.fillUrlInput(
        'https://camunda.proxy.beeceptor.com/pre-prod/basic-auth-test',
      );
      await connectorSettingsPage.clickResultVariableInput();
      await connectorSettingsPage.fillResultVariableInput('result');
      await connectorSettingsPage.clickResultExpressionInput();
      await connectorSettingsPage.fillResultExpressionInput(
        '{message:response.body.message}',
      );
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(5000);
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await modelerCreatePage.clickStartInstanceSubButton();
      await modelerCreatePage.instanceStartedAssertion();
      await performBasicAuthPostRequest(
        'https://camunda.proxy.beeceptor.com/pre-prod/basic-auth-test',
        'username',
        'password',
      );
    });

    await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(90000);
      await navigationPage.goToOperate();
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'REST_Connector_Basic_Auth_Process',
      );

      await operateProcessInstancePage.completedIconAssertion();

      await expect(page.getByTestId('variables-list')).toBeVisible({
        timeout: 60000,
      });

      await expect(
        (
          await operateProcessInstancePage.connectorResultVariableName(
            'message',
          )
        ).isVisible(),
      ).toBeTruthy();

      await expect(
        page.getByTestId('variable-message').getByText('"Message from Mock!"'),
      ).toBeVisible({timeout: 60000});
    });
  });

  test('REST Connector Bearer Token Auth User Flow', async ({
    page,
    operateHomePage,
    modelerHomePage,
    modelerCreatePage,
    connectorSettingsPage,
    navigationPage,
    operateProcessInstancePage,
    operateProcessesPage,
  }) => {
    test.slow();
    await test.step('Open Web Modeler Project and Create a BPMN Diagram Template', async () => {
      await modelerHomePage.clickConnectorsProjectFolder();
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 90000,
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
      await modelerCreatePage.clickStartEventElement();
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
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(10000);
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await sleep(5000);
      await modelerCreatePage.clickStartInstanceSubButton();
      await modelerCreatePage.instanceStartedAssertion();
      await performBearerTokenAuthPostRequest(
        'https://camunda.proxy.beeceptor.com/pre-prod/bearer-auth-test',
        'thisisabearertoken',
      );
    });

    await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await navigationPage.goToOperate();
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'REST_Connector_Bearer_Auth_Process',
      );
      await operateProcessInstancePage.completedIconAssertion();

      await expect(page.getByTestId('variables-list')).toBeVisible({
        timeout: 90000,
      });

      await expect(
        (
          await operateProcessInstancePage.connectorResultVariableName(
            'message',
          )
        ).isVisible(),
      ).toBeTruthy();

      await expect(page.getByText('"Awesome!"')).toBeVisible({
        timeout: 60000,
      });
    });
  });

  test('Start Event Webhook Connector No Auth User Flow', async ({
    page,
    modelerHomePage,
    navigationPage,
    modelerCreatePage,
    request,
    operateHomePage,
    operateProcessInstancePage,
    operateProcessesPage,
    connectorMarketplacePage,
  }) => {
    await test.step('Open Web Modeler Project and Create a BPMN Diagram Template', async () => {
      await modelerHomePage.clickConnectorsProjectFolder();
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with REST Connector and Deploy Diagram', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.enterDiagramName(
        'Start_Event_Webhook_Connector_No_Auth_Process',
      );
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickChangeTypeButton();
      await expect(modelerCreatePage.marketPlaceButton).toBeVisible({
        timeout: 90000,
      });
      await modelerCreatePage.clickMarketPlaceButton();
      await connectorMarketplacePage.clickSearchForConnectorTextbox();
      await connectorMarketplacePage.fillSearchForConnectorTextbox(
        'Webhook Connector',
      );
      await sleep(10000);
      await connectorMarketplacePage.downloadConnectorToProject();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickWebhookStartEventConnectorOption();
      await modelerCreatePage.clickWebhookIdInput();
      await modelerCreatePage.clearWebhookIdInput();
      await modelerCreatePage.fillWebhookIdInput('test-webhook-id');
      await sleep(10000);
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(10000);
      await expect(modelerCreatePage.deployMainButton).toBeVisible({
        timeout: 60000,
      });
      await modelerCreatePage.clickDeployMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await modelerCreatePage.clickDeploySubButton();
      await expect(page.getByText('Diagram Deployed!')).toBeVisible({
        timeout: 180000,
      });
    });

    await test.step('Make Authorization Request', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);

      const baseURL =
        process.env.PLAYWRIGHT_BASE_URL ||
        `http://gke-${process.env.BASE_URL}.ci.distro.ultrawombat.com`;

      const response = await request.post(
        `${baseURL}/connectors/inbound/test-webhook-id`,
      );

      await expect(response.status()).toBe(200);
    });

    await test.step('Assert Diagram Has Successfully Completed in Operate', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await navigationPage.goToOperate();
      await operateHomePage.clickProcessesTab();
      await page.reload();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'Start_Event_Webhook_Connector_No_Auth_Process',
      );
      await operateProcessInstancePage.completedIconAssertion();
    });
  });

  test('Connector Secrets User Flow', async ({
    page,
    operateHomePage,
    modelerHomePage,
    navigationPage,
    modelerCreatePage,
    connectorSettingsPage,
    operateProcessInstancePage,
    operateProcessesPage,
  }) => {
    test.slow();
    await test.step('Create New Project with a BPMN Diagram Template', async () => {
      await modelerHomePage.clickConnectorsProjectFolder();
      await expect(modelerHomePage.diagramTypeDropdown).toBeVisible({
        timeout: 60000,
      });
      await modelerHomePage.clickDiagramTypeDropdown();
      await modelerHomePage.clickBpmnTemplateOption();
    });

    await test.step('Create BPMN Diagram with REST Connector using secrets and Start Process Instance', async () => {
      await expect(modelerCreatePage.generalPanel).toBeVisible({
        timeout: 120000,
      });
      await modelerCreatePage.enterDiagramName('REST_Connector_Process');
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickRestConnectorOption();
      await connectorSettingsPage.selectAuthenticationType('basic');
      await expect(connectorSettingsPage.usernameInput).toBeVisible({
        timeout: 60000,
      });
      await connectorSettingsPage.clickUsernameInput();
      await connectorSettingsPage.fillUsernameInput('{{secrets.username}}');
      await connectorSettingsPage.clickPasswordInput();
      await connectorSettingsPage.fillPasswordInput('{{secrets.password}}');
      await connectorSettingsPage.selectMethodType('POST');
      await connectorSettingsPage.clickUrlInput();
      await connectorSettingsPage.fillUrlInput(
        'https://camunda.proxy.beeceptor.com/pre-prod/basic-auth-test',
      );
      await expect(connectorSettingsPage.resultVariableInput).toBeVisible({
        timeout: 120000,
      });
      await connectorSettingsPage.clickResultVariableInput();
      await connectorSettingsPage.fillResultVariableInput('result');
      await connectorSettingsPage.clickResultExpressionInput();
      await connectorSettingsPage.fillResultExpressionInput(
        '{message:response.body.message}',
      );
      await modelerCreatePage.clickAppendEndEventButton();
      await sleep(10000);
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 90000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await modelerCreatePage.clickStartInstanceSubButton();
      await modelerCreatePage.instanceStartedAssertion();

      await performBasicAuthPostRequest(
        'https://camunda.proxy.beeceptor.com/pre-prod/basic-auth-test',
        'username',
        'password',
      );
    });

    await test.step('View Process Instance in Operate, assert it completes and assert result expression', async () => {
      await navigationPage.goToOperate();
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);
      await page.reload();
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'REST_Connector_Process',
      );

      await operateProcessInstancePage.completedIconAssertion();

      await page.reload();
      await expect(page.getByTestId('variables-list')).toBeVisible({
        timeout: 90000,
      });
      await expect(
        page.getByTestId('variable-message').getByText('"Message from Mock!"'),
      ).toBeVisible({timeout: 180000});
    });
  });

  test('Intermediate Event Webhook Connector No Auth User Flow', async ({
    page,
    modelerHomePage,
    modelerCreatePage,
    request,
    operateHomePage,
    operateProcessInstancePage,
    operateProcessesPage,
    navigationPage,
  }) => {
    await test.step('Open Web Modeler Project and Create a BPMN Diagram Template', async () => {
      await navigationPage.goToModeler();
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
      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickIntermediateBoundaryEvent();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickIntermediateWebhookConnectorOption();
      await modelerCreatePage.clickWebhookIdInput();
      await modelerCreatePage.clearWebhookIdInput();
      await modelerCreatePage.fillWebhookIdInput('test-webhook-intermediate');
      await modelerCreatePage.clickCorrelationKeyProcessInput;
      await modelerCreatePage.fillCorrelationKeyProcessInput('"test"');
      await modelerCreatePage.clickCorrelationKeyPayloadInput;
      await modelerCreatePage.fillCorrelationKeyPayloadInput('"test"');
      await modelerCreatePage.clickAppendEndEventButton();

      await sleep(10000);
      await expect(modelerCreatePage.startInstanceMainButton).toBeVisible({
        timeout: 90000,
      });
      await modelerCreatePage.clickStartInstanceMainButton();
      await modelerCreatePage.completeDeploymentEndpointConfiguration();
      await modelerCreatePage.clickStartInstanceSubButton();
      await expect(page.getByText('Instance started!')).toBeVisible({
        timeout: 180000,
      });
    });

    await test.step('Make Authorization Request', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(60000);

      const baseURL =
        process.env.PLAYWRIGHT_BASE_URL ||
        `http://gke-${process.env.BASE_URL}.ci.distro.ultrawombat.com`;

      const response = await request.post(
        `${baseURL}/connectors/inbound/test-webhook-intermediate`,
        {
          data: {
            test: 'test',
          },
        },
      );

      await expect(response.status()).toBe(200);
    });

    await test.step('Assert Diagram Has Successfully Completed in Operate', async () => {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(90000);
      await navigationPage.goToOperate();
      await expect(operateHomePage.processesTab).toBeVisible({timeout: 120000});
      await operateHomePage.clickProcessesTab();
      await page.reload();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.clickProcessInstanceLink(
        'Intermediate_Event_Webhook_Connector_No_Auth_Process',
      );
      await operateProcessInstancePage.completedIconAssertion();
    });
  });
});
