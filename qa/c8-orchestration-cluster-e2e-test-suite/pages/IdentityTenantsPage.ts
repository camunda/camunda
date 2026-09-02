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
import { waitForItemInList } from 'utils/waitForItemInList';

export class IdentityTenantsPage {
  private page: Page;
  readonly tenantsList: Locator;
  readonly assignedUsersList: Locator;
  readonly createTenantButton: Locator;
  readonly editTenantButton: (rowName?: string) => Locator;
  readonly deleteTenantButton: (rowName?: string) => Locator;
  readonly createTenantModal: Locator;
  readonly closeCreateTenantModal: Locator;
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
  readonly closeDeleteTenantModal: Locator;
  readonly deleteTenantModalCancelButton: Locator;
  readonly deleteTenantModalDeleteButton: Locator;
  readonly assignUserButton: Locator;
  readonly assignUserModal: Locator;
  readonly assignUserSearchbox: Locator;
  readonly assignUserSearchboxResult: Locator;
  readonly assignUserOption: (username: string) => Locator;
  readonly confirmAssignmentButton: Locator;
  readonly openTenantDetails: (rowName: string) => Locator;
  readonly removeUserButton: (rowName?: string) => Locator;
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
    this.editTenantButton = (rowName) =>
      this.tenantsList.getByRole('row', {name: rowName}).getByLabel('Edit');
    this.deleteTenantButton = (rowName) =>
      this.tenantsList.getByRole('row', {name: rowName}).getByRole('button', {name: 'Delete', exact: true});

    this.createTenantModal = page.getByRole('dialog', {
      name: 'Create new tenant',
    });
    this.closeCreateTenantModal = this.createTenantModal.getByRole('button', {
      name: 'Close',
    });
    this.tenantFieldId = this.createTenantModal.getByRole('textbox', {
      name: 'Tenant ID',
    });
    this.tenantNameField = this.createTenantModal.getByRole('textbox', {
      name: 'Tenant name',
    });
    this.tenantDescriptionField = this.createTenantModal.getByRole('textbox', {
      name: 'Description',
    });
    this.createTenantModalCancelButton = this.createTenantModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.createTenantModalButton = this.createTenantModal.getByRole('button', {
      name: 'Create tenant',
    });
    this.editTenantModal = page.getByRole('dialog', {
      name: 'Edit tenant',
    });
    this.editTenantNameField = this.editTenantModal.getByRole('textbox', {
      name: 'Tenant name',
    });
    this.editTenantDescriptionField = this.editTenantModal.getByRole(
      'textbox',
      {
        name: 'Description',
      },
    );
    this.editTenantModalButton = this.editTenantModal.getByRole('button', {
      name: 'Edit tenant',
    });

    this.deleteTenantModal = page.getByRole('dialog', {
      name: 'Delete tenant',
    });
    this.closeDeleteTenantModal = this.deleteTenantModal.getByRole('button', {
      name: 'Close',
    });
    this.deleteTenantModalCancelButton = this.deleteTenantModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.deleteTenantModalDeleteButton = this.deleteTenantModal.getByRole(
      'button',
      {name: 'Delete tenant'},
    );

    this.openTenantDetails = (rowName) =>
      page.getByRole('cell', {name: rowName});
    this.assignUserButton = page.getByRole('button', {
      name: 'Assign user',
    });
    this.assignUserModal = page.getByRole('dialog', {
      name: 'Assign user',
    });
    this.assignUserSearchbox = this.assignUserModal.getByRole('combobox', {
      name: 'Search by username',
    });
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
    this.removeUserButton = (rowName) =>
      page
        .getByRole('row', {name: rowName})
        .getByRole('button', {name: 'Remove'});
    this.userRow = (userName) =>
      this.tenantsList.getByRole('row', {name: userName});
    this.tenantCell = (tenantName) =>
      this.tenantsList.getByRole('cell', {name: tenantName});
    this.tenantRow = (tenantName) =>
      this.tenantsList.getByRole('row', {name: tenantName});

    this.removeUserModal = page.getByRole('dialog', {
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
    await this.assignUserSearchbox.fill(username);
  }

  async assignUserToTenant(user: {id: string}) {
    await this.assignUserButton.click();
    await expect(this.assignUserModal).toBeVisible();
    // The assign-user search filters users by `username` and renders the
    // username as the option title, so `user.id` drives both steps.
    await this.fillAssignUserSearch(user.id);
    const userOption = this.assignUserOption(user.id);
    await expect(userOption).toBeVisible({timeout: 30000});
    await userOption.click();
    // The assign-user search is debounced + server-driven, so the option can
    // outlast the 10s default actionTimeout on a loaded cluster.
    await this.confirmAssignmentButton.click();
    await expect(this.assignUserModal).toBeHidden();
    await waitForItemInList(
      this.page,
      this.assignedUsersList.getByRole('cell', {name: user.id, exact: true}),
      {timeout: 30000},
    );
  }

  async removeUserFromTenant(userName: string) {
    await this.removeUserButton(userName).click();
    await expect(this.removeUserModal).toBeVisible();
    await this.confirmRemoveUserButton.click();
    await expect(this.removeUserModal).toBeHidden();
  }

  async deleteTenant(tenantName: string) {
    await expect(async () => {
      await expect(this.deleteTenantButton(tenantName)).toBeVisible({
        timeout: 20000,
      });
      await this.tenantsHeading.click();
      await this.deleteTenantButton(tenantName).click();
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
      await expect(this.editTenantButton(currentName)).toBeVisible({
        timeout: 20000,
      });
      await this.tenantsHeading.click();
      await this.editTenantButton(currentName).click();
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
    await expect(this.createTenantModal).toBeHidden();
  }
}
