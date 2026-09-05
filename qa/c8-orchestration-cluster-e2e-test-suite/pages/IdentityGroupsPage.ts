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
  readonly editGroupButton: (rowName?: string) => Locator;
  readonly deleteGroupButton: (rowName?: string) => Locator;
  readonly createGroupModal: Locator;
  readonly closeCreateGroupModal: Locator;
  readonly createGroupIdField: Locator;
  readonly createNameField: Locator;
  readonly createDescriptionField: Locator;
  readonly createGroupModalCancelButton: Locator;
  readonly createGroupModalCreateButton: Locator;
  readonly editGroupModal: Locator;
  readonly closeEditGroupModal: Locator;
  readonly editNameField: Locator;
  readonly editDescriptionField: Locator;
  readonly editGroupModalCancelButton: Locator;
  readonly editGroupModalUpdateButton: Locator;
  readonly deleteGroupModal: Locator;
  readonly closeDeleteGroupModal: Locator;
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

    this.editGroupButton = (rowName) =>
      this.groupsList.getByRole('row', {name: rowName}).getByLabel('Edit');
    // The design-system list renders the destructive row action as a button
    // labelled by its visible text, not by `aria-label` (`entityListV2` sets
    // `iconOnly: !!Icon && !isDangerous`), so match it by role and accessible
    // name. Edit stays on `getByLabel` because it is icon-only and keeps its
    // `aria-label`.
    this.deleteGroupButton = (rowName) =>
      this.groupsList
        .getByRole('row', {name: rowName})
        .getByRole('button', {name: 'Delete', exact: true});

    this.createGroupModal = page.getByRole('dialog', {
      name: 'Create group',
    });
    this.closeCreateGroupModal = this.createGroupModal.getByRole('button', {
      name: 'Close',
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
    this.closeEditGroupModal = this.editGroupModal.getByRole('button', {
      name: 'Close',
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
      name: 'Edit group',
    });

    this.deleteGroupModal = page.getByRole('dialog', {
      name: 'Delete group',
    });
    this.closeDeleteGroupModal = this.deleteGroupModal.getByRole('button', {
      name: 'Close',
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
    // On the new design system the assign-user field is a cmdk combobox. The
    // groups modal uses `UserMultiSelect`'s default placeholder, so the
    // accessible name is "Search by username" -- not the "Search by Username
    // or Name" the roles modal overrides it to.
    this.searchBox = this.assignUserModal.getByRole('combobox', {
      name: 'Search by name, email, or username',
    });
    // The results render in a Radix popover that portals as a *sibling* of the
    // dialog (DS #496), so the listbox is not a descendant of the modal --
    // scope it to the page.
    this.searchBoxResult = page.getByRole('listbox');
    this.assignUserButtonModal = this.assignUserModal.getByRole('button', {
      name: 'Assign user',
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
    await this.editGroupButton(currentName).click();
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
      await expect(this.deleteGroupButton(groupName)).toBeVisible({
        timeout: 20000,
      });
      await this.groupsHeading.click();
      await this.deleteGroupButton(groupName).click();
    }).toPass(defaultAssertionOptions);
    await expect(this.deleteGroupModal).toBeVisible();
    await this.deleteGroupModalDeleteButton.click();
    await expect(this.deleteGroupModal).toBeHidden();
  }

  async assertGroupExists(groupName: string) {
    await expect(this.selectGroupRow(groupName)).toBeVisible();
  }

  async clickGroupId(groupName: string) {
    await findLocatorInPaginatedList(this.page, this.groupCell(groupName));
    await this.selectGroupRow(groupName).click();
  }

  async assignUserToGroup(userName: string, userEmail: string) {
    await this.assignUserButton.click();
    await expect(this.assignUserModal).toBeVisible();
    await this.searchBox.fill(userName);
    // Keep matching on the email: this modal leaves `UserMultiSelect`'s
    // default `itemSubTitle`, so the option renders the username as its title
    // and the email beneath it, and the email is the unique half.
    const option = this.searchBoxResult
      .getByRole('option')
      .filter({hasText: userEmail})
      .first();
    await expect(option).toBeVisible({timeout: 30000});
    await option.click({timeout: 20000});
    await this.assignUserButtonModal.click();
    await expect(this.assignUserModal).toBeHidden();
    // Wait for the member row rather than sleeping for a fixed 8s: both
    // callers navigate away immediately afterwards, so the assignment has to
    // be observable before returning. The members table renders username,
    // name and email, and the email is the unique column.
    await waitForItemInList(
      this.page,
      this.assignedUsersList.getByRole('cell', {name: userEmail}),
      {timeout: 60000},
    );
  }
}
