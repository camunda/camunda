import {Page, expect} from '@playwright/test';

class OptimizeDashboardPage {
  private page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async processLinkAssertion(
    processName: string,
    maxRetries: number = 5,
    retryDelay: number = 60000,
  ): Promise<void> {
    for (let attempt = 0; attempt < maxRetries; attempt++) {
      try {
        await this.page.reload();
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

  async processOwnerNameAssertion(
    processName: string,
    maxRetries: number = 5,
    retryDelay: number = 60000,
  ): Promise<void> {
    for (let attempt = 0; attempt < maxRetries; attempt++) {
      try {
        await this.page.reload();
        await expect(
          this.page.getByRole('link', {name: 'Process ' + processName}),
        ).toContainText('QA Camunda', {timeout: 60000});
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
