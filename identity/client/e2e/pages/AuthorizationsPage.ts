import { Page, Locator, expect } from "@playwright/test";
import { Paths } from "../utils/paths";
import { relativizePath } from "../utils/relativizePaths";
import { waitForItemInList } from "../utils/waitForItemInList";

export class AuthorizationsPage {
  readonly page: Page;
  readonly selectResourceTypeTab: (resourceType: string) => Promise<void>;
  readonly createAuthorizationButton: Locator;
  readonly authorizationsList: Locator;

  // Add Authorization Modal
  readonly createAuthorizationModal: Locator;
  readonly createAuthorizationOwnerTypeDropdown: Locator;
  readonly createAuthorizationOwnerTypeCombobox: Locator;
  readonly createAuthorizationOwnerTypeOption: (name: string) => Locator;
  readonly createAuthorizationOwnerComboBox: Locator;
  readonly createAuthorizationOwnerOption: (name: string) => Locator;
  readonly createAuthorizationResourceTypeDropdown: Locator;
  readonly createAuthorizationResourceTypeOption: (name: string) => Locator;
  readonly createAuthorizationResourceIdField: Locator;
  readonly createAuthorizationAccessPermission: (name: string) => Locator;
  readonly createAuthorizationSubmitButton: Locator;

  // Delete Authorization Modal
  readonly deleteAuthorizationButton: (name: string) => Locator;
  readonly deleteAuthorizationModal: Locator;
  readonly deleteAuthorizationModalDeleteButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.selectResourceTypeTab = (resourceType) =>
      page.getByRole("tab", { name: resourceType }).click();
    this.authorizationsList = page.getByRole("table");
    this.createAuthorizationButton = page.getByRole("button", {
      name: /create authorization/i,
    });
    this.createAuthorizationModal = page.getByRole("dialog", {
      name: "Create authorization",
    });
    this.createAuthorizationOwnerTypeCombobox =
      this.createAuthorizationModal.getByRole("combobox", {
        name: "Owner type",
      });
    this.createAuthorizationOwnerTypeOption = (name) =>
      this.createAuthorizationModal.getByRole("option", {
        name,
        exact: true,
      });
    this.createAuthorizationOwnerComboBox =
      this.createAuthorizationModal.getByRole("combobox", {
        name: "Owner",
        exact: true,
      });
    this.createAuthorizationOwnerOption = (name) =>
      this.createAuthorizationModal.getByRole("option", {
        name,
        exact: true,
      });
    this.createAuthorizationResourceTypeDropdown =
      this.createAuthorizationModal.getByRole("combobox", {
        name: "Resource type",
      });
    this.createAuthorizationResourceTypeOption = (name) =>
      this.createAuthorizationModal.getByRole("option", {
        name,
        exact: true,
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

  async createAuthorization(authorization: {
    ownerType: string;
    ownerId: string;
    resourceType: string;
    resourceId: string;
    accessPermissions: string[];
  }) {
    await this.createAuthorizationButton.click();
    await expect(this.createAuthorizationModal).toBeVisible();
    await this.createAuthorizationOwnerTypeCombobox.click();
    await this.createAuthorizationOwnerTypeOption(
      authorization.ownerType,
    ).click();
    await this.createAuthorizationOwnerComboBox.click();
    await this.createAuthorizationOwnerOption(authorization.ownerId).click();
    await this.createAuthorizationResourceTypeDropdown.click();
    await this.createAuthorizationResourceTypeOption(
      authorization.resourceType,
    ).click();
    await this.createAuthorizationResourceIdField.fill(
      authorization.resourceId,
    );
    for (const permission of authorization.accessPermissions) {
      await this.createAuthorizationAccessPermission(permission).click({
        force: true,
      });
    }
    await this.createAuthorizationSubmitButton.click();
    await expect(this.createAuthorizationModal).not.toBeVisible();

    await this.selectResourceTypeTab(authorization.resourceType);

    const item = this.authorizationsList.getByRole("cell", {
      name: authorization.ownerId.toLowerCase().replaceAll(" ", ""),
    });

    await waitForItemInList(this.page, item, {
      clickAuthorizationsPageTab: () =>
        this.selectResourceTypeTab(authorization.resourceType),
    });
  }
}
