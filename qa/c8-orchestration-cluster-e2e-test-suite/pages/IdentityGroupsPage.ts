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
import {
  findLocatorInPaginatedList,
  waitForItemInList,
} from '../utils/waitForItemInList';

export class IdentityGroupsPage {
  private page: Page;
  readonly groupsList: Locator;
  readonly assignedUsersList: Locator;
  readonly createGroupButton: Locator;
  readonly rowActionsButton: (rowName: string) => Locator;
  readonly editGroupMenuItem: Locator;
  readonly deleteGroupMenuItem: Locator;
  readonly createGroupModal: Locator;
  readonly createGroupIdField: Locator;
  readonly createNameField: Locator;
  readonly createDescriptionField: Locator;
  readonly createGroupModalCancelButton: Locator;
  readonly createGroupModalCreateButton: Locator;
  readonly editGroupModal: Locator;
  readonly editNameField: Locator;
  readonly editDescriptionField: Locator;
  readonly editGroupModalCancelButton: Locator;
  readonly editGroupModalUpdateButton: Locator;
  readonly deleteGroupModal: Locator;
  readonly deleteGroupModalCancelButton: Locator;
  readonly deleteGroupModalDeleteButton: Locator;
  readonly emptyStateLocator: Locator;
  readonly assignUserButton: Locator;
  readonly assignUserModal: Locator;
  readonly searchBox: Locator;
  readonly searchBoxResult: Locator;
  readonly assignUserButtonModal: Locator;
  readonly selectGroupRow: (name: string) => Locator;
  readonly groupCell: (name: string) => Locator;
  readonly groupsHeading: Locator;

  constructor(page: Page) {
    this.page = page;
    this.groupsList = page.getByRole('table');
    // The members table on a group's detail page.
    this.assignedUsersList = page.getByRole('table');

    this.selectGroupRow = (name) =>
      this.groupsList.getByRole('row', {name: name});

    this.groupCell = (name) =>
      this.groupsList.getByRole('cell', {name, exact: true});

    this.createGroupButton = page.getByRole('button', {
      name: /Create( a)? group/,
    });

    this.rowActionsButton = (rowName) =>
      this.groupsList
        .getByRole('row')
        .filter({hasText: rowName})
        .getByRole('button', {name: 'Row actions'});
    this.editGroupMenuItem = page.getByRole('menuitem', {name: 'Edit group'});
    this.deleteGroupMenuItem = page.getByRole('menuitem', {
      name: 'Delete group',
    });

    this.createGroupModal = page.getByRole('dialog', {
      name: 'Create group',
    });
    this.createGroupIdField = this.createGroupModal.getByRole('textbox', {
      name: 'Group ID',
    });
    this.createNameField = this.createGroupModal.getByRole('textbox', {
      name: 'Group name',
    });
    this.createDescriptionField = this.createGroupModal.getByRole('textbox', {
      name: 'Description',
    });
    this.createGroupModalCancelButton = this.createGroupModal.getByRole(
      'button',
      {
        name: 'Cancel',
      },
    );
    this.createGroupModalCreateButton = this.createGroupModal.getByRole(
      'button',
      {
        name: 'Create group',
      },
    );

    this.editGroupModal = page.getByRole('dialog', {
      name: 'Edit group',
    });
    this.editNameField = this.editGroupModal.getByRole('textbox', {
      name: 'Group name',
    });
    this.editDescriptionField = this.editGroupModal.getByRole('textbox', {
      name: 'Description',
    });
    this.editGroupModalCancelButton = this.editGroupModal.getByRole('button', {
      name: 'Cancel',
    });
    this.editGroupModalUpdateButton = this.editGroupModal.getByRole('button', {
      name: 'Update group',
    });

    this.deleteGroupModal = page.getByRole('alertdialog', {
      name: 'Delete group',
    });
    this.deleteGroupModalCancelButton = this.deleteGroupModal.getByRole(
      'button',
      {
        name: 'Cancel',
      },
    );
    this.deleteGroupModalDeleteButton = this.deleteGroupModal.getByRole(
      'button',
      {
        name: 'Delete group',
      },
    );
    this.emptyStateLocator = page.getByText('No groups created yet');
    this.assignUserButton = page.getByRole('button', {name: 'Assign user'});
    this.assignUserModal = page.getByRole('dialog', {name: 'Assign user'});
    this.searchBox = this.assignUserModal.getByRole('combobox');
    // Results portal outside the dialog, so scope them to the page.
    this.searchBoxResult = page.getByRole('listbox');
    this.assignUserButtonModal = this.assignUserModal.getByRole('button', {
      name: 'Assign user',
      exact: true,
    });
    this.groupsHeading = this.page.getByRole('heading', {name: 'Groups'});
  }

  async navigateToGroups() {
    await this.page.goto(relativizePath(Paths.groups()));
  }

  async createGroup(groupId: string, groupName: string, description?: string) {
    await this.createGroupButton.click();
    await this.createGroupIdField.fill(groupId);
    await this.createNameField.fill(groupName);
    if (description) {
      await this.createDescriptionField.fill(description);
    }
    await this.createGroupModalCreateButton.click();
    await expect(this.createGroupModal).toBeHidden();
  }

  async editGroup(
    currentName: string,
    newName: string,
    newDescription?: string,
  ) {
    await this.rowActionsButton(currentName).click();
    await this.editGroupMenuItem.click();
    await expect(this.editGroupModal).toBeVisible();
    await this.editNameField.fill(newName);
    if (newDescription) {
      await this.editDescriptionField.fill(newDescription);
    }
    await this.editGroupModalUpdateButton.click();
    await expect(this.editGroupModal).toBeHidden();
  }

  async deleteGroup(groupName: string) {
    const group = this.groupCell(groupName);
    await waitForItemInList(this.page, group, {
      clickNext: true,
      timeout: 30000,
    });
    await expect(async () => {
      await expect(this.rowActionsButton(groupName)).toBeVisible({
        timeout: 20000,
      });
      await this.rowActionsButton(groupName).click({timeout: 20000});
      await this.deleteGroupMenuItem.click({timeout: 20000});
    }).toPass(defaultAssertionOptions);
    await expect(this.deleteGroupModal).toBeVisible();
    await this.deleteGroupModalDeleteButton.click();
    await expect(this.deleteGroupModal).toBeHidden();
  }

  async clickGroupId(groupName: string) {
    await findLocatorInPaginatedList(this.page, this.groupCell(groupName));
    await this.selectGroupRow(groupName).click();
  }

  async assignUserToGroup(userName: string) {
    await this.assignUserButton.click();
    await expect(this.assignUserModal).toBeVisible();
    await this.searchBox.click();
    await this.page.locator('[data-slot="command-input"]').fill(userName);
    const option = this.searchBoxResult
      .getByRole('option')
      .filter({hasText: userName})
      .first();
    await expect(option).toBeVisible({timeout: 30000});
    await option.click({timeout: 20000});
    // The open popover covers the confirm button; Escape closes it, not the dialog.
    await this.page.keyboard.press('Escape');
    await expect(this.searchBoxResult).toBeHidden();
    await this.assignUserButtonModal.click();
    await expect(this.assignUserModal).toBeHidden();
    await waitForItemInList(
      this.page,
      this.assignedUsersList
        .getByRole('row')
        .filter({hasText: userName})
        .getByRole('cell')
        .first(),
      {timeout: 60000},
    );
  }
}
