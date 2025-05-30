/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { Page, Locator } from "@playwright/test";
import { Paths } from "../utils/paths";
import { relativizePath } from "../utils/relativizePaths";

export class TenantsPage {
  private page: Page;
  readonly tenantsList: Locator;
  readonly createTenantButton: Locator;
  readonly deleteTenantButton: (rowName?: string) => Locator;
  readonly createTenantModal: Locator;
  readonly closeCreateTenantModal: Locator;
  readonly createTenantIdField: Locator;
  readonly createTenantNameField: Locator;
  readonly createTenantDescField: Locator;
  readonly createTenantModalCancelButton: Locator;
  readonly createTenantModalCreateButton: Locator;
  readonly deleteTenantModal: Locator;
  readonly closeDeleteTenantModal: Locator;
  readonly deleteTenantModalCancelButton: Locator;
  readonly deleteTenantModalDeleteButton: Locator;
  readonly assignUserButton: Locator;
  readonly assignUserModal: Locator;
  readonly assignUserNameField: Locator;
  readonly assignUserNameOption: (name: string) => Locator;
  readonly confirmAssignmentButton: Locator;
  readonly openTenantDetails: (rowName: string) => Locator;
  readonly removeUserButton: (rowName?: string) => Locator;
  readonly removeUserModal: Locator;
  readonly confirmRemoveUserButton: Locator;
  readonly usersEmptyState: Locator;

  constructor(page: Page) {
    this.page = page;
    // List page
    this.tenantsList = page.getByRole("table");
    this.createTenantButton = page.getByRole("button", {
      name: /create tenant/i,
    });
    this.deleteTenantButton = (rowName) =>
      this.tenantsList.getByRole("row", { name: rowName }).getByLabel("Delete");

    // Create tenant modal
    this.createTenantModal = page.getByRole("dialog", {
      name: "Create new tenant",
    });
    this.closeCreateTenantModal = this.createTenantModal.getByRole("button", {
      name: "Close",
    });
    this.createTenantIdField = this.createTenantModal.getByRole("textbox", {
      name: "Tenant ID",
    });
    this.createTenantNameField = this.createTenantModal.getByRole("textbox", {
      name: "Tenant name",
    });
    this.createTenantDescField = this.createTenantModal.getByRole("textbox", {
      name: "Description",
    });
    this.createTenantModalCancelButton = this.createTenantModal.getByRole(
      "button",
      {
        name: "Cancel",
      },
    );
    this.createTenantModalCreateButton = this.createTenantModal.getByRole(
      "button",
      {
        name: /create tenant/i,
      },
    );

    // Delete tenant modal
    this.deleteTenantModal = page.getByRole("dialog", {
      name: /delete tenant/i,
    });
    this.closeDeleteTenantModal = this.deleteTenantModal.getByRole("button", {
      name: "Close",
    });
    this.deleteTenantModalCancelButton = this.deleteTenantModal.getByRole(
      "button",
      {
        name: "Cancel",
      },
    );
    this.deleteTenantModalDeleteButton = this.deleteTenantModal.getByRole(
      "button",
      {
        name: /delete tenant/i,
      },
    );

    // tenant details
    this.openTenantDetails = (rowName) =>
      page.getByRole("cell", { name: rowName });
    this.assignUserButton = page.getByRole("button", {
      name: /assign user/i,
    });
    this.assignUserModal = page.getByRole("dialog", {
      name: "assign user",
    });
    this.assignUserNameField = this.assignUserModal.getByRole("searchbox", {
      name: /search by full name/i,
    });
    this.assignUserNameOption = (name) =>
      this.assignUserModal.locator(".cds--list-box__menu-item", {
        hasText: name,
      });
    this.confirmAssignmentButton = this.assignUserModal.getByRole("button", {
      name: "Assign user",
    });
    this.usersEmptyState = page.getByText("Assign users to this Tenant");
    this.removeUserButton = (rowName) =>
      page.getByRole("row", { name: rowName }).getByLabel("Remove");

    this.removeUserModal = page.getByRole("dialog", { name: "Remove user" });
    this.confirmRemoveUserButton = this.removeUserModal.getByRole("button", {
      name: "Remove user",
    });
  }

  async navigateToTenants() {
    await this.page.goto(relativizePath(Paths.tenants()));
  }
}
