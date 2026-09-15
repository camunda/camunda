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
  readonly editRoleButton: (rowName?: string) => Locator;
  readonly deleteRoleButton: (rowName?: string) => Locator;
  readonly createRoleModal: Locator;
  readonly closeCreateRoleModal: Locator;
  readonly nameField: Locator;
  readonly idField: Locator;
  readonly createRoleModalCancelButton: Locator;
  readonly createRoleSubButton: Locator;
  readonly editRoleModal: Locator;
  readonly closeEditRoleModal: Locator;
  readonly editNameField: Locator;
  readonly editRoleModalCancelButton: Locator;
  readonly editRoleModalUpdateButton: Locator;
  readonly deleteRoleModal: Locator;
  readonly closeDeleteRoleModal: Locator;
  readonly deleteRoleModalCancelButton: Locator;
  readonly deleteRoleModalDeleteButton: Locator;
  readonly roleCell: (name: string) => Locator;
  readonly rolesHeading: Locator;
  readonly assignUserButton: Locator;
  readonly assignUserModal: Locator;
  readonly assignUserButtonModal: Locator;
  readonly searchBox: Locator;
  readonly searchBoxResult: Locator;
  readonly removeButton: Locator;
  readonly removeUserModalButton: Locator;
  readonly emptyStateLocator: Locator;

  constructor(page: Page) {
    this.page = page;
    this.rolesList = page.getByRole('table');
    this.createRoleButton = page.getByRole('button', {
      name: 'Create role',
    });
    this.editRoleButton = (rowName) =>
      this.rolesList.getByRole('row', {name: rowName}).getByLabel('Edit role');
    // The design-system list renders the destructive row action as a button
    // labelled by its visible text, not by `aria-label` (`entityListV2` sets
    // `iconOnly: !!Icon && !isDangerous`), so match it by role and accessible
    // name, as `IdentityUsersPage.deleteUserButton` already does. Edit stays on
    // `getByLabel` because it is icon-only and keeps its `aria-label`.
    this.deleteRoleButton = (rowName) =>
      this.rolesList
        .getByRole('row', {name: rowName})
        .getByRole('button', {name: 'Delete', exact: true});
    this.createRoleModal = page.getByRole('dialog', {
      name: 'Create role',
    });
    this.closeCreateRoleModal = this.createRoleModal.getByRole('button', {
      name: 'Close',
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
    this.closeEditRoleModal = this.editRoleModal.getByRole('button', {
      name: 'Close',
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
    this.deleteRoleModal = page.getByRole('dialog', {
      name: 'Delete role',
    });
    this.closeDeleteRoleModal = this.deleteRoleModal.getByRole('button', {
      name: 'Close',
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
    // Same design-system migration IdentityRolesDetailsPage.ts already
    // accounts for: the search field is a cmdk combobox now, not a Carbon
    // searchbox, and its results render in a Radix popover that portals as a
    // *sibling* of the dialog (DS #496) -- scope the results to the page, not
    // to the modal.
    this.searchBox = this.assignUserModal.getByRole('combobox', {
      name: 'Search by name, email, or username',
    });
    this.searchBoxResult = page.getByRole('listbox');
    this.assignUserButtonModal = this.assignUserModal.getByRole('button', {
      name: 'assign user',
    });
    this.removeButton = page.getByRole('button', {name: 'Remove'});
    this.removeUserModalButton = page.getByRole('button', {
      name: 'Remove user',
    });
    this.emptyStateLocator = page.getByText('No roles created yet');
  }
  async clickCreateRoles() {
    await this.createRoleButton.click();
  }

  async fillRoleId(rowName: string) {
    await this.editRoleButton(rowName).click();
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
    // The assign-user modal's cmdk search is debounced + server-driven, and
    // the option for a just-created user is eventually consistent -- a single
    // query can return empty and get cached, so waiting longer on one search
    // does not recover. Mirror IdentityRolesDetailsPage.ts's remedy for the
    // same modal: reload and retry the whole open-search-select flow.
    const maxRetries = 3;
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        await this.assignUserButton.click({timeout: 60000});
        await expect(this.assignUserModal).toBeVisible();
        await this.searchBox.fill(userName);
        const option = this.searchBoxResult
          .getByRole('option')
          .filter({hasText: userName})
          .first();
        await expect(option).toBeVisible({timeout: 30000});
        await option.click({timeout: 20000});
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
      await expect(this.deleteRoleButton(roleName)).toBeVisible({
        timeout: 20000,
      });
      await this.rolesHeading.click();
      await this.deleteRoleButton(roleName).click({timeout: 20000});
    }).toPass(defaultAssertionOptions);
    await expect(this.deleteRoleModal).toBeVisible();
    await this.deleteRoleModalDeleteButton.click();
    await expect(this.deleteRoleModal).toBeHidden();
  }
}
