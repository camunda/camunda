import {Page, Locator} from '@playwright/test';

class TaskProcessesPage {
  private page: Page;
  readonly popupContinueButton: Locator;
  readonly popup: Locator;

  constructor(page: Page) {
    this.page = page;
    this.popupContinueButton = page.getByRole('button', {name: 'Continue'});
    this.popup = page.getByLabel('Start your process on demand');
  }

  async clickpopupContinueButton(): Promise<void> {
    try {
      await this.popupContinueButton.click({timeout: 60000});
    } catch (error) {
      console.log('Popup not present');
    }
  }
}

export {TaskProcessesPage};
