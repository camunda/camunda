/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { Page, Locator } from "@playwright/test";

export class UsersPage {
  readonly usersList: Locator;
  readonly createUserButton: Locator;
  readonly editUserButton: (rowName?: string) => Locator;
  readonly deleteUserButton: (rowName?: string) => Locator;
  readonly createUserModal: Locator;
  readonly closeCreateUserModal: Locator;
  readonly createUsernameField: Locator;
  readonly createNameField: Locator;
  readonly createEmailField: Locator;
  readonly createPasswordField: Locator;
  readonly createRepeatPasswordField: Locator;
  readonly createUserModalCancelButton: Locator;
  readonly createUserModalCreateButton: Locator;
  readonly editUserModal: Locator;
  readonly closeEditUserModal: Locator;
  readonly editNameField: Locator;
  readonly editEmailField: Locator;
  readonly editNewPasswordField: Locator;
  readonly editRepeatPasswordField: Locator;
  readonly editUserModalCancelButton: Locator;
  readonly editUserModalUpdateButton: Locator;
  readonly deleteUserModal: Locator;
  readonly closeDeleteUserModal: Locator;
  readonly deleteUserModalCancelButton: Locator;
  readonly deleteUserModalDeleteButton: Locator;

  constructor(page: Page) {
    // List page
    this.usersList = page.getByRole("table");
    this.createUserButton = page.getByRole("button", {
      name: /create user/i,
    });
    this.editUserButton = (rowName) =>
      this.usersList
        .getByRole("row", { name: rowName })
        .getByLabel(/edit user/i);
    this.deleteUserButton = (rowName) =>
      this.usersList.getByRole("row", { name: rowName }).getByLabel("Delete");

    // Create user modal
    this.createUserModal = page.getByRole("dialog", {
      name: "Create user",
    });
    this.closeCreateUserModal = this.createUserModal.getByRole("button", {
      name: "Close",
    });
    this.createUsernameField = this.createUserModal.getByRole("textbox", {
      name: "Username",
    });
    this.createNameField = this.createUserModal.getByRole("textbox", {
      name: "Name",
      exact: true,
    });
    this.createEmailField = this.createUserModal.getByRole("textbox", {
      name: "Email",
    });
    this.createPasswordField = this.createUserModal.getByRole("textbox", {
      name: "Password",
      exact: true,
    });
    this.createRepeatPasswordField = this.createUserModal.getByRole("textbox", {
      name: /repeat password/i,
      exact: true,
    });
    this.createUserModalCancelButton = this.createUserModal.getByRole(
      "button",
      {
        name: "Cancel",
      },
    );
    this.createUserModalCreateButton = this.createUserModal.getByRole(
      "button",
      {
        name: /create user/i,
      },
    );

    // Edit user modal
    this.editUserModal = page.getByRole("dialog", { name: /edit user/i });
    this.closeEditUserModal = this.editUserModal.getByRole("button", {
      name: "Close",
    });
    this.editNameField = this.editUserModal.getByRole("textbox", {
      name: /name/i,
      exact: true,
    });
    this.editEmailField = this.editUserModal.getByRole("textbox", {
      name: /email/i,
    });
    this.editNewPasswordField = this.editUserModal.getByRole("textbox", {
      name: /new password/i,
      exact: true,
    });
    this.editRepeatPasswordField = this.editUserModal.getByRole("textbox", {
      name: /repeat password/i,
      exact: true,
    });
    this.editUserModalCancelButton = this.editUserModal.getByRole("button", {
      name: "Cancel",
    });
    this.editUserModalUpdateButton = this.editUserModal.getByRole("button", {
      name: /update user/i,
    });

    // Delete user modal
    this.deleteUserModal = page.getByRole("dialog", { name: /delete user/i });
    this.closeDeleteUserModal = this.deleteUserModal.getByRole("button", {
      name: "Close",
    });
    this.deleteUserModalCancelButton = this.deleteUserModal.getByRole(
      "button",
      {
        name: "Cancel",
      },
    );
    this.deleteUserModalDeleteButton = this.deleteUserModal.getByRole(
      "button",
      {
        name: /delete user/i,
      },
    );
  }
}
