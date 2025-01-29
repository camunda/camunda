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
  readonly firstElement: Locator;
  readonly secondElement: Locator;
  readonly thirdElement: Locator;
  readonly intermediateBoundaryEvent: Locator;
  readonly intermediateWebhookConnectorOption: Locator;
  readonly correlationKeyProcessInput: Locator;
  readonly correlationKeyPayloadInput: Locator;
  readonly closeButton: Locator;
  readonly rbaEnabledDeployedDialog: Locator;
  readonly viewProcessLink: Locator;
  readonly implementationSection: Locator;
  readonly implementationOptions: Locator;

  constructor(page: Page) {
    this.page = page;
    this.generalPanel = page.getByTitle('General').first();
    this.processIdInput = page.getByLabel('ID', {exact: true});
    this.startEventElement = page.locator('.djs-hit').first();
    this.appendElementButton = page
      .locator('[class="djs-create-pad open djs-append-create-pad"]')
      .first();
    this.appendTaskButton = page.getByTitle('Append task');
    this.changeTypeButton = page.getByLabel('Change element');
    this.userTaskOption = page
      .getByRole('listitem', {name: 'User task'})
      .locator('span')
      .first();
    this.appendEndEventButton = page.getByTitle('Append end event');
    this.startInstanceMainButton = page.getByRole('button', {name: 'Run'});
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
    this.deployMainButton = page.getByTitle('Deploys the current diagram');
    this.deploySubButton = this.deploySubButton = page.locator(
      '[data-test="deploy-action"]',
    );
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
    this.firstElement = page.locator('[class="djs-element djs-shape"]').nth(0);
    this.secondElement = page.locator('[class="djs-element djs-shape"]').nth(1);
    this.thirdElement = page.locator('[class="djs-element djs-shape"]').nth(2);
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
    this.closeButton = page.getByRole('button', {name: 'Close'});
    this.rbaEnabledDeployedDialog = page.getByRole('dialog', {
      name: 'Process successfully deployed.',
    });
    this.implementationSection = page.locator(
      '[data-group-id="group-userTaskImplementation"]',
    );
    this.implementationOptions = page.locator(
      '#bio-properties-panel-userTaskImplementation',
    );
  }

  async clickGeneralPropertiesPanel(): Promise<void> {
    await this.generalPanel.click({timeout: 60000});
  }

  async clickProcessIdInput(): Promise<void> {
    await this.processIdInput.click({timeout: 30000});
  }

  async assertThreeElementsVisible(): Promise<void> {
    await expect(this.firstElement).toBeVisible({timeout: 30000});
    await expect(this.secondElement).toBeVisible({timeout: 30000});
    await expect(this.thirdElement).toBeVisible({timeout: 30000});
  }

  async fillProcessIdInput(id: string): Promise<void> {
    await this.processIdInput.fill(id, {timeout: 60000});
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

  async clickStartEventElement(): Promise<void> {
    await this.startEventElement.click({timeout: 90000});
  }

  async clickAppendTaskButton(): Promise<void> {
    await this.appendTaskButton.click({timeout: 120000});
  }

  async clickChangeTypeButton(): Promise<void> {
    await this.changeTypeButton.click({timeout: 90000});
  }

  async clickUserTaskOption(): Promise<void> {
    await this.userTaskOption.click({timeout: 90000});
  }

  async chooseImplementationOption(implementationType: string): Promise<void> {
    const isExpanded =
      (await this.implementationSection
        .locator('.bio-properties-panel-group-header.empty.open')
        .count()) > 0;

    if (!isExpanded) {
      await this.implementationSection.click();
    }
    await this.implementationOptions.selectOption(implementationType);
  }

  async clickAppendEndEventButton(): Promise<void> {
    try {
      await this.appendEndEventButton.click({timeout: 120000});
    } catch (error) {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await this.page.reload();
      await this.clickSecondPlacedElement();
      await this.appendElementButton.hover();
      await this.appendEndEventButton.click({timeout: 120000});
    }
  }

  async clickStartInstanceMainButton(retries: number = 3): Promise<void> {
    for (let i = 0; i < retries; i++) {
      try {
        await this.startInstanceMainButton.click({timeout: 90000});
        return; // If successful, exit the function
      } catch (error) {
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(10000);
      }
    }
    throw new Error(`Failed to click button after ${retries} retries`);
  }

  async clickStartInstanceSubButton(retries: number = 3): Promise<void> {
    for (let i = 0; i < retries; i++) {
      try {
        await this.startInstanceSubButton.click({timeout: 90000});
        return; // If successful, exit the function
      } catch (error) {
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(10000);
      }
    }
    throw new Error(`Failed to click button after ${retries} retries`);
  }

  async clickViewProcessInstanceLink(): Promise<void> {
    await this.viewProcessInstanceLink.click({timeout: 120000});
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
    await this.diagramNameInput.fill(name, {timeout: 60000});
    await this.diagramNameInput.press('Enter', {timeout: 60000});
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
    await this.newForm.click({timeout: 60000});
  }

  async clickDeployMainButton(retries: number = 3): Promise<void> {
    for (let i = 0; i < retries; i++) {
      try {
        await this.deployMainButton.click({timeout: 90000, force: true});
        return; // If successful, exit the function
      } catch (error) {
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(10000);
      }
    }
    throw new Error(`Failed to click button after ${retries} retries`);
  }

  async clickDeploySubButton(retries: number = 3): Promise<void> {
    for (let i = 0; i < retries; i++) {
      try {
        await this.deploySubButton.click({timeout: 90000, force: true});
        return; // If successful, exit the function
      } catch (error) {
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(10000);
      }
    }
    throw new Error(`Failed to click button after ${retries} retries`);
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
    await this.webhookTab.click({timeout: 120000});
  }

  async clickWebhookIsActiveButton(): Promise<void> {
    await this.webhookIsActiveButton.click({timeout: 60000});
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

  async clickAppendElementButton(): Promise<void> {
    try {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(6000);
      await this.appendElementButton.hover({timeout: 60000, force: true});
    } catch (error) {
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
      await sleep(10000);
      await this.appendElementButton.hover({timeout: 60000, force: true});
    }
  }

  async fillStartformPayload(value: string): Promise<void> {
    await this.payloadInput.fill(value, {timeout: 60000});
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
