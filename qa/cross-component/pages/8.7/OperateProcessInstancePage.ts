import {Page, Locator} from '@playwright/test';

class OperateProcessInstancePage {
  private page: Page;
  readonly diagram: Locator;
  readonly completedIcon: Locator;
  readonly diagramSpinner: Locator;
  readonly activeIcon: Locator;

  constructor(page: Page) {
    this.page = page;
    this.diagram = page.getByTestId('diagram');
    this.completedIcon = page
      .getByTestId('instance-header')
      .getByTestId('COMPLETED-icon');
    this.diagramSpinner = page.getByTestId('diagram-spinner');
    this.activeIcon = page
      .getByTestId('instance-header')
      .getByTestId('ACTIVE-icon');
  }

  async connectorResultVariableName(name: string): Promise<Locator> {
    return await this.page.getByTestId(name);
  }

  async connectorResultVariableValue(variableName: string): Promise<Locator> {
    return await this.page.getByTestId(variableName).locator('td').last();
  }
}

export {OperateProcessInstancePage};
