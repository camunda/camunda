/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { Page, Locator } from "@playwright/test";

export class RolesPage {
  readonly rolesList: Locator;
  readonly createRoleButton: Locator;
  readonly editRoleButton: (rowName?: string) => Locator;
  readonly deleteRoleButton: (rowName?: string) => Locator;
  readonly createRoleModal: Locator;
  readonly closeCreateRoleModal: Locator;
  readonly createNameField: Locator;
  readonly createIdField: Locator;
  readonly createRoleModalCancelButton: Locator;
  readonly createRoleModalCreateButton: Locator;
  readonly editRoleModal: Locator;
  readonly closeEditRoleModal: Locator;
  readonly editNameField: Locator;
  readonly editEmailField: Locator;
  readonly editNewPasswordField: Locator;
  readonly editRepeatPasswordField: Locator;
  readonly editRoleModalCancelButton: Locator;
  readonly editRoleModalUpdateButton: Locator;
  readonly deleteRoleModal: Locator;
  readonly closeDeleteRoleModal: Locator;
  readonly deleteRoleModalCancelButton: Locator;
  readonly deleteRoleModalDeleteButton: Locator;

  constructor(page: Page) {
    // List page
    this.rolesList = page.getByRole("table");
    this.createRoleButton = page.getByRole("button", {
      name: /create role/i,
    });
    this.editRoleButton = (rowName) =>
      this.rolesList
        .getByRole("row", { name: rowName })
        .getByLabel(/edit role/i);
    this.deleteRoleButton = (rowName) =>
      this.rolesList.getByRole("row", { name: rowName }).getByLabel("Delete");

    // Create role modal
    this.createRoleModal = page.getByRole("dialog", {
      name: "Create role",
    });
    this.closeCreateRoleModal = this.createRoleModal.getByRole("button", {
      name: "Close",
    });
    this.createIdField = this.createRoleModal.getByRole("textbox", {
      name: "Role ID",
      exact: true,
    });
    this.createNameField = this.createRoleModal.getByRole("textbox", {
      name: "Role name",
      exact: true,
    });
    this.createRoleModalCancelButton = this.createRoleModal.getByRole(
      "button",
      {
        name: "Cancel",
      },
    );
    this.createRoleModalCreateButton = this.createRoleModal.getByRole(
      "button",
      {
        name: /create role/i,
      },
    );

    // Edit role modal
    this.editRoleModal = page.getByRole("dialog", { name: /edit role/i });
    this.closeEditRoleModal = this.editRoleModal.getByRole("button", {
      name: "Close",
    });
    this.editNameField = this.editRoleModal.getByRole("textbox", {
      name: /name/i,
      exact: true,
    });
    this.editRoleModalCancelButton = this.editRoleModal.getByRole("button", {
      name: "Cancel",
    });
    this.editRoleModalUpdateButton = this.editRoleModal.getByRole("button", {
      name: /update role/i,
    });

    // Delete role modal
    this.deleteRoleModal = page.getByRole("dialog", { name: /delete role/i });
    this.closeDeleteRoleModal = this.deleteRoleModal.getByRole("button", {
      name: "Close",
    });
    this.deleteRoleModalCancelButton = this.deleteRoleModal.getByRole(
      "button",
      {
        name: "Cancel",
      },
    );
    this.deleteRoleModalDeleteButton = this.deleteRoleModal.getByRole(
      "button",
      {
        name: /delete role/i,
      },
    );
  }
}
