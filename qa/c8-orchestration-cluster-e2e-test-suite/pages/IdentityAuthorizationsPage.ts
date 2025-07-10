/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';

export class IdentityAuthorizationsPage {
  readonly page: Page;
  readonly authorizationTab: Locator;
  readonly createAuthorizationButton: Locator;
  readonly createAuthorizationButtonInDialog: Locator;
  readonly authorizationsList: Locator;
  readonly createAuthorizationModal: Locator;
  readonly createAuthorizationOwnerComboBox: Locator;
  readonly createAuthorizationOwnerOption: (name: string) => Locator;
  readonly createAuthorizationResourceIdField: Locator;
  readonly createAuthorizationAccessPermission: (name: string) => Locator;
  readonly createAuthorizationSubmitButton: Locator;
  readonly deleteAuthorizationButton: (name: string) => Locator;
  readonly deleteAuthorizationModal: Locator;
  readonly deleteAuthorizationModalDeleteButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.authorizationTab = page
      .getByRole('listitem')
      .filter({hasText: 'Authorizations'});
    this.authorizationsList = page.getByRole('table');
    this.createAuthorizationModal = page.getByRole('dialog', {
      name: 'Create authorization',
    });
    this.createAuthorizationButton = page.getByRole('button', {
      name: 'Create authorization',
    });
    this.createAuthorizationButtonInDialog =
      this.createAuthorizationModal.getByRole('button', {
        name: 'Create authorization',
      });
    this.createAuthorizationOwnerComboBox =
      this.createAuthorizationModal.getByRole('combobox', {
        name: 'Owner',
        exact: true,
      });
    this.createAuthorizationOwnerOption = (name) =>
      this.createAuthorizationModal.getByRole('option', {
        name,
      });
    this.createAuthorizationResourceIdField =
      this.createAuthorizationModal.getByRole('textbox', {
        name: 'Resource ID',
      });
    this.createAuthorizationAccessPermission = (name) =>
      this.createAuthorizationModal.getByRole('checkbox', {
        name,
      });
    this.createAuthorizationSubmitButton =
      this.createAuthorizationModal.getByRole('button', {
        name: 'Create authorization',
      });
    this.deleteAuthorizationButton = (name) =>
      this.authorizationsList.getByRole('row', {name}).getByLabel('Delete');
    this.deleteAuthorizationModal = page.getByRole('dialog', {
      name: 'Delete authorization',
    });
    this.deleteAuthorizationModalDeleteButton =
      this.deleteAuthorizationModal.getByRole('button', {
        name: 'Delete authorization',
      });
  }

  async navigateToAuthorizations() {
    await this.page.goto(relativizePath(Paths.authorizations()));
  }

  async clickAuthorizationsTab(): Promise<void> {
    await expect(this.authorizationTab).toBeVisible({timeout: 60000});
    await this.authorizationTab.click({timeout: 60000});
  }

  async clickAuthorizationButton(): Promise<void> {
    await expect(this.createAuthorizationButton).toBeVisible({timeout: 60000});
    await this.createAuthorizationButton.click({timeout: 60000});
  }

  async clickAuthorizationButtonInDialog(): Promise<void> {
    await expect(this.createAuthorizationButtonInDialog).toBeVisible({
      timeout: 60000,
    });
    await this.createAuthorizationButtonInDialog.click({timeout: 60000});
  }

  async assertAuthorizationModalPresent(): Promise<void> {
    await expect(this.createAuthorizationModal).toBeVisible({timeout: 60000});
  }

  async clickAuthorizationOwnerComboBox(): Promise<void> {
    await expect(this.createAuthorizationOwnerComboBox).toBeVisible({
      timeout: 60000,
    });
    await this.createAuthorizationOwnerComboBox.click({timeout: 60000});
  }

  async selectOwnerComboBox(owner: string): Promise<void> {
    await this.createAuthorizationOwnerOption(owner).click({timeout: 30000});
  }

  async clickAuthorizationResourceIdField(): Promise<void> {
    await expect(this.createAuthorizationResourceIdField).toBeVisible({
      timeout: 60000,
    });
    await this.createAuthorizationResourceIdField.click({timeout: 60000});
  }

  async fillAuthorizationResourceIdField(resourceId: string): Promise<void> {
    await this.createAuthorizationResourceIdField.fill(resourceId, {
      timeout: 60000,
    });
  }

  async clickAuthorizationAccessPermissionCheckbox(
    permission: string,
  ): Promise<void> {
    await expect(
      this.createAuthorizationAccessPermission(permission),
    ).toBeVisible({timeout: 60000});
    await this.createAuthorizationAccessPermission(permission).click({
      force: true,
    });
  }

  async clickCreateAuthorizationButton(): Promise<void> {
    await expect(this.createAuthorizationButton).toBeVisible({timeout: 60000});
    await this.createAuthorizationButton.click({timeout: 60000});
  }

  async clickDeleteAuthorizationButton(name: string): Promise<void> {
    await expect(this.deleteAuthorizationButton(name)).toBeVisible({
      timeout: 60000,
    });
    await this.deleteAuthorizationButton(name).click({timeout: 60000});
  }
}
