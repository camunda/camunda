import {Page, Locator, expect} from '@playwright/test';
import {ConnectorMarketplacePage} from './ConnectorMarketplacePage';

class ModelerCreatePage {
  private page: Page;
  readonly generalPanel: Locator;
  readonly processIdInput: Locator;
  readonly startEventElement: Locator;
  readonly appendTaskButton: Locator;
  readonly changeTypeButton: Locator;
  readonly userTaskOption: Locator;
  readonly appendEndEventButton: Locator;
  readonly startInstanceMainButton: Locator;
  readonly startInstanceSubButton: Locator;
  readonly viewProcessInstanceLink: Locator;
  readonly nameInput: Locator;
  readonly diagramBreadcrumb: Locator;
  readonly editDiagramNameButton: Locator;
  readonly diagramNameInput: Locator;
  readonly variableInput: Locator;
  readonly embedFormButton: Locator;
  readonly embedButton: Locator;
  readonly newForm: Locator;
  readonly deployMainButton: Locator;
  readonly deploySubButton: Locator;
  readonly cancelButton: Locator;
  readonly restConnectorOption: Locator;
  readonly marketPlaceButton: Locator;
  readonly clusterEndpointTextbox: Locator;
  readonly clientIdTextbox: Locator;
  readonly clientSecretTextbox: Locator;
  readonly oauthUrlTextbox: Locator;
  readonly audienceTextbox: Locator;
  readonly rememberCredentialsCheckbox: Locator;
  readonly oAuthCheckbox: Locator;
  readonly webhookStartEventConnectorOption: Locator;
  readonly webhookIdInput: Locator;
  readonly intermediateBoundaryEvent: Locator;
  readonly intermediateWebhookConnectorOption: Locator;
  readonly correlationKeyProcessInput: Locator;
  readonly correlationKeyPayloadInput: Locator;

  constructor(page: Page) {
    this.page = page;
    this.generalPanel = page.getByTitle('General').first();
    this.processIdInput = page.getByLabel('ID', {exact: true});
    this.startEventElement = page.locator('[data-element-id="StartEvent_1"]');
    this.appendTaskButton = page.getByTitle('Append task');
    this.changeTypeButton = page.getByTitle('Change type').first();
    this.userTaskOption = page.getByRole('listitem', {name: 'User Task'});
    this.appendEndEventButton = page.getByTitle('Append EndEvent');
    this.startInstanceMainButton = page.getByRole('button', {name: 'Run'});
    this.startInstanceSubButton = page
      .getByLabel('Start instance')
      .getByRole('button', {name: 'Run'});
    this.viewProcessInstanceLink = page.getByRole('link', {
      name: 'View process instance',
    });
    this.nameInput = page.getByLabel('Name', {exact: true});
    this.diagramBreadcrumb = page.locator('[data-test="breadcrumb-diagram"]');
    this.editDiagramNameButton = page.getByText('Edit name');
    this.diagramNameInput = page.locator('[data-test="editable-input"]');
    this.variableInput = page.getByLabel('Variables');
    this.embedFormButton = page.getByRole('button', {
      name: 'Click to embed a form',
    });
    this.embedButton = page.locator('[data-test="confirm-move"]');
    this.newForm = page.locator('[data-test="item-New Form"]');
    this.deployMainButton = page.locator('[data-test="deploy-button"]');
    this.deploySubButton = page
      .getByLabel('Deploy diagram')
      .getByRole('button', {name: 'Deploy'});
    this.cancelButton = page.getByRole('button', {name: 'Cancel'});
    this.restConnectorOption = page.getByRole('listitem', {
      name: 'REST Outbound Connector',
    });
    this.marketPlaceButton = page.getByTitle(
      'Browse Marketplace for more Connectors',
    );
    this.clusterEndpointTextbox = page.getByLabel('Cluster endpoint');
    this.clientIdTextbox = page.getByLabel('Client ID');
    this.clientSecretTextbox = page.getByLabel('Client secret');
    this.oauthUrlTextbox = page.getByLabel('OAuth URL *');
    this.audienceTextbox = page.getByLabel('Audience *');
    this.rememberCredentialsCheckbox = page.getByText('Remember credentials');
    this.oAuthCheckbox = page.getByLabel('OAuth', {exact: true});
    this.webhookStartEventConnectorOption = page.getByRole('listitem', {
      name: 'Webhook Start Event Connector',
      exact: true,
    });
    this.webhookIdInput = page.getByLabel('Webhook ID');
    this.intermediateBoundaryEvent = page.getByTitle(
      'Append intermediate/boundary',
    );
    this.intermediateWebhookConnectorOption = page.getByRole('listitem', {
      name: 'Webhook Intermediate Event',
    });
    this.correlationKeyPayloadInput = page
      .getByText(/^Correlation key \(payload\)=Opened in editor$/)
      .getByRole('textbox');
    this.correlationKeyProcessInput = page
      .getByText(/^Correlation key \(process\)=Opened in editor$/)
      .getByRole('textbox');
  }

