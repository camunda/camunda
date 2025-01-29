import {Page, Locator, expect} from '@playwright/test';

class OptimizeDashboardPage {
  private page: Page;
  readonly filterTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.filterTable = page.getByPlaceholder('Filter table');
  }

  async clickFilterTable(): Promise<void> {
    await this.filterTable.click({timeout: 180000});
  }

  async fillFilterTable(processName: string): Promise<void> {
    await this.filterTable.fill(processName, {timeout: 60000});
    await this.filterTable.press('Enter');
  }

  async processOwnerNameAssertion(processName: string): Promise<void> {
    try {
      await this.page.reload();
      await expect(
        this.page.getByRole('row', {
          name: processName + ' Process QA Camunda',
        }),
      ).toBeVisible({timeout: 180000});
    } catch {
      await this.page.reload();
      await expect(
        this.page.getByRole('row', {
          name: processName + ' Process QA Camunda',
        }),
      ).toBeVisible({timeout: 180000});
    }
  }
}
export {OptimizeDashboardPage};
