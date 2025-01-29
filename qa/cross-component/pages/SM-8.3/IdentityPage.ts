import {Page, Locator} from '@playwright/test';

class IdentityPage {
  private page: Page;
  readonly identityBanner: Locator;
  readonly usersLink: Locator;
  readonly assignedRolesTab: Locator;
  readonly demoUser: Locator;
  readonly confirmDeleteButton: Locator;
  readonly assignRolesButton: Locator;
  readonly addButton: Locator;
  readonly operateCheckbox: Locator;
  readonly tasklistCheckbox: Locator;
  readonly optimizeCheckbox: Locator;
  readonly modelerCheckbox: Locator;
  readonly consoleCheckbox: Locator;

  constructor(page: Page) {
    this.page = page;
    this.identityBanner = page.getByRole('link', {name: 'Camunda Identity'});
    this.usersLink = page.getByRole('link', {name: 'Users'});
    this.assignedRolesTab = page.getByRole('tab', {name: 'Assigned roles'});
    this.demoUser = page.getByRole('cell', {name: 'demo'}).first();
    this.confirmDeleteButton = page.getByRole('button', {
      name: 'danger Delete',
    });
    this.assignRolesButton = page.getByRole('button', {name: 'Assign roles'});
    this.addButton = page.getByRole('button', {name: 'Add'});
    this.operateCheckbox = page
      .locator('label')
      .filter({hasText: 'Operate (Grants full access to Operate)'});
    this.tasklistCheckbox = page
      .locator('label')
      .filter({hasText: 'Tasklist (Grants full access to Tasklist)'});
    this.optimizeCheckbox = page
      .locator('label')
      .filter({hasText: 'Optimize (Grants full access to Optimize)'});
    this.modelerCheckbox = page
      .locator('label')
      .filter({hasText: 'Web Modeler (Grants full access to Web Modeler)'});
    this.consoleCheckbox = page
      .locator('label')
      .filter({hasText: 'Console (Grants full access to Console)'});
  }

  async clickUsersLink(): Promise<void> {
    try {
      await this.usersLink.click({timeout: 180000, force: true});
    } catch {
      await this.page.reload();
      await this.usersLink.click({timeout: 180000, force: true});
    }
  }

  async clickAssignedRolesTab(): Promise<void> {
    await this.assignedRolesTab.click();
  }

  async clickDemoUser(): Promise<void> {
    await this.demoUser.click({timeout: 180000});
  }

  async clickDeleteAccessButton(accessToDelete: string): Promise<void> {
    try {
      await this.page
        .locator('td:right-of(:text("' + accessToDelete + '"))')
        .first()
        .click({timeout: 60000});
    } catch {
      console.log('Access already deleted');
    }
  }

  async clickConfirmDeleteButton(): Promise<void> {
    try {
      await this.confirmDeleteButton.click();
    } catch {
      console.log('Access already deleted');
    }
  }

  async clickAssignRolesButton(): Promise<void> {
    await this.assignRolesButton.click();
  }

  async clickAddButton(): Promise<void> {
    await this.addButton.click();
  }

  async clickOperateCheckbox(): Promise<void> {
    await this.operateCheckbox.click();
  }

  async clickTasklistCheckbox(): Promise<void> {
    await this.tasklistCheckbox.click();
  }

  async clickOptimizeCheckbox(): Promise<void> {
    await this.optimizeCheckbox.click();
  }

  async clickModelerCheckbox(): Promise<void> {
    await this.modelerCheckbox.click();
  }

  async clickConsoleCheckbox(): Promise<void> {
    await this.consoleCheckbox.click();
  }
}

export {IdentityPage};
