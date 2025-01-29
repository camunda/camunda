import {Page, Locator, expect} from '@playwright/test';
import {sleep} from '../../utils/sleep';

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
  readonly webhookStartEventConnectorOption: Locator;
  readonly webhookTab: Locator;
  readonly webhookIsActiveButton: Locator;
  readonly copyWebhookUrlToClipboardButton: Locator;
  readonly createTask: Locator;
  readonly createEndEvent: Locator;
  readonly canvas: Locator;
  readonly endEventCanvas: Locator;
  readonly connectToOtherElementButton: Locator;
  readonly firstPlacedElement: Locator;
  readonly secondPlacedElement: Locator;
  readonly appendElementButton: Locator;
  readonly payloadInput: Locator;
  readonly marketPlaceButton: Locator;
  readonly publicHolidayConnectorOption: Locator;
  readonly publicHolidayYearOption: Locator;
  readonly publicHolidayCountryCodeOption: Locator;
  readonly implementationSection: Locator;
  readonly implementationOptions: Locator;
  readonly intermediateBoundaryEvent: Locator;
  readonly intermediateWebhookConnectorOption: Locator;
  readonly correlationKeyProcessInput: Locator;
  readonly correlationKeyPayloadInput: Locator;
  readonly firstElement: Locator;
  readonly secondElement: Locator;
  readonly thirdElement: Locator;
  readonly payloadNameInput: Locator;
  readonly payloadNumberInput: Locator;
  readonly payloadCheckboxInput: Locator;
  readonly payloadDateInput: Locator;
  readonly form: Locator;
  readonly formPropertySection: Locator;
  readonly formLinkingTypeDropdown: Locator;
  readonly formLinkTypeOptions: Locator;
  readonly addFormJsonConfigurationInput: Locator;
  readonly closeButton: Locator;
  readonly rbaEnabledDeployedDialog: Locator;
  readonly viewProcessLink: Locator;
  readonly appendGatewayButton: Locator;
  readonly parallelGatewayOption: Locator;
  readonly firstPlacedGateway: Locator;
  readonly secondPlacedGateway: Locator;
  readonly serviceTaskOption: Locator;
  readonly taskDefinitionPanel: Locator;
  readonly jobTypeInput: Locator;
  readonly playTab: Locator;
  readonly continueToPlayButton: Locator;
  readonly assigneeInput: Locator;
  readonly candidateGroupsInput: Locator;
  readonly candidateUsersInput: Locator;
  readonly assignmentSection: Locator;

  constructor(page: Page) {
    this.page = page;
    this.generalPanel = page.getByTitle('General').first();
    this.processIdInput = page.getByLabel('ID', {exact: true});
    this.startEventElement = page.locator('.djs-hit').first();
    this.appendElementButton = page
      .locator('[class="djs-create-pad open djs-append-create-pad"]')
      .first();
    this.appendTaskButton = page.getByTitle('Append task');
    this.changeTypeButton = page.getByRole('button', {
      name: 'Change element',
      exact: true,
    });
    this.userTaskOption = page
      .getByRole('listitem', {name: 'User task'})
      .locator('span')
      .first();
    this.appendEndEventButton = page.getByTitle('Append end event');
    this.startInstanceMainButton = this.startInstanceMainButton = page
      .getByTitle(
        'Deploys the current process (if not already deployed) and starts a new instance',
      )
      .first();
    this.startInstanceSubButton = page
      .getByRole('button', {name: 'Run'})
      .last();
    this.viewProcessInstanceLink = page.getByRole('link', {
      name: 'View process instance',
    });
    this.viewProcessLink = page.getByRole('link', {
      name: 'View process in Operate',
    });
    this.nameInput = page.getByLabel('Name', {exact: true});
    this.diagramBreadcrumb = page.locator('[data-test="breadcrumb-diagram"]');
    this.editDiagramNameButton = page.getByText('Edit name');
    this.diagramNameInput = page.locator('[data-test="editable-input"]');
    this.variableInput = page.getByLabel('Variables');
    this.embedFormButton = page.getByRole('button', {name: 'Link form'});
    this.embedButton = page.locator('[data-test="confirm-move"]');
    this.newForm = page
      .locator('[data-test="item-New form"]')
      .getByText('New form');
    this.deployMainButton = page.locator('[data-test="deploy-button"]');
    this.deploySubButton = page.locator('[data-test="deploy-action"]');
    this.cancelButton = page.getByRole('button', {name: 'Cancel'});
    this.restConnectorOption = page.getByRole('listitem', {
      name: 'REST Outbound Connector',
    });
    this.webhookStartEventConnectorOption = page.getByRole('listitem', {
      name: 'Webhook Start Event Connector',
      exact: true,
    });
    this.webhookTab = page.getByRole('tab', {name: 'Webhook'});
    this.webhookIsActiveButton = page.getByRole('button', {
      name: 'Webhook is active in Test',
    });
    this.copyWebhookUrlToClipboardButton = page.locator(
      '[aria-label="Copy to clipboard"]',
    );
    this.createTask = page.getByTitle('Create task');
    this.createEndEvent = page.getByTitle('Create end event');
    this.canvas = page.locator('rect').nth(1);
    this.endEventCanvas = page.locator('[class="bjs-container"]');
    this.connectToOtherElementButton = page
      .getByLabel('Connect to other element')
      .locator('path');
    this.firstPlacedElement = page.locator(
      'g:nth-child(2) > .djs-element > .djs-hit',
    );
    this.secondPlacedElement = page
      .locator('[class= "djs-element djs-shape"]')
      .last();
    this.payloadInput = page.locator('[class="fjs-input"]');
    this.marketPlaceButton = page.getByTitle(
      'Browse Marketplace for more Connectors',
    );
    this.publicHolidayConnectorOption = page
      .locator('[data-test="modeler"]')
      .getByText('Worldwide Public Holiday');
    this.publicHolidayYearOption = page.getByLabel('Year');
    this.publicHolidayCountryCodeOption = page.getByLabel('Countrycode');
    this.implementationSection = page.locator(
      '[data-group-id="group-userTaskImplementation"]',
    );
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
    this.form = page.locator('[class="fjs-container"]');
    this.firstElement = page.locator('[class="djs-element djs-shape"]').nth(0);
    this.secondElement = page.locator('[class="djs-element djs-shape"]').nth(1);
    this.thirdElement = page.locator('[class="djs-element djs-shape"]').nth(2);
    this.processIdInput = page.getByLabel('ID', {exact: true});
    this.payloadNameInput = page.getByLabel('Full Name');
    this.payloadDateInput = page.getByLabel('Date of Birth');
    this.payloadCheckboxInput = this.form.getByLabel('Agree');
    this.payloadNumberInput = this.form.getByLabel('Count');
    this.formPropertySection = page.locator('[data-group-id="group-form"]');
    this.formLinkingTypeDropdown = page.getByLabel('Type');
    this.formLinkTypeOptions = page.locator('#bio-properties-panel-formType');
    this.addFormJsonConfigurationInput = page.getByLabel(
      'Form JSON configuration',
    );
    this.closeButton = page.getByRole('button', {name: 'Close'});
    this.rbaEnabledDeployedDialog = page.getByRole('dialog', {
      name: 'Process successfully deployed.',
    });
    this.appendGatewayButton = page.getByTitle('append gateway');
    this.parallelGatewayOption = page.getByText('parallel gateway');
    this.firstPlacedGateway = page
      .locator('[data-element-id*="Gateway"]')
      .first();
    this.secondPlacedGateway = page
      .locator('[data-element-id*="Gateway"]')
      .last();
    this.serviceTaskOption = page.getByRole('listitem', {name: 'Service Task'});
    this.taskDefinitionPanel = page.getByTitle('Task definition').first();
    this.jobTypeInput = page.getByRole('textbox', {name: /job type/i});
    this.playTab = page.getByRole('tab', {
      name: 'Play',
    });
    this.continueToPlayButton = page.getByRole('button', {name: 'Continue'});
    this.assignmentSection = page.locator(
      '[data-group-id="group-assignmentDefinition"]',
    );
    this.assigneeInput = page.getByRole('textbox', {name: 'assignee'});
    this.candidateGroupsInput = page.getByRole('textbox', {
      name: 'candidate groups',
    });
    this.candidateUsersInput = page.getByRole('textbox', {
      name: 'candidate users',
    });
    this.appendGatewayButton = page.getByTitle('append gateway');
    this.parallelGatewayOption = page.getByText('parallel gateway');
    this.firstPlacedGateway = page
      .locator('[data-element-id*="Gateway"]')
      .first();
    this.secondPlacedGateway = page
      .locator('[data-element-id*="Gateway"]')
      .last();
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

  async clickAssigneeInput(): Promise<void> {
    await this.assigneeInput.click({timeout: 60000});
  }

  async fillAssigneeInput(name: string): Promise<void> {
    await this.assigneeInput.fill(name, {timeout: 60000});
  }

  async clickCandidateGroupsInput(): Promise<void> {
    await this.candidateGroupsInput.click({timeout: 60000});
  }

  async fillCandidateGroupsInput(name: string): Promise<void> {
    await this.candidateGroupsInput.fill(name, {timeout: 60000});
  }
  async clickCandidateUsersInput(): Promise<void> {
    await this.candidateUsersInput.click({timeout: 60000});
  }

  async fillCandidateUsersInput(candidateUser: string): Promise<void> {
    await this.candidateUsersInput.fill(candidateUser, {timeout: 60000});
  }

  async selectUserTask(userTask: string): Promise<void> {
    await this.page.locator(`g[data-element-id="${userTask}"]`).click();
  }

  async clickGeneralPropertiesPanel(): Promise<void> {
    await this.generalPanel.click({timeout: 60000});
  }

  async clickProcessIdInput(): Promise<void> {
    await this.processIdInput.click({timeout: 60000});
  }

  async fillProcessIdInput(id: string): Promise<void> {
    await this.processIdInput.fill(id, {timeout: 60000});
  }

  async clickStartEventElement(): Promise<void> {
    await this.startEventElement.click({timeout: 60000});
  }

  async clickAppendTaskButton(): Promise<void> {
    await this.appendTaskButton.click({timeout: 90000});
  }

  async clickChangeTypeButton(): Promise<void> {
    await this.changeTypeButton.click({timeout: 90000});
  }

  async clickUserTaskOption(): Promise<void> {
    await this.userTaskOption.click({timeout: 90000});
  }

  async clickForm(name: string): Promise<void> {
    await this.page
      .locator(`[data-test="item-${name}"]`)
      .getByText(name)
      .click({timeout: 90000});
  }

  async clickUserTask(id: string): Promise<void> {
    const priorityUserTask = this.page.locator(`g[data-element-id="${id}"]`);
    await priorityUserTask.click({timeout: 60000});
  }

  async clickAppendEndEventButton(parentElement?: string): Promise<void> {
    try {
      await this.appendEndEventButton.click({timeout: 120000});
    } catch (error) {
      await this.page.reload();
      await sleep(10000);
      if (parentElement == undefined) {
        await this.clickSecondPlacedElement();
      } else if (parentElement == 'parallelGateway') {
        await this.clickSecondPlacedGateway();
      }
      await this.appendElementButton.hover();
      await this.appendEndEventButton.click({timeout: 120000});
    }
  }

  async clickCloseButton(): Promise<void> {
    await this.closeButton.click({timeout: 60000});
  }

  async deployDiagram(rbaEnabled: boolean) {
    await expect(this.deployMainButton).toBeVisible({
      timeout: 120000,
    });
    await this.clickDeployMainButtonWithRetry();
    await expect(this.deploySubButton).toBeVisible({
      timeout: 120000,
    });
    await this.clickDeploySubButtonWithRetry();
    if (rbaEnabled) {
      await expect(this.rbaEnabledDeployedDialog).toBeVisible({
        timeout: 120000,
      });
      await this.clickCloseButton();
    } else {
      await expect(this.viewProcessLink).toBeVisible({
        timeout: 180000,
      });
      await expect(this.viewProcessLink).toBeHidden({
        timeout: 120000,
      });
    }
  }

  async clickStartInstanceMainButton(): Promise<void> {
    await this.startInstanceMainButton.click({timeout: 90000});
  }

  async clickStartInstanceSubButton(): Promise<void> {
    await this.startInstanceSubButton.click({timeout: 90000});
  }

  async clickViewProcessInstanceLink(): Promise<void> {
    await this.viewProcessInstanceLink.click({timeout: 180000});
  }

  async clickNameInput(): Promise<void> {
    await this.nameInput.click({timeout: 60000});
  }

  async fillNamedInput(name: string): Promise<void> {
    await this.nameInput.fill(name, {timeout: 60000});
  }

  async clickDiagramBreadcrumb(): Promise<void> {
    await this.diagramBreadcrumb.click({timeout: 10000});
  }

  async clickEditDiagramNameButton(): Promise<void> {
    await this.editDiagramNameButton.click();
  }

  async enterDiagramName(name: string): Promise<void> {
    await this.diagramNameInput.fill(name, {timeout: 90000});
    await this.diagramNameInput.press('Enter', {timeout: 90000});
  }

  async clickVariableInput(): Promise<void> {
    await this.variableInput.click({timeout: 60000});
  }

  async fillVariableInput(variable: string): Promise<void> {
    await this.variableInput.fill(variable);
  }

  async clickEmbedFormButton(): Promise<void> {
    await this.embedFormButton.click({timeout: 60000});
  }

  async clickEmbedButton(): Promise<void> {
    await this.embedButton.click({timeout: 60000});
  }

  async clickNewForm(): Promise<void> {
    await this.newForm.click({timeout: 120000});
  }

  async clickDeployMainButton(): Promise<void> {
    await this.deployMainButton.click({timeout: 120000, force: true});
  }

  async clickDeploySubButton(): Promise<void> {
    await this.deploySubButton.click({timeout: 120000, force: true});
  }

  async clickCancelButton(): Promise<void> {
    await this.cancelButton.click({timeout: 60000});
  }

  async clickRestConnectorOption(): Promise<void> {
    await this.restConnectorOption.click({timeout: 60000});
  }

  async clickWebhookStartEventConnectorOption(): Promise<void> {
    await this.webhookStartEventConnectorOption.click({timeout: 60000});
  }

  async clickWebhookTab(): Promise<void> {
    await this.webhookTab.click({timeout: 60000});
  }

  async clickWebhookIsActiveButton(): Promise<void> {
    await this.webhookIsActiveButton.click({timeout: 60000});
  }

  async assertThreeElementsVisible(): Promise<void> {
    await expect(this.firstElement).toBeVisible({timeout: 30000});
    await expect(this.secondElement).toBeVisible({timeout: 30000});
    await expect(this.thirdElement).toBeVisible({timeout: 30000});
  }

  async clickDeployMainButtonWithRetry(retries: number = 3): Promise<void> {
    for (let i = 0; i < retries; i++) {
      try {
        await this.deployMainButton.click({timeout: 120000, force: true});
        return; // Click succeeded, exit the loop
      } catch (error) {
        console.error(
          `Attempt ${i + 1} to click deploy button failed: ${error}`,
        );
        // Refresh the page
        await this.page.reload();
        // Wait for 10 seconds
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(10000);
      }
    }
    throw new Error(`Failed to click deploy button after ${retries} retries`);
  }

  async clickDeploySubButtonWithRetry(retries: number = 3): Promise<void> {
    for (let i = 0; i < retries; i++) {
      try {
        await this.deploySubButton.click({timeout: 120000, force: true});
        return; // Click succeeded, exit the loop
      } catch (error) {
        console.error(
          `Attempt ${i + 1} to click deploy button failed: ${error}`,
        );
        // Refresh the page
        await this.page.reload();
        // Wait for 10 seconds
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(10000);
        await this.clickDeployMainButtonWithRetry();
      }
    }
    throw new Error(`Failed to click deploy button after ${retries} retries`);
  }

  async clickCreateTask(): Promise<void> {
    await this.createTask.click({timeout: 60000});
  }

  async clickCreateEndEvent(): Promise<void> {
    await this.createEndEvent.click({timeout: 60000});
  }

  async clickCanvas(): Promise<void> {
    await this.canvas.click({timeout: 60000});
  }

  async clickEndEventCanvas(): Promise<void> {
    await this.endEventCanvas.click({timeout: 60000});
  }

  async clickConnectToOtherElementButton(): Promise<void> {
    await this.connectToOtherElementButton.click({timeout: 60000});
  }

  async clickFirstPlacedElement(): Promise<void> {
    await this.firstPlacedElement.click({timeout: 60000});
  }

  async clickSecondPlacedElement(): Promise<void> {
    await this.secondPlacedElement.click({timeout: 60000});
  }

  async clickSecondElement(): Promise<void> {
    await this.secondElement.click({timeout: 60000});
  }

  async clickAppendElementButton(): Promise<void> {
    try {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await this.appendElementButton.hover({timeout: 60000, force: true});
    } catch (error) {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await this.appendElementButton.hover({timeout: 90000, force: true});
    }
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

  async clickPublicHolidayConnectorOption(): Promise<void> {
    await this.publicHolidayConnectorOption.click({timeout: 60000});
  }

  async clickPublicHolidayYearOption(): Promise<void> {
    await this.publicHolidayYearOption.click({timeout: 60000});
  }

  async fillPublicHolidayYearOption(year: string): Promise<void> {
    await this.publicHolidayYearOption.fill(year);
  }

  async clickPublicHolidayCountryCodeOption(): Promise<void> {
    await this.publicHolidayCountryCodeOption.click({timeout: 60000});
  }

  async fillPublicHolidayCountryCodeOption(countryCode: string): Promise<void> {
    await this.publicHolidayCountryCodeOption.fill(countryCode);
  }

  async chooseImplementationOption(implementationType: string): Promise<void> {
    await this.implementationSection.click();
    if ((await this.implementationOptions.inputValue()) == implementationType) {
      console.log(`${implementationType} is already selected.`);
    } else {
      await this.implementationOptions.selectOption(implementationType);
    }
  }

  async assertImplementationOption(implementationType: string): Promise<void> {
    await this.expandImplementationOptionIfNecessary();
    expect(await this.implementationOptions.inputValue()).toEqual(
      implementationType,
    );
  }

  private async expandImplementationOptionIfNecessary(): Promise<void> {
    const isExpanded =
      (await this.implementationSection
        .locator('.bio-properties-panel-group-header empty open')
        .count()) > 0;

    if (!isExpanded) {
      await this.implementationSection.click();
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

  async clickIdInput(): Promise<void> {
    await this.processIdInput.click();
  }

  async fillIdInput(id: string): Promise<void> {
    await this.processIdInput.fill(id);
  }

  async fillStartFormName(name: string): Promise<void> {
    await this.payloadNameInput.fill(name);
  }
  async fillStartFormDate(date: string): Promise<void> {
    await this.payloadDateInput.click();
    await this.payloadDateInput.fill(date);
    await this.payloadDateInput.press('Enter');
  }

  async checkStartFormCheckbox(): Promise<void> {
    await this.payloadCheckboxInput.check();
  }

  async fillStartFormNumber(number: string): Promise<void> {
    await this.payloadNumberInput.fill(number);
  }

  async chooseFormLinkingTypeOption(formLinkType: string): Promise<void> {
    await this.formPropertySection.click();

    const jsonConfig = JSON.stringify({
      executionPlatform: 'Camunda Cloud',
      executionPlatformVersion: '8.6.0',
      exporter: {
        name: 'Camunda Web Modeler',
        version: 'ca7603f',
      },
      schemaVersion: 17,
      id: 'Form_0ex0ayi',
      components: [
        {
          label: 'Full Name',
          type: 'textfield',
          id: 'Textfield_0',
          validate: {
            minLength: 2,
            required: true,
          },
          key: 'fullName',
        },
      ],
      generated: true,
      type: 'default',
    });

    await this.formLinkTypeOptions.selectOption(formLinkType);
    await this.addFormJsonConfigurationInput.click();
    await this.addFormJsonConfigurationInput.fill(jsonConfig);
  }

  async clickAppendGatewayButton(): Promise<void> {
    await this.appendGatewayButton.click({timeout: 90000});
  }

  async clickParallelGatewayOption(): Promise<void> {
    await this.parallelGatewayOption.click({timeout: 90000});
  }

  async clickFirstPlacedGateway(): Promise<void> {
    try {
      await this.firstPlacedGateway.click({timeout: 60000});
      await this.appendElementButton.isVisible({timeout: 60000});
    } catch (error) {
      await this.page.reload();
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await this.firstPlacedGateway.click({timeout: 60000});
    }
  }

  async clickSecondPlacedGateway(): Promise<void> {
    try {
      await this.secondPlacedGateway.click({timeout: 60000});
      await this.appendElementButton.isVisible({timeout: 60000});
    } catch (error) {
      await this.page.reload();
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await this.secondPlacedGateway.click({timeout: 60000});
    }
  }

  async addParallelUserTasks(
    numberOfTasks: number,
    taskName: string,
  ): Promise<void> {
    try {
      await this.clickAppendElementButton();
    } catch (error) {
      await this.page.reload();
      await sleep(10000);
      await this.clickFirstPlacedElement();
      await this.appendElementButton.hover();
    }
    await this.clickAppendGatewayButton();
    await this.clickChangeTypeButton();
    await this.clickParallelGatewayOption();
    await this.clickAppendElementButton();
    await this.clickAppendTaskButton();
    await this.clickChangeTypeButton();
    await this.clickUserTaskOption();
    await this.assertImplementationOption('zeebeUserTask');
    await this.clickGeneralPropertiesPanel();
    await this.clickNameInput();
    await this.fillNamedInput(taskName + '1');
    await this.clickIdInput();
    await this.fillIdInput(taskName + '1');
    await this.clickAppendElementButton();
    await this.clickAppendGatewayButton();
    await this.clickChangeTypeButton();
    await this.clickParallelGatewayOption();
    for (let i = 2; i <= numberOfTasks; i++) {
      await this.clickFirstPlacedGateway();
      await this.clickAppendElementButton();
      await this.clickAppendTaskButton();
      await this.clickChangeTypeButton();
      await this.clickUserTaskOption();
      await this.assertImplementationOption('zeebeUserTask');
      await this.clickNameInput();
      await this.fillNamedInput(taskName + i);
      await this.clickIdInput();
      await this.fillIdInput(taskName + i);
      await this.clickConnectToOtherElementButton();
      await this.clickSecondPlacedGateway();
      await this.clickCanvas();
    }
    await this.clickSecondPlacedGateway();
  }

  async clickServiceTaskOption(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.serviceTaskOption.click({timeout: 30000, force: true});
        } else {
          await this.changeTypeButton.click({force: true, timeout: 30000});
          await this.serviceTaskOption.click({timeout: 30000, force: true});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(`Failed to click the button after ${maxRetries} attempts.`);
  }

  async clickTaskDefinitionPropertiesPanel(): Promise<void> {
    await this.taskDefinitionPanel.click();
  }

  async clickJobTypeInput(): Promise<void> {
    await this.jobTypeInput.click();
  }

  async fillJobTypeInput(name: string): Promise<void> {
    await this.jobTypeInput.fill(name);
  }

  async switchToPlay(): Promise<void> {
    await this.playTab.click();
  }

  async completePlayConfiguration(): Promise<void> {
    const timeout: number = 30000;
    let attempts = 0;
    const maxRetries = 2;

    while (attempts < maxRetries) {
      try {
        if (attempts > 0) await this.switchToPlay(); // Call switchToPlay on subsequent attempts
        await this.continueToPlayButton.click({timeout});
        await expect(
          this.page.getByText('Play environment is ready'),
        ).toBeVisible({timeout: 90000});

        return; // Exit if successful
      } catch (error) {
        if (attempts >= maxRetries - 1) throw error;
        await this.page.reload();
        attempts++;
      }
    }
  }
}

export {ModelerCreatePage};
