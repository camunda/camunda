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
import {waitForItemInList} from 'utils/waitForItemInList';

export class IdentityAuthorizationsPage {
  readonly page: Page;
  readonly createAuthorizationButton: Locator;
  readonly authorizationsList: Locator;
  readonly createAuthorizationModal: Locator;
  readonly createAuthorizationOwnerComboBox: Locator;
  readonly createAuthorizationOwnerOption: (name: string) => Locator;
  readonly createAuthorizationResourceIdField: Locator;
  readonly createAuthorizationAccessPermission: (name: string) => Locator;
  readonly createAuthorizationOwnerTypeComboBox: Locator;
  readonly createAuthorizationOwnerTypeOption: (name: string) => Locator;
  readonly createAuthorizationSubmitButton: Locator;
  readonly deleteAuthorizationButton: (name: string) => Locator;
  readonly deleteAuthorizationModal: Locator;
  readonly deleteAuthorizationSubButton: Locator;
  readonly selectAuthorizationRow: (name: string) => Locator;
  readonly authorizationRowByOwnerId: (ownerId: string) => Locator;
  readonly selectResourceTypeTab: (resourceType: string) => Promise<void>;
  readonly resourceTypeComboBox: Locator;
  readonly getAuthorizationCell: (ownerId: string) => Locator;
  readonly resourceTypeOption: (resourceType: string) => Locator;
  readonly resourceTypeTab: (resourceType: string) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.authorizationsList = page.getByRole('table');

    this.selectAuthorizationRow = (name) =>
      this.authorizationsList.getByRole('row', {name: name});

    this.authorizationRowByOwnerId = (ownerId) =>
      this.authorizationsList.getByRole('row').filter({hasText: ownerId});

    this.createAuthorizationButton = page.getByRole('button', {
      name: 'Create authorization',
    });
    this.createAuthorizationModal = page.getByRole('dialog', {
      name: 'Create authorization',
    });
    this.createAuthorizationOwnerComboBox =
      this.createAuthorizationModal.getByPlaceholder('Select an owner');
    this.createAuthorizationOwnerOption = (name) =>
      this.createAuthorizationModal.getByRole('option', {
        name,
      });
    this.createAuthorizationResourceIdField =
      this.createAuthorizationModal.getByRole('textbox', {
        name: 'Resource ID',
      });
    this.createAuthorizationAccessPermission = (name) =>
      this.page.locator(`label[for="${name.toUpperCase()}"]`);
    this.createAuthorizationOwnerTypeComboBox =
      this.createAuthorizationModal.getByRole('combobox', {name: 'Owner type'});
    this.createAuthorizationOwnerTypeOption = (name) =>
      this.createAuthorizationModal.getByRole('option', {
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
    this.deleteAuthorizationSubButton = this.deleteAuthorizationModal.getByRole(
      'button',
      {
        name: 'Delete authorization',
      },
    );
    this.selectResourceTypeTab = (resourceType) =>
      this.resourceTypeTab(resourceType).click();
    this.resourceTypeComboBox = page.getByRole('combobox', {
      name: 'Resource type',
    });
    this.getAuthorizationCell = (ownerId) =>
      this.authorizationsList.getByRole('cell', {
        name: ownerId.toLowerCase().replace(/ /g, ''),
      });
    this.resourceTypeOption = (resourceType) =>
      this.page.getByRole('option', {
        name: new RegExp(`^${resourceType}$`, 'i'),
      });
    this.resourceTypeTab = (resourceType) =>
      this.page.getByRole('tab', {name: new RegExp(`^${resourceType}$`, 'i')});
  }

  async navigateToAuthorizations() {
    await this.page.goto(relativizePath(Paths.authorizations()));
  }

  async clickResourceType(resourceType: string) {
    await this.resourceTypeTab(resourceType).click();
  }

  async clickCreateAuthorizationButton() {
    await this.createAuthorizationButton.click();
  }

  async clickOwnerDropdown() {
    await this.createAuthorizationOwnerComboBox.click();
  }
  async selectOwnerFromDropdown(name: string) {
    await this.createAuthorizationOwnerOption(name).click();
  }

  async clickOwnerTypeDropdown() {
    await this.createAuthorizationOwnerTypeComboBox.click();
  }

  async selectOwnerTypeFromDrowdown(name: string) {
    await this.createAuthorizationOwnerTypeOption(name).click();
  }

  async fillResourceId(resourceId: string) {
    await expect(this.createAuthorizationResourceIdField).toBeVisible();
    await this.createAuthorizationResourceIdField.fill(resourceId);
    await expect(this.createAuthorizationResourceIdField).toHaveValue(
      resourceId,
    );
  }

  async checkAccessPermissions(permission: string[]) {
    for (let index = 0; index < permission.length; index++) {
      await this.createAuthorizationAccessPermission(permission[index]).click();
    }
  }

  async selectAccessPermissions(permissions: string[]) {
    for (const permission of permissions) {
      const checkboxLabel =
        this.createAuthorizationAccessPermission(permission);
      await checkboxLabel.waitFor({state: 'visible'});
      await checkboxLabel.click();
    }
  }

  async clickCreateAuthorizationSubmitButton() {
    await this.createAuthorizationSubmitButton.click();
    await expect(this.createAuthorizationModal).toBeHidden();
  }

  async clickDeleteAuthorizationButton(name: string) {
    await this.deleteAuthorizationButton(name).click();
  }

  async clickDeleteAuthorizationSubButton() {
    await this.deleteAuthorizationSubButton.click();
  }

  async createAuthorization(authorization: {
    ownerType: string;
    ownerId: string;
    resourceType: string;
    resourceId: string;
    accessPermissions: string[];
  }) {
    const maxRetries = 3;
    let lastError: Error | null = null;

    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        await this.createAuthorizationButton.click();
        await expect(this.createAuthorizationModal).toBeVisible({
          timeout: 15000,
        });
        await this.selectAuthorizationOwnerType({
          ownerType: authorization.ownerType,
        });
        await this.selectAuthorizationOwner({
          ownerId: authorization.ownerId,
        });
        await this.selectResourceType(authorization.resourceType);
        await this.fillResourceId(authorization.resourceId);
        await this.selectAccessPermissions(authorization.accessPermissions);
        await this.createAuthorizationSubmitButton.click({timeout: 15000});
        await expect(this.createAuthorizationModal).toBeHidden({
          timeout: 15000,
        });

        await this.selectResourceTypeTab(authorization.resourceType);
        const item = this.getAuthorizationCell(authorization.ownerId);
        await waitForItemInList(this.page, item, {
          timeout: 30000,
          clickNext: true,
          onAfterReload: () =>
            this.selectResourceTypeTab(authorization.resourceType),
        });
        return;
      } catch (error) {
        lastError = error as Error;
        if (attempt < maxRetries) {
          await sleep(60000);
          await this.page.reload();
        }
      }
    }

