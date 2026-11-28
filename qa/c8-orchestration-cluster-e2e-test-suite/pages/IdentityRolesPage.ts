/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {defaultAssertionOptions} from 'utils/constants';
import {waitForItemInList} from 'utils/waitForItemInList';
import {sleep} from 'utils/sleep';
export class IdentityRolesPage {
  readonly page: Page;
  readonly rolesList: Locator;
  readonly createRoleButton: Locator;
  readonly rowActionsButton: (rowName: string) => Locator;
  readonly editRoleMenuItem: Locator;
  readonly deleteRoleMenuItem: Locator;
  readonly createRoleModal: Locator;
  readonly nameField: Locator;
  readonly idField: Locator;
  readonly createRoleModalCancelButton: Locator;
  readonly createRoleSubButton: Locator;
  readonly editRoleModal: Locator;
  readonly editNameField: Locator;
  readonly editRoleModalCancelButton: Locator;
  readonly editRoleModalUpdateButton: Locator;
  readonly deleteRoleModal: Locator;
  readonly deleteRoleModalCancelButton: Locator;
  readonly deleteRoleModalDeleteButton: Locator;
  readonly roleCell: (name: string) => Locator;
  readonly rolesHeading: Locator;
  readonly assignUserButton: Locator;
  readonly assignUserModal: Locator;
  readonly assignUserButtonModal: Locator;
  readonly searchBox: Locator;
  readonly searchBoxResult: Locator;
  readonly emptyStateLocator: Locator;

  constructor(page: Page) {
    this.page = page;
    this.rolesList = page.getByRole('table');
    this.createRoleButton = page.getByRole('button', {
      name: 'Create role',
    });
    this.rowActionsButton = (rowName) =>
      this.rolesList
        .getByRole('row')
        .filter({hasText: rowName})
        .getByRole('button', {name: 'Row actions'});
    this.editRoleMenuItem = page.getByRole('menuitem', {name: 'Edit role'});
    this.deleteRoleMenuItem = page.getByRole('menuitem', {name: 'Delete role'});
    this.createRoleModal = page.getByRole('dialog', {
      name: 'Create role',
    });
    this.idField = this.createRoleModal.getByRole('textbox', {
      name: 'Role ID',
      exact: true,
    });
    this.nameField = this.createRoleModal.getByRole('textbox', {
      name: 'Role name',
      exact: true,
    });
    this.createRoleModalCancelButton = this.createRoleModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.createRoleSubButton = this.createRoleModal.getByRole('button', {
      name: 'Create role',
    });
    this.editRoleModal = page.getByRole('dialog', {
      name: 'Edit role',
    });
    this.editNameField = this.editRoleModal.getByRole('textbox', {
      name: 'Role name',
      exact: true,
    });
    this.editRoleModalCancelButton = this.editRoleModal.getByRole('button', {
      name: 'Cancel',
    });
    this.editRoleModalUpdateButton = this.editRoleModal.getByRole('button', {
      name: 'Update role',
    });
    this.deleteRoleModal = page.getByRole('alertdialog', {
      name: 'Delete role',
    });
    this.deleteRoleModalCancelButton = this.deleteRoleModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.deleteRoleModalDeleteButton = this.deleteRoleModal.getByRole(
      'button',
      {
        name: 'Delete role',
      },
    );
    this.roleCell = (roleID: string) =>
      this.rolesList.getByRole('cell', {name: roleID, exact: true});
    this.rolesHeading = this.page.getByRole('heading', {name: 'Roles'});
    this.assignUserButton = page.getByRole('button', {name: 'Assign user'});
    this.assignUserModal = page.getByRole('dialog', {name: 'Assign user'});
    this.searchBox = this.assignUserModal.getByRole('combobox');
    // Results portal outside the dialog, so scope them to the page.
    this.searchBoxResult = page.getByRole('listbox');
    this.assignUserButtonModal = this.assignUserModal.getByRole('button', {
      name: 'Assign user',
      exact: true,
    });
    this.emptyStateLocator = page.getByText('No roles created yet');
  }
  async clickCreateRoles() {
    await this.createRoleButton.click();
  }

  async clickEditRoleButton(rowName: string) {
    await this.rowActionsButton(rowName).click();
    await this.editRoleMenuItem.click();
  }

  async createRole(role: {id: string; name: string}) {
    await this.clickCreateRoles();
    await expect(this.createRoleModal).toBeVisible();
    await this.idField.fill(role.id);
    await this.nameField.fill(role.name);
    await this.createRoleSubButton.click();
    await expect(this.createRoleModal).toBeHidden();
    const item = this.roleCell(role.name);
    await waitForItemInList(this.page, item, {
      timeout: 30000,
      clickNext: true,
    });
  }

  async clickRole(roleID: string) {
    const item = this.roleCell(roleID);
    await waitForItemInList(this.page, item, {
      clickNext: true,
      timeout: 30000,
    });
    await this.roleCell(roleID).click();
  }

  async assignUserToRole(userName: string) {
    // The search for a just-created user can return empty and get cached, so
    // reload and retry the whole flow.
    const maxRetries = 3;
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        await this.assignUserButton.click({timeout: 60000});
        await expect(this.assignUserModal).toBeVisible();
        await this.searchBox.click();
        await this.page.locator('[data-slot="command-input"]').fill(userName);
        const options = this.searchBoxResult
          .getByRole('option')
          .filter({hasText: userName});
        await expect(options.first()).toBeVisible({timeout: 30000});
        await options.first().click({timeout: 20000});
        // The open popover covers the confirm button; Escape closes it, not the dialog.
        await this.page.keyboard.press('Escape');
        await expect(this.searchBoxResult).toBeHidden();
        await this.assignUserButtonModal.click();
        await expect(this.assignUserModal).toBeHidden();
        return;
      } catch (error) {
        if (attempt === maxRetries) {
          throw error;
        }
        await sleep(10000);
        await this.page.reload();
      }
    }
  }

  async deleteRole(roleName: string) {
    await waitForItemInList(this.page, this.roleCell(roleName), {
      clickNext: true,
      timeout: 30000,
    });
    await expect(async () => {
      await expect(this.rowActionsButton(roleName)).toBeVisible({
        timeout: 20000,
      });
      await this.rowActionsButton(roleName).click({timeout: 20000});
      await this.deleteRoleMenuItem.click({timeout: 20000});
    }).toPass(defaultAssertionOptions);
    await expect(this.deleteRoleModal).toBeVisible();
    await this.deleteRoleModalDeleteButton.click();
    await expect(this.deleteRoleModal).toBeHidden();
  }
}
