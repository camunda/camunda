import {Page, Locator, expect} from '@playwright/test';

const maxWaitTimeSeconds: number = 120000;

class PlayPage {
  private page: Page;
  readonly completeJobButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.completeJobButton = page
      .getByTestId('diagram')
      .getByLabel('Complete job');
  }

  async waitForCompleteJobButtonToBeAvailable(): Promise<void> {
    await expect(this.completeJobButton).toBeVisible({
      timeout: maxWaitTimeSeconds,
    });
  }

  async clickCompleteJobButton(): Promise<void> {
    await this.completeJobButton.click();
  }

  async clickStartInstanceButton(): Promise<void> {
    await this.page.getByTestId('diagram').getByLabel('Start instance').click();
  }

  async dismissStartModal(): Promise<void> {
    await this.page
      .getByRole('button', {
        name: 'Start a process instance',
      })
      .click();
  }

  async waitForInstanceDetailsToBeLoaded(): Promise<void> {
    const maxRetries = 2;
    let attempts = 0;

    while (attempts < maxRetries) {
      try {
        await expect(this.page.getByText(/process instance key/i)).toBeVisible({
          timeout: maxWaitTimeSeconds,
        });
        await expect(
          this.page.getByText(/This process instance has no variables/i),
        ).toBeVisible({
          timeout: maxWaitTimeSeconds,
        });
        return;
      } catch (error) {
        if (attempts >= maxRetries - 1) throw error;
        await this.page.reload();
        attempts++;
      }
    }
  }

  async waitForNextElementToBeActive(historyItem: string): Promise<void> {
    await expect(
      this.page.getByText(new RegExp(`^${historyItem}`, 'i')),
    ).toBeVisible({
      timeout: maxWaitTimeSeconds,
    });
  }

  async waitForProcessToBeCompleted(): Promise<void> {
    await expect(
      this.page.getByRole('heading', {name: 'Scenario recorded!'}),
    ).toBeVisible({timeout: maxWaitTimeSeconds});
  }
}

export {PlayPage};
