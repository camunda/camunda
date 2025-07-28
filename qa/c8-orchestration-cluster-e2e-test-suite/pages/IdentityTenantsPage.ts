/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';

export class IdentityTenantsPage {
  private page: Page;
  readonly tenantsList: Locator;
  readonly createTenantButton: Locator;
  readonly deleteTenantButton: (rowName?: string) => Locator;
  readonly createTenantModal: Locator;
  readonly closeCreateTenantModal: Locator;
  readonly tenantFieldId: Locator;
  readonly tenantNameField: Locator;
  readonly tenantDescriptionField: Locator;
  readonly createTenantModalCancelButton: Locator;
  readonly createTenantModalButton: Locator;
  readonly deleteTenantModal: Locator;
  readonly closeDeleteTenantModal: Locator;
  readonly deleteTenantModalCancelButton: Locator;
  readonly deleteTenantModalDeleteButton: Locator;
  readonly assignUserButton: Locator;
  readonly assignUserModal: Locator;
  readonly assignUserSearchbox: Locator;
  readonly assignUserNameOption: (name: string) => Locator;
  readonly confirmAssignmentButton: Locator;
  readonly openTenantDetails: (rowName: string) => Locator;
  readonly removeUserButton: (rowName?: string) => Locator;
  readonly removeUserModal: Locator;
  readonly confirmRemoveUserButton: Locator;
  readonly usersEmptyState: Locator;
  readonly userRow: (userName: string) => Locator;
  readonly tenantCell: (tenantName: string) => Locator;
  readonly tenantRow: (tenantName: string) => Locator;

  constructor(page: Page) {
    this.page = page;

    this.tenantsList = page.getByRole('table');
    this.createTenantButton = page.getByRole('button', {
      name: 'Create tenant',
    });
    this.deleteTenantButton = (rowName) =>
      this.tenantsList.getByRole('row', {name: rowName}).getByLabel('Delete');

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
      name: 'assign user',
    });
    this.assignUserSearchbox = this.assignUserModal.getByRole('searchbox', {
      name: 'Search by full name',
    });
    this.assignUserNameOption = (name) =>
      this.assignUserModal.locator('.cds--list-box__menu-item', {
        hasText: name,
      });
    this.confirmAssignmentButton = this.assignUserModal.getByRole('button', {
      name: 'Assign user',
    });
    this.usersEmptyState = page.getByText('Assign users to this Tenant');
    this.removeUserButton = (rowName) =>
      page.getByRole('row', {name: rowName}).getByLabel('Remove');
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

  async fillAssignUserName(userName: string) {
    await this.assignUserSearchbox.fill(userName);
  }

  async assignUserToTenant(user: {id: string; name: string}) {
    await this.assignUserButton.click();
    await expect(this.assignUserModal).toBeVisible();
    await this.fillAssignUserName(user.name);
    await this.assignUserNameOption(user.id).click();
    await this.confirmAssignmentButton.click();
    await expect(this.assignUserModal).toBeHidden();
  }

  async removeUserFromTenant(userName: string) {
    await this.removeUserButton(userName).click();
    await expect(this.removeUserModal).toBeVisible();
    await this.confirmRemoveUserButton.click();
    await expect(this.removeUserModal).toBeHidden();
  }

  async deleteTenant(tenantName: string) {
    await this.deleteTenantButton(tenantName).click();
    await expect(this.deleteTenantModal).toBeVisible();
    await this.deleteTenantModalDeleteButton.click();
    await expect(this.deleteTenantModal).toBeHidden();
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
