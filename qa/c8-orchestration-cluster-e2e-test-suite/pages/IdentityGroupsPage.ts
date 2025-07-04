/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';

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

  constructor(page: Page) {
    this.page = page;
    this.groupsList = page.getByRole('table');
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
  }

  async navigateToGroups() {
    await this.page.goto(relativizePath(Paths.groups()));
  }
}
