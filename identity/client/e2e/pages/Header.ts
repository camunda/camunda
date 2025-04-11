import { Page, Locator } from "@playwright/test";

export class Header {
  readonly openSettingsButton: Locator;
  readonly logoutButton: Locator;

  constructor(page: Page) {
    this.openSettingsButton = page.getByRole("button", {
      name: "Open Settings",
    });
    this.logoutButton = page.getByRole("button", { name: "Log out" });
  }

  async logout() {
    await this.openSettingsButton.click();
    await this.logoutButton.click();
  }
}
