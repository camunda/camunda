import {Page, Locator} from '@playwright/test';

class OperateHomePage {
  private page: Page;
  readonly operateBanner: Locator;
  readonly processesTab: Locator;
  readonly informationDialog: Locator;
  readonly editVariableButton: Locator;
  readonly variableValueInput: Locator;
  readonly saveVariableButton: Locator;
  readonly editVariableSpinner: Locator;

  constructor(page: Page) {
    this.page = page;
    this.operateBanner = page.getByRole('link', {name: 'Camunda logo Operate'});
    this.processesTab = page.getByRole('link', {name: 'Processes'});
    this.informationDialog = page.getByRole('button', {
      name: 'Close this dialog',
    });
    this.editVariableButton = page.getByTestId('edit-variable-button');
    this.variableValueInput = page.getByTestId('edit-variable-value');
    this.saveVariableButton = page.getByLabel('Save variable');
    this.editVariableSpinner = page
      .getByTestId('variable-operation-spinner')
      .locator('circle')
      .nth(1);
  }

  async clickProcessesTab(): Promise<void> {
    await this.processesTab.click();
  }

  async clickEditVariableButton(variableName: string): Promise<void> {
    const editVariableButton = 'Edit variable ' + variableName;
    await this.page.getByLabel(editVariableButton).click();
  }

  async clickVariableValueInput(): Promise<void> {
    await this.variableValueInput.click();
  }

  async clearVariableValueInput(): Promise<void> {
    await this.variableValueInput.clear();
  }

  async fillVariableValueInput(value: string): Promise<void> {
    await this.variableValueInput.fill(value);
  }

  async clickSaveVariableButton(): Promise<void> {
    await this.saveVariableButton.click();
  }
}

export {OperateHomePage};
