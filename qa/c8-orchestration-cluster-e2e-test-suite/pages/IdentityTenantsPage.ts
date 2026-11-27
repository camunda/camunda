/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';
import {defaultAssertionOptions} from '../utils/constants';
import {waitForItemInList} from 'utils/waitForItemInList';

export class IdentityTenantsPage {
  private page: Page;
  readonly tenantsList: Locator;
  readonly assignedUsersList: Locator;
  readonly createTenantButton: Locator;
  readonly rowActionsButton: (rowName: string) => Locator;
  readonly editTenantMenuItem: Locator;
  readonly deleteTenantMenuItem: Locator;
  readonly createTenantModal: Locator;
  readonly tenantCreatedHeading: Locator;
  readonly tenantCreatedDismissButton: Locator;
  readonly tenantFieldId: Locator;
  readonly tenantNameField: Locator;
  readonly tenantDescriptionField: Locator;
  readonly createTenantModalCancelButton: Locator;
  readonly createTenantModalButton: Locator;
  readonly editTenantModal: Locator;
  readonly editTenantNameField: Locator;
  readonly editTenantDescriptionField: Locator;
  readonly editTenantModalButton: Locator;
  readonly deleteTenantModal: Locator;
  readonly deleteTenantModalCancelButton: Locator;
  readonly deleteTenantModalDeleteButton: Locator;
  readonly assignUserButton: Locator;
  readonly assignUserModal: Locator;
  readonly assignUserCombobox: Locator;
  readonly assignUserSearchInput: Locator;
  readonly assignUserSearchboxResult: Locator;
  readonly assignUserOption: (username: string) => Locator;
  readonly confirmAssignmentButton: Locator;
  readonly openTenantDetails: (rowName: string) => Locator;
  readonly removeUserMenuItem: Locator;
  readonly removeUserModal: Locator;
  readonly confirmRemoveUserButton: Locator;
  readonly usersEmptyState: Locator;
  readonly userRow: (userName: string) => Locator;
  readonly tenantCell: (tenantName: string) => Locator;
  readonly tenantRow: (tenantName: string) => Locator;
  readonly tenantsHeading: Locator;

  constructor(page: Page) {
    this.page = page;

    this.tenantsList = page.getByRole('table');
    this.assignedUsersList = page.getByRole('table');
    this.createTenantButton = page.getByRole('button', {
      name: 'Create tenant',
    });
    this.rowActionsButton = (rowName) =>
      this.tenantsList
        .getByRole('row')
        .filter({hasText: rowName})
        .getByRole('button', {name: 'Row actions'});
    this.editTenantMenuItem = page.getByRole('menuitem', {
      name: 'Edit',
      exact: true,
    });
    this.deleteTenantMenuItem = page.getByRole('menuitem', {
      name: 'Delete',
      exact: true,
    });

    this.createTenantModal = page.getByRole('dialog', {
      name: 'Create tenant',
      exact: true,
    });
    this.tenantCreatedHeading = page.getByRole('heading', {
      name: 'Tenant created',
    });
    this.tenantCreatedDismissButton = page.getByRole('button', {
      name: 'Got it',
    });
    this.tenantFieldId = this.createTenantModal.getByRole('textbox', {
      name: 'Tenant ID',
    });
    this.tenantNameField = this.createTenantModal.getByRole('textbox', {
      name: 'Name',
      exact: true,
    });
    this.tenantDescriptionField = this.createTenantModal.getByRole('textbox', {
      name: 'Description',
    });
    this.createTenantModalCancelButton = this.createTenantModal.getByRole(
      'button',
      {name: 'Cancel', exact: true},
    );
    this.createTenantModalButton = this.createTenantModal.getByRole('button', {
      name: 'Create',
      exact: true,
    });
    this.editTenantModal = page.getByRole('dialog', {
      name: 'Edit tenant',
    });
    this.editTenantNameField = this.editTenantModal.getByRole('textbox', {
      name: 'Name',
      exact: true,
    });
    this.editTenantDescriptionField = this.editTenantModal.getByRole(
      'textbox',
      {
        name: 'Description',
      },
    );
    this.editTenantModalButton = this.editTenantModal.getByRole('button', {
      name: 'Save',
      exact: true,
    });

    this.deleteTenantModal = page.getByRole('alertdialog', {
      name: 'Delete tenant',
    });
    this.deleteTenantModalCancelButton = this.deleteTenantModal.getByRole(
      'button',
      {name: 'Cancel', exact: true},
    );
    this.deleteTenantModalDeleteButton = this.deleteTenantModal.getByRole(
      'button',
      {name: 'Delete', exact: true},
    );

    this.openTenantDetails = (rowName) =>
      page.getByRole('cell', {name: rowName});
    this.assignUserButton = page.getByRole('button', {
      name: 'Assign user',
    });
    this.assignUserModal = page.getByRole('dialog', {
      name: 'Assign user',
    });
    this.assignUserCombobox = this.assignUserModal.getByRole('combobox', {
      name: 'Users',
    });
    this.assignUserSearchInput = page.locator('[data-slot="command-input"]');
    this.assignUserSearchboxResult = page.getByRole('listbox');
    this.assignUserOption = (username) =>
      this.assignUserSearchboxResult
        .getByRole('option')
        .filter({hasText: username})
        .first();
    this.confirmAssignmentButton = this.assignUserModal.getByRole('button', {
      name: 'Assign user',
    });
    this.usersEmptyState = page.getByText(
      'No users assigned to this tenant yet',
    );
    this.removeUserMenuItem = page.getByRole('menuitem', {
      name: 'Remove user',
      exact: true,
    });
    this.userRow = (userName) =>
      this.tenantsList.getByRole('row', {name: userName});
    this.tenantCell = (tenantName) =>
      this.tenantsList.getByRole('cell', {name: tenantName});
    this.tenantRow = (tenantName) =>
      this.tenantsList.getByRole('row', {name: tenantName});

    this.removeUserModal = page.getByRole('alertdialog', {
      name: 'Remove user',
    });
    this.confirmRemoveUserButton = this.removeUserModal.getByRole('button', {
      name: 'Remove user',
    });
    this.tenantsHeading = this.page.getByRole('heading', {name: 'Tenants'});
  }

