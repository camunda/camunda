/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {waitForItemInList} from 'utils/waitForItemInList';

export class IdentityRolesDetailsPage {
  private page: Page;
  readonly assignedUsersList: Locator;
  readonly assignUserButton: Locator;
  readonly unassignUserButton: (rowName?: string) => Locator;
  readonly assignUserModal: Locator;
  readonly closeAssignUserModal: Locator;
  readonly assignUserModalSearchField: Locator;
  readonly assignUserModalSearchResult: Locator;
  readonly assignUserModalCancelButton: Locator;
  readonly assignUserModalAssignButton: Locator;
  readonly unassignUserModal: Locator;
  readonly closeUnassignUserModal: Locator;
  readonly unassignUserModalCancelButton: Locator;
  readonly unassignUserModalRemoveButton: Locator;
  readonly emptyState: Locator;
  readonly userCell: (name: string) => Locator;
  readonly userRow: (userName: string) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.assignedUsersList = page.getByRole('table');
    this.assignUserButton = page.getByRole('button', {
      name: 'assign user',
    });
    this.unassignUserButton = (rowName) =>
      this.assignedUsersList
        .getByRole('row', {name: rowName})
        .getByLabel('Remove');
    this.assignUserModal = page.getByRole('dialog', {
      name: 'Assign user',
    });
    this.closeAssignUserModal = this.assignUserModal.getByRole('button', {
      name: 'Close',
    });
    this.assignUserModalSearchField =
      this.assignUserModal.getByRole('searchbox');
    this.assignUserModalSearchResult =
      this.assignUserModal.getByRole('listbox');
    this.assignUserModalCancelButton = this.assignUserModal.getByRole(
      'button',
      {
        name: 'Cancel',
      },
    );
    this.assignUserModalAssignButton = this.assignUserModal.getByRole(
      'button',
      {
        name: 'assign user',
      },
    );
    this.unassignUserModal = page.getByRole('dialog', {name: 'remove user'});
    this.closeUnassignUserModal = this.unassignUserModal.getByRole('button', {
      name: 'Close',
    });
    this.unassignUserModalCancelButton = this.unassignUserModal.getByRole(
      'button',
      {
        name: 'Cancel',
      },
    );
    this.unassignUserModalRemoveButton = this.unassignUserModal.getByRole(
      'button',
      {
        name: 'remove user',
      },
    );
    this.emptyState = page.getByText('No users assigned to this role yet');
    this.userCell = (userID: string) =>
      this.assignedUsersList.getByRole('cell', {name: userID, exact: true});
    this.userRow = (userName: string) =>
      this.page.getByRole('row').filter({hasText: userName});
  }

  async assignUser(user: {
    username: string;
    name: string;
    email: string;
    password: string;
  }) {
    await this.assignUserButton.click();
    await expect(this.assignUserModal).toBeVisible();
    await this.assignUserModalSearchField.fill(user.username);
    await this.assignUserModalSearchResult.getByText(user.name).click();
    await this.assignUserModalAssignButton.click();
    await expect(this.assignUserModal).toBeHidden();

    const item = this.assignedUsersList.getByRole('cell', {
      name: user.email,
    });

    await waitForItemInList(this.page, item, {
      emptyStateLocator: this.emptyState,
    });
  }

  async unassignUserFromRole(userName: string): Promise<void> {
    const userRow = this.userRow(userName);
    await expect(userRow).toBeVisible({timeout: 30000});
    await this.unassignUserButton(userName).click();
    await this.unassignUserModalRemoveButton.click({timeout: 30000});
  }
}
