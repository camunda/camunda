import {Page, Locator, expect} from '@playwright/test';

class OptimizeDashboardPage {
  private page: Page;
  readonly filterTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.filterTable = page.getByPlaceholder('Filter table');
  }

  async clickFilterTable(): Promise<void> {
    await this.filterTable.click({timeout: 120000});
  }

  async fillFilterTable(processName: string): Promise<void> {
    await this.filterTable.fill(processName, {timeout: 60000});
    await this.filterTable.press('Enter');
  }

  async processLinkAssertion(
    processName: string,
    maxRetries: number,
    retryDelay: number = 60000,
  ): Promise<void> {
    for (let attempt = 0; attempt < maxRetries; attempt++) {
      try {
        await this.page.reload();
        await this.clickFilterTable();
        await this.fillFilterTable(processName);
        await expect(
          this.page.getByRole('link', {name: processName}),
        ).toBeVisible({timeout: 70000});
        return;
      } catch (error) {
        if (attempt < maxRetries - 1) {
          console.warn(
            `Attempt ${
              attempt + 1
            } failed for asserting process link in Optimize. Retrying...`,
          );
          await new Promise((resolve) => setTimeout(resolve, retryDelay));
        } else {
          throw new Error(
            `Process link in Optimize assertion failed after ${maxRetries} attempts`,
          );
        }
      }
    }
  }

  async processOwnerNameAssertion(processName: string): Promise<void> {
    const maxRetries = 15;
    const retryDelay = 60000;

    for (let attempt = 0; attempt < maxRetries; attempt++) {
      try {
        await this.page.reload();
        await expect(
          this.page.getByRole('row', {
            name: `${processName}\nProcess\n\tQA Camunda`,
          }),
        ).toBeVisible({timeout: 90000});
        return;
      } catch (error) {
        if (attempt < maxRetries - 1) {
          console.warn(
            `Attempt ${
              attempt + 1
            } failed for asserting owner name in Optimize.. Retrying...`,
          );
          await new Promise((resolve) => setTimeout(resolve, retryDelay));
        } else {
          throw new Error(
            `Owner name assertion failed after ${maxRetries} attempts`,
          );
        }
      }
    }
  }
}
export {OptimizeDashboardPage};
