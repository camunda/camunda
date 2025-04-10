import { Page, Locator } from "@playwright/test";

export class Common {
  private page: Page;
  readonly openSettingsButton: Locator;
  readonly logoutButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.openSettingsButton = page.getByRole("button", {
      name: "Open Settings",
    });
    this.logoutButton = page.getByRole("button", { name: "Log out" });
  }

  clickOpenSettingsButton() {
    return this.openSettingsButton.click();
  }

  clickLogoutButton() {
    return this.logoutButton.click();
  }

  async logout() {
    await this.clickOpenSettingsButton();
    await this.clickLogoutButton();
  }
}
