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
    // On the new design system the assign-user modal's search field is a cmdk
    // combobox ("Search by Username or Name") rather than Carbon's searchbox.
    this.assignUserModalSearchField = this.assignUserModal.getByRole(
      'combobox',
      {name: 'Search by Username or Name'},
    );
    // The results render in a Radix popover that portals as a *sibling* of the
    // dialog (DS #496), so the listbox is not a descendant of the modal —
    // scope it to the page, not to `assignUserModal`.
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
    const assignedCell = this.assignedUsersList.getByRole('cell', {
      name: user.email,
    });

    // The assign-user modal's cmdk search is debounced + server-driven, and the
    // option for a just-created user is eventually consistent: a single query
    // can return empty and get cached by react-query, so waiting longer on one
    // search does not recover. Mirror the authorizations owner search (#51442)
    // remedy and reload between attempts to reset the query cache.
    //
    // Only the modal interaction is retried, and only while the user is still
    // unassigned: `AssignMembersModal` passes the assigned users as `excluded`,
    // so once a submit has landed the option is gone from the results for good
    // and re-running the search could only ever fail.
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
    await this.assignUserModalSearchField.fill(username);
    // Match on the username: `EntitySearchMultiSelect` passes `getId` as the
    // option title, so it is always present regardless of the display name.
    const option = this.assignUserModalSearchResult
      .getByRole('option')
      .filter({hasText: username})
      .first();
    await expect(option).toBeVisible({timeout: 30000});
    await option.click({timeout: 20000});
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
