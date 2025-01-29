import {Page, Locator, expect} from '@playwright/test';

class ModelerHomePage {
  private page: Page;
  readonly modelerPageBanner: Locator;
  readonly createNewProjectButton: Locator;
  readonly projectNameInput: Locator;
  readonly chooseBpmnTemplateButton: Locator;
  readonly diagramTypeDropdown: Locator;
  readonly bpmnTemplateOption: Locator;
  readonly optimizeProjectFolder: Locator;
  readonly optimizeUserTaskFlowDiagram: Locator;
  readonly htoProjectFolder: Locator;
  readonly htoUserFlowDiagram: Locator;
  readonly formTemplateOption: Locator;
  readonly projectBreadcrumb: Locator;
  readonly openOrganizationsButton: Locator;
  readonly manageButton: Locator;
  readonly webModelerProjectFolder: Locator;
  readonly webModelerUserFlowDiagram: Locator;
  readonly connectorsProjectFolder: Locator;
  readonly rows: Locator;

  constructor(page: Page) {
    this.page = page;
    this.modelerPageBanner = page.getByRole('banner', {
      name: 'Camunda Modeler',
    });
    this.createNewProjectButton = page.getByRole('button', {
      name: 'New project',
    });
    this.projectNameInput = page.locator('[data-test="editable-input"]');
    this.chooseBpmnTemplateButton = page.getByRole('button', {
      name: 'Choose BPMN template',
    });
    this.diagramTypeDropdown = page.locator('[data-test="diagram-dropdown"]');
    this.bpmnTemplateOption = page
      .locator('[data-test="create-bpmn-diagram"]')
      .getByText('BPMN Diagram');
    this.optimizeProjectFolder = page.getByText('Optimize Project').first();
    this.optimizeUserTaskFlowDiagram = page
      .getByText('User Task Diagram')
      .first();
    this.htoProjectFolder = page.getByText('HTO Project').first();
    this.htoUserFlowDiagram = page
      .getByRole('link')
      .filter({hasText: 'User_Task_Process'})
      .first();
    this.formTemplateOption = page
      .locator('[data-test="create-form"]')
      .getByText('Form');
    this.projectBreadcrumb = page.locator('[data-test="breadcrumb-project"]');
    this.openOrganizationsButton = page.getByLabel('Open Organizations');
    this.manageButton = page.getByRole('button', {name: 'Manage'});
    this.webModelerUserFlowDiagram = page
      .getByText('Web Modeler Test Diagram')
      .first();
    this.webModelerProjectFolder = page
      .getByText('Web Modeler Project')
      .first();
    this.connectorsProjectFolder = page.getByText('Connectors Project').first();
    this.rows = page.getByRole('row');
  }

  async clickCreateNewProjectButton(): Promise<void> {
    await this.createNewProjectButton.click({timeout: 60000});
  }

  async enterNewProjectName(name: string): Promise<void> {
    await this.projectNameInput.click({timeout: 120000});
    await this.projectNameInput.fill(name);
    await this.projectNameInput.press('Enter');
  }

  async getProjectNames(name: string) {
    return this.page
      .getByRole('row')
      .filter({hasText: name})
      .getByTitle(name)
      .evaluateAll((elements) =>
        elements
          .map((el) => el.textContent || '')
          .filter((text) => text !== ''),
      );
  }

  async clickProcessDiagram(name: string): Promise<void> {
    const process = this.rows.filter({hasText: name}).getByTitle(name).first();
    await expect(process).toBeVisible({timeout: 30000});
    await process.click();
  }

  async clickChooseBpmnTemplateButton(): Promise<void> {
    await this.chooseBpmnTemplateButton.click();
  }

  async clickDiagramTypeDropdown(): Promise<void> {
    await this.diagramTypeDropdown.click({timeout: 60000});
  }

  async clickBpmnTemplateOption(): Promise<void> {
    await this.bpmnTemplateOption.click();
  }

  async clickOptimizeProjectFolder(): Promise<void> {
    await this.optimizeProjectFolder.click({timeout: 90000});
  }

  async clickOptimizeUserTaskFlowDiagram(): Promise<void> {
    await this.optimizeUserTaskFlowDiagram.click({timeout: 60000});
  }

  async clickHTOProjectFolder(): Promise<void> {
    await this.htoProjectFolder.click({timeout: 60000});
  }

  async clickHTOUserFlowDiagram(): Promise<void> {
    await this.htoUserFlowDiagram.click({timeout: 60000});
  }

  async clickFormOption(): Promise<void> {
    await this.formTemplateOption.click();
  }

  async clickProjectBreadcrumb(): Promise<void> {
    await this.projectBreadcrumb.click({timeout: 60000});
  }

  async clickOpenOrganizationsButton(retries: number = 3): Promise<void> {
    for (let i = 0; i < retries; i++) {
      try {
        await this.openOrganizationsButton.click({timeout: 90000, force: true});
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

  async clickManageButton(retries: number = 3): Promise<void> {
    for (let i = 0; i < retries; i++) {
      try {
        await this.manageButton.click({timeout: 90000, force: true});
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
        await this.clickOpenOrganizationsButton();
      }
    }
    throw new Error(`Failed to click deploy button after ${retries} retries`);
  }

  async clickWebModelerProjectFolder(): Promise<void> {
    await this.webModelerProjectFolder.click({timeout: 60000});
  }

  async clickWebModelerUserFlowDiagram(): Promise<void> {
    await this.webModelerUserFlowDiagram.click({timeout: 60000});
  }

  async clickConnectorsProjectFolder(): Promise<void> {
    await this.connectorsProjectFolder.click({timeout: 60000});
  }

  async openConnectorsProjectFolder(name: string): Promise<void> {
    const sleep = (ms: number | undefined) =>
      new Promise((r) => setTimeout(r, ms));
    await sleep(20000);

    if ((await this.connectorsProjectFolder.count()) == 0) {
      await this.clickCreateNewProjectButton();
      await this.enterNewProjectName(name);
    } else this.clickConnectorsProjectFolder();
  }
}

export {ModelerHomePage};