  async navigateToTenants() {
    await this.page.goto(relativizePath(Paths.tenants()));
  }

  async fillTenantId(tenantId: string) {
    await this.tenantFieldId.fill(tenantId);
  }

  async fillTenantName(name: string) {
    await this.tenantNameField.fill(name);
  }

  async fillTenantDescription(description: string) {
    await this.tenantDescriptionField.fill(description);
  }

  async fillAssignUserSearch(username: string) {
    await this.assignUserCombobox.click();
    await this.assignUserSearchInput.fill(username);
  }

  async assignUserToTenant(user: {id: string}) {
    await this.assignUserButton.click();
    await expect(this.assignUserModal).toBeVisible();
    await this.fillAssignUserSearch(user.id);
    const userOption = this.assignUserOption(user.id);
    await expect(userOption).toBeVisible({timeout: 30000});
    await userOption.click();
    await this.assignUserModal.getByRole('heading').click();
    await expect(this.assignUserSearchboxResult).toBeHidden();
    await this.confirmAssignmentButton.click();
    await expect(this.assignUserModal).toBeHidden();
    await waitForItemInList(
      this.page,
      this.assignedUsersList.getByRole('cell', {name: user.id, exact: true}),
      {timeout: 30000},
    );
  }

  async removeUserFromTenant(userName: string) {
    await this.rowActionsButton(userName).click();
    await this.removeUserMenuItem.click();
    await expect(this.removeUserModal).toBeVisible();
    await this.confirmRemoveUserButton.click();
    await expect(this.removeUserModal).toBeHidden();
  }

  async deleteTenant(tenantName: string) {
    await expect(async () => {
      await expect(this.rowActionsButton(tenantName)).toBeVisible({
        timeout: 20000,
      });
      await this.rowActionsButton(tenantName).click();
      await this.deleteTenantMenuItem.click({timeout: 20000});
    }).toPass(defaultAssertionOptions);

    await expect(this.deleteTenantModal).toBeVisible();
    await this.deleteTenantModalDeleteButton.click();
    await expect(this.deleteTenantModal).toBeHidden();
  }

  async editTenant(
    currentName: string,
    updatedName: string,
    updatedDescription?: string,
  ) {
    await expect(async () => {
      await expect(this.rowActionsButton(currentName)).toBeVisible({
        timeout: 20000,
      });
      await this.rowActionsButton(currentName).click();
      await this.editTenantMenuItem.click({timeout: 20000});
    }).toPass(defaultAssertionOptions);

    await expect(this.editTenantModal).toBeVisible();
    await this.editTenantNameField.fill(updatedName);
    if (updatedDescription) {
      await this.editTenantDescriptionField.fill(updatedDescription);
    }
    await this.editTenantModalButton.click();
    await expect(this.editTenantModal).toBeHidden();
  }

  async createTenant(tenant: {
    tenantId: string;
    name: string;
    description: string;
  }) {
    await this.createTenantButton.click();
    await expect(this.createTenantModal).toBeVisible();
    await this.fillTenantId(tenant.tenantId);
    await this.fillTenantName(tenant.name);
    await this.fillTenantDescription(tenant.description);
    await this.createTenantModalButton.click();
    await expect(this.tenantCreatedHeading).toBeVisible();
    await this.tenantCreatedDismissButton.click();
    await expect(this.tenantCreatedHeading).toBeHidden();
  }
}
