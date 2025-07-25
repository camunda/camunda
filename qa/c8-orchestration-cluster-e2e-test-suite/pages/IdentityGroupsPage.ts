/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';
import {sleep} from 'utils/sleep';

export class IdentityGroupsPage {
  private page: Page;
  readonly groupsList: Locator;
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
  readonly emptyState: Locator;
  readonly assignUserButton: Locator;
  readonly searchBox: Locator;
  readonly searchBoxResult: Locator;
  readonly assignUserButtonModal: Locator;
  readonly selectGroupRow: (name: string) => Locator;
  readonly groupCell: (name: string) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.groupsList = page.getByRole('table');

    this.selectGroupRow = (name) =>
      this.groupsList.getByRole('row', {name: name});

    this.groupCell = (name) =>
      this.groupsList.getByRole('cell', {name, exact: true});

    this.createGroupButton = page.getByRole('button', {
      name: /Create( a)? group/,
    });

    this.editGroupButton = (rowName) =>
      this.groupsList.getByRole('row', {name: rowName}).getByLabel('Edit');
    this.deleteGroupButton = (rowName) =>
      this.groupsList.getByRole('row', {name: rowName}).getByLabel('Delete');

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
    this.emptyState = page.getByText('No groups created yet');
    this.assignUserButton = page.getByRole('button', {name: 'Assign user'});
    this.searchBox = page.getByRole('searchbox');
    this.searchBoxResult = page.getByRole('listitem');
    this.assignUserButtonModal = page
      .getByLabel('Assign user')
      .getByRole('button', {name: 'Assign user'});
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
    await this.deleteGroupButton(groupName).click();
    await expect(this.deleteGroupModal).toBeVisible();
    await this.deleteGroupModalDeleteButton.click();
    await expect(this.deleteGroupModal).toBeHidden();
  }

  async assertGroupExists(groupName: string) {
    await expect(this.selectGroupRow(groupName)).toBeVisible();
  }

  async clickGroupId(groupName: string) {
    await this.selectGroupRow(groupName).click();
  }

  async assignUserToGroup(userName: string, userEmail: string) {
    await this.assignUserButton.click();
    await this.searchBox.fill(userName);
    await this.searchBoxResult
      .filter({
        hasText: userEmail,
      })
      .click();
    await this.assignUserButtonModal.click();
    await sleep(8000);
  }
}
