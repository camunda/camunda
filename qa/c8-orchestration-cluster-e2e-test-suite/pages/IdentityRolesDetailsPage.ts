/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {waitForItemInList} from 'utils/waitForItemInList';
import {sleep} from 'utils/sleep';

export class IdentityRolesDetailsPage {
  private page: Page;
  readonly assignedUsersList: Locator;
  readonly assignUserButton: Locator;
  readonly unassignUserButton: (rowName?: string) => Locator;
  readonly assignUserModal: Locator;
  readonly assignUserModalSearchField: Locator;
  readonly assignUserModalSearchResult: Locator;
  readonly assignUserModalCancelButton: Locator;
  readonly assignUserModalAssignButton: Locator;
  readonly unassignUserModal: Locator;
  readonly unassignUserModalCancelButton: Locator;
  readonly unassignUserModalRemoveButton: Locator;
  readonly emptyState: Locator;
  readonly userCell: (name: string) => Locator;
  readonly userRow: (userName: string) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.assignedUsersList = page.getByRole('table');
    this.assignUserButton = page.getByRole('button', {
      name: 'Assign user',
      exact: true,
    });
    this.unassignUserButton = (userName) =>
      this.assignedUsersList.getByRole('button', {
        name: `Remove user ${userName}`,
        exact: true,
      });
    this.assignUserModal = page.getByRole('dialog', {
      name: 'Assign user',
    });
    this.assignUserModalSearchField =
      this.assignUserModal.getByRole('combobox');
    // Results portal outside the dialog, so scope them to the page.
    this.assignUserModalSearchResult = page.getByRole('listbox');
    this.assignUserModalCancelButton = this.assignUserModal.getByRole(
      'button',
      {
        name: 'Cancel',
      },
    );
    this.assignUserModalAssignButton = this.assignUserModal.getByRole(
      'button',
      {
        name: 'Assign user',
        exact: true,
      },
    );
    this.unassignUserModal = page.getByRole('alertdialog', {
      name: 'Remove user',
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
        name: 'Remove user',
        exact: true,
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
    const assignedCell = this.assignedUsersList.getByRole('cell', {
      name: user.username,
      exact: true,
    });

    const maxRetries = 3;
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        await this.selectUserInAssignModal(user.username);
        break;
      } catch (error) {
        if (attempt === maxRetries) {
          throw error;
        }
        await sleep(10000);
        await this.page.reload();
        if (await assignedCell.isVisible()) {
          break;
        }
      }
    }

    await waitForItemInList(this.page, assignedCell, {
      timeout: 60000,
      emptyStateLocator: this.emptyState,
    });
  }

  private async selectUserInAssignModal(username: string): Promise<void> {
    await this.assignUserButton.click();
    await expect(this.assignUserModal).toBeVisible();
    await this.assignUserModalSearchField.click();
    await this.page.locator('[data-slot="command-input"]').fill(username);
    const option = this.assignUserModalSearchResult
      .getByRole('option')
      .filter({hasText: username})
      .first();
    await expect(option).toBeVisible({timeout: 30000});
    await option.click({timeout: 20000});
    // The open popover covers the confirm button; Escape closes it, not the dialog.
    await this.page.keyboard.press('Escape');
    await expect(this.assignUserModalSearchResult).toBeHidden();
    await this.assignUserModalAssignButton.click();
    await expect(this.assignUserModal).toBeHidden();
  }

  async unassignUserFromRole(userName: string): Promise<void> {
    const userRow = this.userRow(userName);
    await expect(userRow).toBeVisible({timeout: 30000});
    await this.unassignUserButton(userName).click();
    await this.unassignUserModalRemoveButton.click({timeout: 30000});
  }
}