    throw new Error(
      `Failed to create authorization for ${authorization.ownerId} after ${maxRetries} attempts. Last error: ${lastError?.message}`,
    );
  }

  async selectResourceType(resourceType: string) {
    await this.resourceTypeComboBox.click({timeout: 90000});
    await this.resourceTypeOption(resourceType).click();
  }

  async selectAuthorizationOwnerType(authorization: {ownerType: string}) {
    await this.createAuthorizationOwnerTypeComboBox.click();
    await this.createAuthorizationOwnerTypeOption(
      authorization.ownerType,
    ).click();
  }

  async selectAuthorizationOwner(authorization: {ownerId: string}) {
    await this.createAuthorizationOwnerComboBox.click();
    try {
      await this.createAuthorizationOwnerOption(authorization.ownerId).click({
        timeout: 20000,
      });
    } catch (error) {
      console.log('Error while selecting owner' + error);
      await this.createAuthorizationOwnerComboBox.fill(authorization.ownerId);
    }
  }

  async findAuthorizationInPaginatedList(
    ownerId: string,
    resourceType?: string,
  ): Promise<boolean> {
    const authorizationRow = this.authorizationRowByOwnerId(ownerId);

    try {
      await waitForItemInList(this.page, authorizationRow, {
        clickNext: true,
        timeout: 30000,
        onAfterReload: async () => {
          if (resourceType) {
            await this.selectResourceTypeTab(resourceType);
          }
        },
      });
      return true;
    } catch {
      return false;
    }
  }

  async assertAuthorizationExists(
    ownerId: string,
    ownerType: string,
    accessPermissions?: string[],
    authorizationTab?: string,
  ) {
    const exists = await this.findAuthorizationInPaginatedList(
      ownerId,
      authorizationTab,
    );

    if (!exists) {
      throw new Error(
        `Authorization for owner ${ownerId} not found in paginated list`,
      );
    }

    const authorizationRow = this.authorizationRowByOwnerId(ownerId);

    await expect(authorizationRow).toBeVisible();
    await expect(authorizationRow).toContainText(ownerType.toUpperCase());

    if (accessPermissions && accessPermissions.length > 0) {
      for (const permission of accessPermissions) {
        await expect(authorizationRow).toContainText(permission.toUpperCase());
      }
    }
  }
}
