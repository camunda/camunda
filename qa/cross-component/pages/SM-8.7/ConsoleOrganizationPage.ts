import {Page, Locator} from '@playwright/test';

class ConsoleOrganizationPage {
  private page: Page;
  readonly usersTab: Locator;
  readonly optionsButton: Locator;
  readonly editUserMenuItem: Locator;
  readonly operationsEngineerCheckbox: Locator;
  readonly developerCheckbox: Locator;
  readonly analystCheckbox: Locator;
  readonly confirmButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usersTab = page.getByRole('tab', {name: 'Users'});
    this.optionsButton = page.getByRole('button', {name: 'Options'});
    this.editUserMenuItem = page.getByRole('menuitem', {name: 'Edit User'});
    this.operationsEngineerCheckbox = page
      .getByRole('group')
      .locator('label')
      .filter({hasText: 'Operations Engineer'});
    this.developerCheckbox = page
      .getByRole('group')
      .locator('label')
      .filter({hasText: 'Developer'});
    this.analystCheckbox = page
      .getByRole('group')
      .locator('label')
      .filter({hasText: 'Analyst'});
    this.confirmButton = page.getByRole('button', {name: 'Confirm'});
  }

  async clickUsersTab(): Promise<void> {
    await this.usersTab.click();
  }

  async clickOptionsButton(): Promise<void> {
    await this.optionsButton.click();
  }

  async clickEditUserMenuItem(): Promise<void> {
    await this.editUserMenuItem.click();
  }

  async clickOperationsEngineerCheckbox(): Promise<void> {
    await this.operationsEngineerCheckbox.click();
  }

  async checkDeveloperCheckbox(): Promise<void> {
    if (!(await this.developerCheckbox.isChecked())) {
      await this.developerCheckbox.click();
    }
  }

  async uncheckDeveloperCheckbox(): Promise<void> {
    if (await this.developerCheckbox.isChecked()) {
      await this.developerCheckbox.click();
    }
  }

  async checkAnalystCheckbox(): Promise<void> {
    if (!(await this.analystCheckbox.isChecked())) {
      await this.analystCheckbox.click();
    }
  }

  async uncheckAnalystCheckbox(): Promise<void> {
    if (await this.analystCheckbox.isChecked()) {
      await this.analystCheckbox.click();
    }
  }

  async clickConfirmButton(): Promise<void> {
    await this.confirmButton.click();
  }
}

export {ConsoleOrganizationPage};
