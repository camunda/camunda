import { Page, Locator } from "@playwright/test";

export class Users {
  readonly createUserButton: Locator;
  readonly editUserButton: Locator;
  readonly deleteFirstUserButton: Locator;

  constructor(page: Page) {
    this.createUserButton = page.getByRole("button", {
      name: "Create user",
    });
    this.editUserButton = page.getByRole("button", {
      name: "Edit user",
    });
    this.deleteFirstUserButton = page
      .getByRole("button", { name: "Delete" })
      .first();
  }
}
