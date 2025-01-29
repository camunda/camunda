import {Page, Locator, expect} from '@playwright/test';

class OperateProcessInstancePage {
  private page: Page;
  readonly diagram: Locator;
  readonly completedIcon: Locator;
  readonly diagramSpinner: Locator;
  readonly activeIcon: Locator;
  readonly taskActiveIcon: Locator;

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
    this.taskActiveIcon = page.getByTestId('state-overlay');
  }

  async connectorResultVariableName(name: string): Promise<Locator> {
    return await this.page.getByTestId(name);
  }

  async connectorResultVariableValue(variableName: string): Promise<Locator> {
    return await this.page.getByTestId(variableName).locator('td').last();
  }

  async completedIconAssertion(): Promise<void> {
    try {
      await expect(this.completedIcon).toBeVisible({
        timeout: 90000,
      });
    } catch {
      this.page.reload();
      await expect(this.completedIcon).toBeVisible({
        timeout: 90000,
      });
    }
  }

  async activeIconAssertion(): Promise<void> {
    try {
      await expect(this.activeIcon).toBeVisible({
        timeout: 90000,
      });
    } catch {
      this.page.reload();
      await expect(this.activeIcon).toBeVisible({
        timeout: 90000,
      });
    }
  }

  async variableAssertion(): Promise<void> {
    try {
      await expect(this.page.getByText('"testValue"')).toBeVisible({
        timeout: 90000,
      });
    } catch {
      this.page.reload();
      await expect(this.page.getByText('"testValue"')).toBeVisible({
        timeout: 90000,
      });
    }
  }

  async taskActiveIconAssertion(count: number): Promise<void> {
    let retryCount = 0;
    const maxRetries = 3;
    while (retryCount < maxRetries) {
      try {
        await expect(await this.taskActiveIcon.count()).toEqual(count);
        return; // Exit the function if the expectation is met
      } catch (error) {
        // If the active icon is found, reload the page and try again
        retryCount++;
        console.log(`Attempt ${retryCount} failed. Retrying...`);
        await this.page.reload();
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(10000);
      }
    }
    throw new Error(
      `Active icon count is not matching after ${maxRetries} attempts.`,
    );
  }
}

export {OperateProcessInstancePage};
