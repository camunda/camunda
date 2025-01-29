import {Page, Locator} from '@playwright/test';

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
  readonly emptyHomePageBanner: Locator;

  constructor(page: Page) {
    this.page = page;
    this.modelerPageBanner = page
      .locator('a')
      .filter({hasText: 'Camunda logoModeler'});
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
      .getByText('Optimize User Task Flow')
      .first();
    this.htoProjectFolder = page.getByText('HTO Project').first();
    this.htoUserFlowDiagram = page
      .getByText('User_Task_Process', {exact: true})
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
    this.emptyHomePageBanner = page.getByText('start by creating a project');
  }

  async clickCreateNewProjectButton(): Promise<void> {
    await this.createNewProjectButton.click({timeout: 30000});
  }

  async enterNewProjectName(name: string): Promise<void> {
    await this.projectNameInput.click();
    await this.projectNameInput.fill(name);
    await this.projectNameInput.press('Enter');
  }

  async clickChooseBpmnTemplateButton(): Promise<void> {
    await this.chooseBpmnTemplateButton.click();
  }

  async clickDiagramTypeDropdown(): Promise<void> {
    await this.diagramTypeDropdown.click();
  }

  async clickBpmnTemplateOption(): Promise<void> {
    await this.bpmnTemplateOption.click();
  }

  async clickOptimizeProjectFolder(): Promise<void> {
    await this.optimizeProjectFolder.click({timeout: 60000});
  }

  async clickOptimizeUserTaskFlowDiagram(): Promise<void> {
    await this.optimizeUserTaskFlowDiagram.click({timeout: 60000});
  }

  async clickHTOProjectFolder(): Promise<void> {
    await this.htoProjectFolder.click({timeout: 60000});
  }

  async openHTOProjectFolder(name: string): Promise<void> {
    const sleep = (ms: number | undefined) =>
      new Promise((r) => setTimeout(r, ms));
    await sleep(20000);

    if ((await this.htoProjectFolder.count()) == 0) {
      await this.clickCreateNewProjectButton();
      await this.enterNewProjectName(name);
    } else this.clickHTOProjectFolder();
  }

  async clickHTOUserFlowDiagram(): Promise<void> {
    await this.htoUserFlowDiagram.click({timeout: 60000});
  }

  async clickFormOption(): Promise<void> {
    await this.formTemplateOption.click();
  }

  async clickProjectBreadcrumb(): Promise<void> {
    await this.projectBreadcrumb.click();
  }

  async clickOpenOrganizationsButton(): Promise<void> {
    await this.openOrganizationsButton.click({timeout: 30000});
  }

  async clickManageButton(): Promise<void> {
    await this.manageButton.click({timeout: 30000});
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
}

export {ModelerHomePage};
