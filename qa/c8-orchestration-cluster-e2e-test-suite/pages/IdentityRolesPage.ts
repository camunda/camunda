/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {waitForItemInList} from 'utils/waitForItemInList';

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

  constructor(page: Page) {
    this.page = page;
    this.rolesList = page.getByRole('table');
    this.createRoleButton = page.getByRole('button', {
      name: 'Create role',
    });
    this.editRoleButton = (rowName) =>
      this.rolesList.getByRole('row', {name: rowName}).getByLabel('Edit role');
    this.deleteRoleButton = (rowName) =>
      this.rolesList.getByRole('row', {name: rowName}).getByLabel('Delete');

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

    await waitForItemInList(this.page, item, {timeout: 60000});
  }

  async clickRole(roleID: string) {
    await expect(this.roleCell(roleID)).toBeVisible({timeout: 60000});
    await this.roleCell(roleID).click();
  }

  async deleteRole(roleName: string) {
    await this.deleteRoleButton(roleName).click();
    await expect(this.deleteRoleModal).toBeVisible();
    await this.deleteRoleModalDeleteButton.click();
    await expect(this.deleteRoleModal).toBeHidden();
  }
}
