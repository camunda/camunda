import {Page, Locator, expect} from '@playwright/test';

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

  async assertProcessCompleteStatusWithRetry(
    timeout: number = 60000,
    maxRetries: number = 10,
  ): Promise<void> {
    for (let attempt = 0; attempt < maxRetries; attempt++) {
      try {
        await this.page.reload();
        await expect(this.completedIcon).toBeVisible({
          timeout: timeout,
        });
        return;
      } catch (error) {
        if (attempt < maxRetries - 1) {
          console.warn(
            `Process complete status attempt ${
              attempt + 1
            } failed. Retrying...`,
          );
        } else {
          throw new Error(`Assertion failed after ${maxRetries} attempts`);
        }
      }
    }
  }
}

export {OperateProcessInstancePage};
