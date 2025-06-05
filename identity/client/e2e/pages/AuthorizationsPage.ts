import { Page, Locator } from "@playwright/test";
import { Paths } from "../utils/paths";
import { relativizePath } from "../utils/relativizePaths";

export class AuthorizationsPage {
  readonly page: Page;
  readonly createAuthorizationButton: Locator;
  readonly authorizationsList: Locator;

  // Add Authorization Modal
  readonly createAuthorizationModal: Locator;
  readonly createAuthorizationOwnerComboBox: Locator;
  readonly createAuthorizationOwnerOption: (name: string) => Locator;
  readonly createAuthorizationResourceIdField: Locator;
  readonly createAuthorizationAccessPermission: (name: string) => Locator;
  readonly createAuthorizationSubmitButton: Locator;

  // Delete Authorization Modal
  readonly deleteAuthorizationButton: (name: string) => Locator;
  readonly deleteAuthorizationModal: Locator;
  readonly deleteAuthorizationModalDeleteButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.authorizationsList = page.getByRole("table");
    this.createAuthorizationButton = page.getByRole("button", {
      name: /create authorization/i,
    });
    this.createAuthorizationModal = page.getByRole("dialog", {
      name: "Create authorization",
    });
    this.createAuthorizationOwnerComboBox =
      this.createAuthorizationModal.getByRole("combobox", {
        name: "Owner",
        exact: true,
      });
    this.createAuthorizationOwnerOption = (name) =>
      this.createAuthorizationModal.getByRole("option", {
        name,
      });
    this.createAuthorizationResourceIdField =
      this.createAuthorizationModal.getByRole("textbox", {
        name: "Resource ID",
      });
    this.createAuthorizationAccessPermission = (name) =>
      this.createAuthorizationModal.getByRole("checkbox", {
        name,
      });
    this.createAuthorizationSubmitButton =
      this.createAuthorizationModal.getByRole("button", {
        name: "Create authorization",
      });
    this.deleteAuthorizationButton = (name) =>
      this.authorizationsList.getByRole("row", { name }).getByLabel("Delete");
    this.deleteAuthorizationModal = page.getByRole("dialog", {
      name: /delete authorization/i,
    });
    this.deleteAuthorizationModalDeleteButton =
      this.deleteAuthorizationModal.getByRole("button", {
        name: /delete authorization/i,
      });
  }

  async navigateToAuthorizations() {
    await this.page.goto(relativizePath(Paths.authorizations()));
  }
}
