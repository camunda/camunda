import {Page, Locator, expect} from '@playwright/test';

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
  readonly implementationSection: Locator;
  readonly implementationOptions: Locator;
  readonly intermediateBoundaryEvent: Locator;
  readonly intermediateWebhookConnectorOption: Locator;
  readonly correlationKeyProcessInput: Locator;
  readonly correlationKeyPayloadInput: Locator;
  readonly candidateUsersInput: Locator;
  readonly assigneeInput: Locator;
  readonly appendGatewayButton: Locator;
  readonly parallelGatewayOption: Locator;
  readonly assignmentSection: Locator;
  readonly priorityInput: Locator;
  readonly firstPlacedGateway: Locator;
  readonly secondPlacedGateway: Locator;
  readonly connectToOtherElementButton: Locator;
  readonly canvas: Locator;
  readonly appendElementButton: Locator;
  readonly firstElement: Locator;
  readonly secondElement: Locator;
  readonly changeElementHeading: Locator;
  readonly candidateGroupsInput: Locator;

  constructor(page: Page) {
    this.page = page;
    this.generalPanel = page.getByTitle('General').first();
    this.processIdInput = page.getByLabel('ID', {exact: true});
    this.startEventElement = page.locator('[data-element-id="StartEvent_1"]');
    this.appendTaskButton = page.getByTitle('Append task');
    this.changeTypeButton = page.getByTitle('Change element').first();
    this.userTaskOption = page.getByRole('listitem', {name: 'User Task'});
    this.appendEndEventButton = page.getByTitle('Append end event');
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
      name: 'Click to add a form',
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
    this.oauthUrlTextbox = page.getByLabel('OAuth Token URL');
    this.audienceTextbox = page.getByLabel('OAuth Audience');
    this.rememberCredentialsCheckbox = page.getByText('Remember credentials');
    this.oAuthCheckbox = page.getByLabel('OAuth', {exact: true});
    this.webhookStartEventConnectorOption = page.getByRole('listitem', {
      name: 'Webhook Start Event Connector',
      exact: true,
    });
    this.webhookIdInput = page.getByLabel('Webhook ID');
    this.implementationSection = page.getByText('implementation');
    this.implementationOptions = page.locator(
      '#bio-properties-panel-userTaskImplementation',
    );
    this.intermediateBoundaryEvent = page.getByTitle(
      'Append intermediate/boundary',
    );
    this.intermediateWebhookConnectorOption = page.getByRole('listitem', {
      name: 'Webhook Intermediate Event',
    });
    this.correlationKeyPayloadInput = page.getByLabel(
      'Correlation key (payload)',
    );
    this.correlationKeyProcessInput = page.getByLabel(
      'Correlation key (process)',
    );
    this.candidateUsersInput = page.getByRole('textbox', {
      name: 'candidate users',
    });
    this.assigneeInput = page.getByRole('textbox', {name: 'assignee'});
    this.appendGatewayButton = page.getByTitle('append gateway');
    this.parallelGatewayOption = page.getByText('parallel gateway');
    this.implementationSection = page.locator(
      '[data-group-id="group-userTaskImplementation"]',
    );
    this.assignmentSection = page.locator(
      '[data-group-id="group-assignmentDefinition"]',
    );
    this.priorityInput = page.getByRole('textbox', {name: 'priority'});
    this.firstPlacedGateway = page
      .locator('[data-element-id*="Gateway"]')
      .first();
    this.secondPlacedGateway = page
      .locator('[data-element-id*="Gateway"]')
      .last();
    this.connectToOtherElementButton = page.getByTitle(
      'Connect to other element',
    );

    this.canvas = page.locator('rect').nth(1);
    this.appendElementButton = page
      .locator('[class="djs-create-pad open djs-append-create-pad"]')
      .first();
    this.firstElement = page.locator('[class="djs-element djs-shape"]').first();
    this.secondElement = page.locator('[class="djs-element djs-shape"]').last();
    this.changeElementHeading = page.getByRole('heading', {
      name: 'change element',
    });
    this.candidateGroupsInput = page.getByRole('textbox', {
      name: 'candidate groups',
    });
  }

  async clickGeneralPropertiesPanel(): Promise<void> {
    const isExpanded =
      (await this.generalPanel
        .locator('.bio-properties-panel-group-header.open')
        .count()) > 0;

    if (!isExpanded) {
      await this.generalPanel.click({timeout: 60000});
    }
  }

  async expandAssignmentSection(): Promise<void> {
    const isExpanded =
      (await this.assignmentSection

        .locator(
          '[data-group-id="group-assignmentDefinition"] .bio-properties-panel-group-header.open',
        )

        .count()) > 0;

    if (!isExpanded) {
      await this.assignmentSection.click({timeout: 60000});
    }
  }

  async clickIdInput(): Promise<void> {
    await this.processIdInput.click();
  }

  async fillIdInput(id: string): Promise<void> {
    await this.processIdInput.fill(id);
  }

  async clickStartEventElement(): Promise<void> {
    await this.startEventElement.dblclick({force: true});
  }

  async selectStartEventElement(): Promise<void> {
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

  async clickAppendEndEventButton(
    parentElement?: string,
    retries: number = 3,
  ): Promise<void> {
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
        if (parentElement == undefined) {
          await this.clickSecondPlacedElement();
        } else if (parentElement == 'parallelGateway') {
          await this.clickSecondPlacedGateway();
        }
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

  async fillNameInput(name: string): Promise<void> {
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
          await this.restConnectorOption.click({timeout: 30000});
        } else {
          await this.changeTypeButton.click({force: true, timeout: 30000});
          await this.restConnectorOption.click({timeout: 90000});
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
  async chooseImplementationOption(implementationType: string): Promise<void> {
    const isExpanded =
      (await this.implementationSection
        .locator('.bio-properties-panel-group-header open')
        .count()) > 0;

    if (!isExpanded) {
      await this.implementationSection.click();
    }
    await this.implementationOptions.selectOption(implementationType);
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

  async selectUserTask(userTask: string): Promise<void> {
    await this.page.locator(`[data-element-id="${userTask}"]`).click();
  }

  async clickCandidateUsersInput(): Promise<void> {
    await this.candidateUsersInput.click({timeout: 60000});
  }

  async fillCandidateUsersInput(candidateUser: string): Promise<void> {
    await this.candidateUsersInput.fill(candidateUser, {timeout: 60000});
  }

  async clickAssigneeInput(): Promise<void> {
    await this.assigneeInput.click({timeout: 60000});
  }

  async fillAssigneeInput(name: string): Promise<void> {
    await this.assigneeInput.fill(name, {timeout: 60000});
  }

  async addParallelUserTasks(
    numberOfTasks: number,
    taskName: string,
  ): Promise<void> {
    await this.appendGatewayButton.click();
    await this.clickChangeTypeButton();
    await this.clickParallelGatewayOption();
    await this.clickAppendTaskButton();
    await this.clickChangeTypeButton();
    await this.clickUserTaskOption();
    await this.clickGeneralPropertiesPanel();
    await this.clickNameInput();
    await this.fillNameInput(taskName + '1');
    await this.clickIdInput();
    await this.fillIdInput(taskName + '1');
    await this.chooseImplementationOption('zeebeUserTask');
    await this.clickAppendGatewayButton();
    await this.clickChangeTypeButton();
    await this.clickParallelGatewayOption();
    for (let i = 2; i <= numberOfTasks; i++) {
      await this.clickFirstPlacedGateway();
      await this.clickAppendTaskButton();
      await this.clickChangeTypeButton();
      await this.clickUserTaskOption();
      await this.clickNameInput();
      await this.fillNameInput(taskName + i);
      await this.clickIdInput();
      await this.fillIdInput(taskName + i);
      await this.chooseImplementationOption('zeebeUserTask');
      await this.clickConnectToOtherElementButton();
      await this.clickSecondPlacedGateway();
      await this.clickCanvas();
    }
    await this.clickSecondPlacedGateway();
  }

  async clickAppendGatewayButton(): Promise<void> {
    await this.appendGatewayButton.click({timeout: 90000});
  }

  async clickFirstPlacedElement(): Promise<void> {
    await this.firstElement.click({timeout: 90000});
  }

  async clickSecondPlacedElement(): Promise<void> {
    await this.secondElement.click({timeout: 60000});
  }

  async clickFirstPlacedGateway(): Promise<void> {
    await this.firstPlacedGateway.click({timeout: 60000});
  }

  async clickSecondPlacedGateway(): Promise<void> {
    await this.secondPlacedGateway.click({timeout: 60000});
  }

  async clickParallelGatewayOption(): Promise<void> {
    try {
      await this.parallelGatewayOption.click({timeout: 90000});
    } catch (error) {
      console.error(`Click attempt  failed: ${error}`);
      await this.clickChangeTypeButton();
      await expect(this.changeElementHeading).toBeVisible({timeout: 60000});
      await this.parallelGatewayOption.click({timeout: 90000});
    }
  }

  async clickConnectToOtherElementButton(): Promise<void> {
    await this.connectToOtherElementButton.click({timeout: 60000});
  }

  async clickPriorityInput(): Promise<void> {
    try {
      await this.priorityInput.click();
    } catch (error) {
      await this.assignmentSection.click({timeout: 60000});

      await this.priorityInput.click({timeout: 60000});
    }
  }

  async fillPriorityInput(priority: string): Promise<void> {
    await this.priorityInput.fill(priority, {timeout: 60000});
  }

  async clickCanvas(): Promise<void> {
    await this.canvas.click({timeout: 60000});
  }

  async clickCandidateGroupsInput(): Promise<void> {
    await this.candidateGroupsInput.click({timeout: 60000});
  }

  async fillCandidateGroupsInput(candidateGroup: string): Promise<void> {
    await this.candidateGroupsInput.fill(candidateGroup, {timeout: 60000});
  }
}

export {ModelerCreatePage};
