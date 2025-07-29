/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';
import {waitForItemInList} from 'utils/waitForItemInList';

export class IdentityUsersPage {
  private page: Page;
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
  readonly emptyState: Locator;
  readonly userCell: (name: string) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.usersList = page.getByRole('table');
    this.createUserButton = page.getByRole('button', {
      name: 'Create user',
      exact: true,
    });
    this.editUserButton = (rowName) =>
      this.usersList
        .getByRole('row', {name: rowName})
        .getByLabel('Edit user', {exact: true});
    this.deleteUserButton = (rowName) =>
      this.usersList
        .getByRole('row', {name: rowName})
        .getByLabel('Delete', {exact: true});

    this.createUserModal = page.getByRole('dialog', {
      name: 'Create user',
      exact: true,
    });
    this.closeCreateUserModal = this.createUserModal.getByRole('button', {
      name: 'Close',
      exact: true,
    });
    this.createUsernameField = this.createUserModal.getByRole('textbox', {
      name: 'Username',
      exact: true,
    });
    this.createNameField = this.createUserModal.getByRole('textbox', {
      name: 'Name',
      exact: true,
    });
    this.createEmailField = this.createUserModal.getByRole('textbox', {
      name: 'Email',
      exact: true,
    });
    this.createPasswordField = this.createUserModal.getByRole('textbox', {
      name: 'Password',
      exact: true,
    });
    this.createRepeatPasswordField = this.createUserModal.getByRole('textbox', {
      name: 'Repeat password',
      exact: true,
    });
    this.createUserModalCancelButton = this.createUserModal.getByRole(
      'button',
      {
        name: 'Cancel',
        exact: true,
      },
    );
    this.createUserModalCreateButton = this.createUserModal.getByRole(
      'button',
      {
        name: 'Create user',
        exact: true,
      },
    );
    this.editUserModal = page.getByRole('dialog', {
      name: 'Edit user',
    });
    this.closeEditUserModal = this.editUserModal.getByRole('button', {
      name: 'Close',
      exact: true,
    });
    this.editNameField = this.editUserModal.getByRole('textbox', {
      name: 'Name',
      exact: true,
    });
    this.editEmailField = this.editUserModal.getByRole('textbox', {
      name: 'Email',
      exact: true,
    });
    this.editNewPasswordField = this.editUserModal.getByRole('textbox', {
      name: 'New password',
      exact: true,
    });
    this.editRepeatPasswordField = this.editUserModal.getByRole('textbox', {
      name: 'Repeat password',
      exact: true,
    });
    this.editUserModalCancelButton = this.editUserModal.getByRole('button', {
      name: 'Cancel',
      exact: true,
    });
    this.editUserModalUpdateButton = this.editUserModal.getByRole('button', {
      name: 'Update user',
      exact: true,
    });

    this.deleteUserModal = page.getByRole('dialog', {
      name: 'Delete user',
      exact: true,
    });
    this.closeDeleteUserModal = this.deleteUserModal.getByRole('button', {
      name: 'Close',
      exact: true,
    });
    this.deleteUserModalCancelButton = this.deleteUserModal.getByRole(
      'button',
      {
        name: 'Cancel',
        exact: true,
      },
    );
    this.deleteUserModalDeleteButton = this.deleteUserModal.getByRole(
      'button',
      {name: 'danger Delete user'},
    );

    this.emptyState = page.getByText('No users created yet', {exact: true});
    this.userCell = (name) =>
      this.usersList.getByRole('cell', {name, exact: true});
  }

  async navigateToUsers() {
    await this.page.goto(relativizePath(Paths.users()));
  }

  async createUser(user: {
    username: string;
    name: string;
    email: string;
    password: string;
  }) {
    await this.createUserButton.click();
    await expect(this.createUserModal).toBeVisible();
    await this.createUsernameField.fill(user.username);
    await this.createNameField.fill(user.name);
    await this.createEmailField.fill(user.email);
    await this.createPasswordField.fill(user.password);
    await this.createRepeatPasswordField.fill(user.password);
    await this.createUserModalCreateButton.click();
    await expect(this.createUserModal).toBeHidden();

    const item = this.usersList.getByRole('cell', {
      name: user.email,
    });

    await waitForItemInList(this.page, item, {
      emptyStateLocator: this.emptyState,
    });
  }

  async editUser(
    currentUser: {email: string},
    updatedUser: {name: string; email: string},
  ) {
    await this.editUserButton(currentUser.email).click();
    await expect(this.editUserModal).toBeVisible();
    await this.editNameField.fill(updatedUser.name);
    await this.editEmailField.fill(updatedUser.email);
    await this.editUserModalUpdateButton.click();
    await expect(this.editUserModal).toBeHidden();
  }

  async deleteUser(user: {username: string; email: string}) {
    await this.deleteUserButton(user.username).click();
    await expect(this.deleteUserModal).toBeVisible();
    await this.deleteUserModalDeleteButton.click();
    await expect(this.deleteUserModal).toBeHidden();

    const item = this.usersList.getByRole('cell', {
      name: user.email,
    });

    await waitForItemInList(this.page, item, {
      shouldBeVisible: false,
      emptyStateLocator: this.emptyState,
    });
  }
}
