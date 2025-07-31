/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';

export class IdentityMappingRulesPage {
  private page: Page;
  readonly mappingRulesList: Locator;
  readonly createMappingRuleButton: Locator;
  readonly editMappingRuleButton: (rowName?: string) => Locator;
  readonly deleteMappingRuleButton: (rowName?: string) => Locator;
  readonly createMappingRuleModal: Locator;
  readonly closeCreateMappingRuleModal: Locator;
  readonly createMappingRuleIdField: Locator;
  readonly createMappingRuleNameField: Locator;
  readonly createMappingRuleClaimNameField: Locator;
  readonly createMappingRuleClaimValueField: Locator;
  readonly createMappingRuleModalCancelButton: Locator;
  readonly createMappingRuleModalCreateButton: Locator;
  readonly editMappingRuleModal: Locator;
  readonly closeEditMappingRuleModal: Locator;
  readonly editMappingRuleIdField: Locator;
  readonly editMappingRuleNameField: Locator;
  readonly editMappingRuleClaimNameField: Locator;
  readonly editMappingRuleClaimValueField: Locator;
  readonly editMappingRuleModalCancelButton: Locator;
  readonly editMappingRuleModalUpdateButton: Locator;
  readonly deleteMappingRuleModal: Locator;
  readonly closeDeleteMappingRuleModal: Locator;
  readonly deleteMappingRuleModalCancelButton: Locator;
  readonly deleteMappingRuleModalDeleteButton: Locator;
  readonly emptyState: Locator;
  readonly usersNavItem: Locator;

  constructor(page: Page) {
    this.page = page;
    this.mappingRulesList = page.getByRole('table');
    this.createMappingRuleButton = page.getByRole('button', {
      name: 'Create a mapping rule',
    });
    this.editMappingRuleButton = (rowName) =>
      this.mappingRulesList.getByRole('row', {name: rowName}).getByLabel('Edit');
    this.deleteMappingRuleButton = (rowName) =>
      this.mappingRulesList.getByRole('row', {name: rowName}).getByLabel('Delete');

    this.createMappingRuleModal = page.getByRole('dialog', {
      name: 'Create new mapping rule',
    });
    this.closeCreateMappingRuleModal = this.createMappingRuleModal.getByRole('button', {
      name: 'Close',
    });
    this.createMappingRuleIdField = this.createMappingRuleModal.getByRole('textbox', {
      name: 'Mapping rule ID',
    });
    this.createMappingRuleNameField = this.createMappingRuleModal.getByRole('textbox', {
      name: 'Mapping rule name',
    });
    this.createMappingRuleClaimNameField = this.createMappingRuleModal.getByRole(
      'textbox',
      {name: 'Claim name'},
    );
    this.createMappingRuleClaimValueField = this.createMappingRuleModal.getByRole(
      'textbox',
      {name: 'Claim value'},
    );
    this.createMappingRuleModalCancelButton = this.createMappingRuleModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.createMappingRuleModalCreateButton = this.createMappingRuleModal.getByRole(
      'button',
      {name: 'Create a mapping rule'},
    );

    this.editMappingRuleModal = page.getByRole('dialog', {
      name: 'Edit mapping rule',
    });
    this.closeEditMappingRuleModal = this.editMappingRuleModal.getByRole('button', {
      name: 'Close',
    });
    this.editMappingRuleIdField = this.editMappingRuleModal.getByRole('textbox', {
      name: 'Mapping rule ID',
    });
    this.editMappingRuleNameField = this.editMappingRuleModal.getByRole('textbox', {
      name: 'Mapping rule name',
    });
    this.editMappingRuleClaimNameField = this.editMappingRuleModal.getByRole(
      'textbox',
      {name: 'Claim name'},
    );
    this.editMappingRuleClaimValueField = this.editMappingRuleModal.getByRole(
      'textbox',
      {name: 'Claim value'},
    );
    this.editMappingRuleModalCancelButton = this.editMappingRuleModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.editMappingRuleModalUpdateButton = this.editMappingRuleModal.getByRole(
      'button',
      {name: 'Update mapping rule'},
    );

    this.deleteMappingRuleModal = page.getByRole('dialog', {
      name: 'Delete mapping rule',
    });
    this.closeDeleteMappingRuleModal = this.deleteMappingRuleModal.getByRole('button', {
      name: 'Close',
    });
    this.deleteMappingRuleModalCancelButton = this.deleteMappingRuleModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.deleteMappingRuleModalDeleteButton = this.deleteMappingRuleModal.getByRole(
      'button',
      {name: 'Delete mapping rule'},
    );

    this.emptyState = page.getByText("You don't have any mapping rules yet");
    this.usersNavItem = page.getByText('Users');
  }

  async navigateToMappingRules() {
    await this.page.goto(relativizePath(Paths.mappingRules()));
  }
}
