import {Page, Locator} from '@playwright/test';

class TaskProcessesPage {
  private page: Page;
  readonly popupContinueButton: Locator;
  readonly popup: Locator;
  readonly processes: Locator;

  constructor(page: Page) {
    this.page = page;
    this.popupContinueButton = page.getByRole('button', {name: 'Continue'});
    this.popup = page.getByLabel('Start your process on demand');
    this.processes = page.locator('[data-testid="process-tile"]');
  }

  async clickpopupContinueButton(): Promise<void> {
    if ((await this.popup.count()) == 1) {
      await this.popupContinueButton.click({timeout: 30000});
    }
  }

  async startProcess(name: string): Promise<void> {
    await this.processes
      .filter({hasText: name})
      .nth(0)
      .getByRole('button', {name: 'Start process'})
      .click({timeout: 60000});
  }
}

export {TaskProcessesPage};
