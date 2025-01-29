import {Page, Locator} from '@playwright/test';
import {expect} from '@playwright/test';

class ModelerUserInvitePage {
  private page: Page;
  private user_name!: string;
  readonly buttonAddUser: Locator;
  readonly inputFieldUserEmailAddress: Locator;
  readonly buttonSendInvitation: Locator;
  readonly pendingInviteText: Locator;
  readonly settings: Locator;
  readonly logout: Locator;
  readonly buttonSkipCustomization: Locator;
  readonly buttonCloseHelpCentre: Locator;
  readonly collaboratorName: Locator;

  constructor(page: Page) {
    this.page = page;
    this.buttonAddUser = page.locator('[data-test="add-collaborator"]');
    this.inputFieldUserEmailAddress = page.locator(
      '[data-test="multi-select-input"]',
    );
    this.buttonSendInvitation = page.locator(
      '[data-test="invite-email-button"]',
    );
    this.pendingInviteText = page.getByText('pending');
    this.settings = page.getByLabel('Open Settings');
    this.logout = page.getByRole('button', {name: 'Log out'});
    this.buttonSkipCustomization = page.getByRole('button', {
      name: 'Skip customization',
    });
    this.buttonCloseHelpCentre = page.getByRole('button', {name: 'Close'});
    this.collaboratorName = page.getByText('QA Camunda');
  }

  async clickAddUser(): Promise<void> {
    await expect(this.buttonAddUser).toBeVisible({timeout: 60000});
    await this.buttonAddUser.click({timeout: 30000});
  }

  async clickUserEmailInput(): Promise<void> {
    await expect(this.inputFieldUserEmailAddress).toBeVisible({
      timeout: 60000,
    });
    await this.inputFieldUserEmailAddress.click();
  }

  async fillUserEmailInput(
    emailAddress: string = this.user_name,
  ): Promise<void> {
    await this.inputFieldUserEmailAddress.fill(emailAddress);
    await expect(this.inputFieldUserEmailAddress).toHaveValue(emailAddress);
    await this.inputFieldUserEmailAddress.press('Enter');
  }

  async clickSendInviteButton(): Promise<void> {
    await expect(this.buttonSendInvitation).toBeVisible({timeout: 60000});
    await this.buttonSendInvitation.click();
  }

  async checkPendingInviteText(): Promise<void> {
    await expect(this.pendingInviteText).toBeVisible({
      timeout: 60000,
    });
  }

  async inviteNewUser(): Promise<void> {
    await this.clickAddUser();
    await this.clickUserEmailInput();
    await this.fillUserEmailInput();
    const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
    await sleep(8000);
    await this.clickSendInviteButton();
  }

  async clickSettings(): Promise<void> {
    await expect(this.settings).toBeVisible({timeout: 60000});
    await this.settings.click({timeout: 90000});
  }

  async clickLogout(): Promise<void> {
    await expect(this.logout).toBeVisible({timeout: 60000});
    await this.logout.click({timeout: 50000});
  }
  async clickSkipCustomization(): Promise<void> {
    await expect(this.buttonSkipCustomization).toBeVisible({timeout: 60000});
    await this.buttonSkipCustomization.click();
  }
  async clickCloseHelpCenter(): Promise<void> {
    await expect(this.buttonCloseHelpCentre).toBeVisible({timeout: 60000});
    await this.buttonCloseHelpCentre.click();
  }
  async verifyCollaboratorName(): Promise<void> {
    await expect(this.collaboratorName).toBeVisible({timeout: 60000});
  }
}
export {ModelerUserInvitePage};