  async clickGeneralPropertiesPanel(): Promise<void> {
    await this.generalPanel.click();
  }

  async clickProcessIdInput(): Promise<void> {
    await this.processIdInput.click();
  }

  async fillProcessIdInput(id: string): Promise<void> {
    await this.processIdInput.fill(id);
  }

  async clickStartEventElement(): Promise<void> {
    await this.startEventElement.click({force: true});
  }

  async clickAppendTaskButton(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.appendTaskButton.click({force: true, timeout: 60000});
        } else {
          await this.startEventElement.click({force: true, timeout: 30000});
          await this.appendTaskButton.click({force: true, timeout: 60000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }
    throw new Error(`Failed to click the button after ${maxRetries} attempts.`);
  }

  async clickChangeTypeButton(): Promise<void> {
    try {
      await this.changeTypeButton.click({timeout: 180000, force: true});
    } catch {
      await this.page.reload();
      await this.changeTypeButton.click({timeout: 180000, force: true});
    }
  }

  async clickUserTaskOption(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.userTaskOption.click({timeout: 30000, force: true});
        } else {
          await this.changeTypeButton.click({force: true, timeout: 30000});
          await this.userTaskOption.click({timeout: 30000, force: true});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(`Failed to click the button after ${maxRetries} attempts.`);
  }

  async clickAppendEndEventButton(retries: number = 3): Promise<void> {
    for (let i = 0; i < retries; i++) {
      try {
        await this.appendEndEventButton.click({timeout: 90000, force: true});
        return; // Click succeeded, exit the loop
      } catch (error) {
        console.error(
          `Attempt ${i + 1} to click deploy button failed: ${error}`,
        );
        // Wait for 10 seconds
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(10000);
      }
    }
    throw new Error(`Failed to click deploy button after ${retries} retries`);
  }

  async clickStartInstanceMainButton(): Promise<void> {
    await this.startInstanceMainButton.click({timeout: 120000});
  }

  async clickStartInstanceSubButton(): Promise<void> {
    await this.startInstanceSubButton.click({timeout: 120000});
  }

  async clickViewProcessInstanceLink(): Promise<void> {
    await this.viewProcessInstanceLink.click({timeout: 90000});
  }

  async clickNameInput(): Promise<void> {
    await this.nameInput.click();
  }

  async fillNamedInput(name: string): Promise<void> {
    await this.nameInput.fill(name);
  }

  async clickDiagramBreadcrumb(): Promise<void> {
    await this.diagramBreadcrumb.click({timeout: 60000});
  }

  async clickEditDiagramNameButton(): Promise<void> {
    await this.editDiagramNameButton.click();
  }

  async enterDiagramName(name: string): Promise<void> {
    await this.diagramNameInput.fill(name, {timeout: 60000});
    await this.diagramNameInput.press('Enter', {timeout: 60000});
  }

  async clickVariableInput(): Promise<void> {
    await this.variableInput.click();
  }

  async fillVariableInput(variable: string): Promise<void> {
    await this.variableInput.fill(variable);
  }

  async clickEmbedFormButton(): Promise<void> {
    await this.embedFormButton.click({timeout: 90000});
  }

  async clickEmbedButton(): Promise<void> {
    await this.embedButton.click();
  }

  async clickNewForm(): Promise<void> {
    await this.newForm.click({timeout: 60000});
  }

  async clickDeployMainButton(): Promise<void> {
    await this.deployMainButton.click({timeout: 90000});
  }

  async clickDeploySubButton(): Promise<void> {
    await this.deploySubButton.click({timeout: 90000});
  }

  async clickCancelButton(): Promise<void> {
    await this.cancelButton.click();
  }

  async clickRestConnectorOption(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          // First attempt
          await this.restConnectorOption.click({timeout: 60000});
        } else if (retries === 1) {
          // Second attempt
          await this.changeTypeButton.click({force: true, timeout: 60000});
          await this.restConnectorOption.click({timeout: 90000});
        } else {
          // Third and subsequent attempts
          await this.clickMarketPlaceButton();
          const connectorMarketplacePage = new ConnectorMarketplacePage(
            this.page,
          );
          await connectorMarketplacePage.clickSearchForConnectorTextbox();
          await connectorMarketplacePage.fillSearchForConnectorTextbox(
            'REST Connector',
          );
          const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
          await sleep(10000);
          await connectorMarketplacePage.downloadConnectorToProject();
          await this.clickChangeTypeButton();
          await this.restConnectorOption.click({timeout: 120000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(`Failed to click the button after ${maxRetries} attempts.`);
  }

  async clickMarketPlaceButton(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.marketPlaceButton.click({timeout: 30000});
        } else {
          await this.changeTypeButton.click({force: true, timeout: 30000});
          await this.marketPlaceButton.click({timeout: 30000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(`Failed to click the button after ${maxRetries} attempts.`);
  }

  async clickClusterEndpointTextbox(): Promise<void> {
    await this.clusterEndpointTextbox.click();
  }

  async fillClusterEndpointTextbox(clusterEndpoint: string): Promise<void> {
    await this.clusterEndpointTextbox.fill(clusterEndpoint);
  }

  async completeDeploymentEndpointConfiguration(): Promise<void> {
    await this.oAuthCheckbox.click({timeout: 30000});
    await this.clusterEndpointTextbox.click({timeout: 30000});
    await this.clusterEndpointTextbox.fill(process.env.CLUSTER_ENDPOINT!, {
      timeout: 30000,
    });
    await expect(this.clientIdTextbox).toBeVisible({timeout: 30000});
    await this.clientIdTextbox.click({timeout: 30000});
    await this.clientIdTextbox.fill('test', {timeout: 30000});
    await this.clientSecretTextbox.click({timeout: 30000});
    await this.clientSecretTextbox.fill(
      process.env.DISTRO_QA_E2E_TESTS_KEYCLOAK_CLIENTS_SECRET!,
    );
    await this.oauthUrlTextbox.click({timeout: 30000});
    await this.oauthUrlTextbox.fill(process.env.OAUTH_URL!);
    await this.audienceTextbox.click({timeout: 30000});
    await this.audienceTextbox.fill('zeebe-api');
    await this.rememberCredentialsCheckbox.click({timeout: 30000});
  }

  async clickWebhookStartEventConnectorOption(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.webhookStartEventConnectorOption.click({timeout: 30000});
        } else {
          await this.changeTypeButton.click({force: true, timeout: 30000});
          await this.webhookStartEventConnectorOption.click({timeout: 90000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(`Failed to click the button after ${maxRetries} attempts.`);
  }

  async clickWebhookIdInput(): Promise<void> {
    await this.webhookIdInput.click();
  }

  async clearWebhookIdInput(): Promise<void> {
    await this.webhookIdInput.clear();
  }

  async fillWebhookIdInput(webhookId: string): Promise<void> {
    await this.webhookIdInput.fill(webhookId);
  }

  async hoverOnStartEvent(): Promise<void> {
    await this.startEventElement.hover();
  }

  async instanceStartedAssertion(): Promise<void> {
    try {
      await expect(this.page.getByText('Instance started!')).toBeVisible({
        timeout: 180000,
      });
    } catch {
      await this.clickStartInstanceMainButton();
      await this.completeDeploymentEndpointConfiguration();
      await this.clickStartInstanceSubButton();
      await expect(this.page.getByText('Instance started!')).toBeVisible({
        timeout: 180000,
      });
    }
  }

  async clickIntermediateBoundaryEvent(): Promise<void> {
    await this.intermediateBoundaryEvent.click({timeout: 60000});
  }

  async clickIntermediateWebhookConnectorOption(): Promise<void> {
    await this.intermediateWebhookConnectorOption.click({timeout: 60000});
  }

  async clickCorrelationKeyProcessInput(): Promise<void> {
    await this.correlationKeyProcessInput.click({timeout: 60000});
  }

  async fillCorrelationKeyProcessInput(
    correlationKeyProcess: string,
  ): Promise<void> {
    await this.correlationKeyProcessInput.fill(correlationKeyProcess);
  }

  async clickCorrelationKeyPayloadInput(): Promise<void> {
    await this.correlationKeyPayloadInput.click({timeout: 60000});
  }

  async fillCorrelationKeyPayloadInput(
    correlationKeyPayload: string,
  ): Promise<void> {
    await this.correlationKeyPayloadInput.fill(correlationKeyPayload);
  }
}

export {ModelerCreatePage};
