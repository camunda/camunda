/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';

export class IdentityAuthorizationsPage {
  readonly page: Page;
  readonly createAuthorizationButton: Locator;
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
    this.authorizationsList = page.getByRole('table');
    this.createAuthorizationButton = page.getByRole('button', {
      name: 'Create authorization',
    });
    this.createAuthorizationModal = page.getByRole('dialog', {
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
}
