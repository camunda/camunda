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

export class IdentityMappingRulesPage {
  private page: Page;
  readonly mappingRulesList: Locator;
  readonly createMappingRuleButton: Locator;
  readonly rowActionsButton: (rowName: string) => Locator;
  readonly editMappingRuleMenuItem: Locator;
  readonly deleteMappingRuleMenuItem: Locator;
  readonly createMappingRuleModal: Locator;
  readonly createMappingRuleIdField: Locator;
  readonly createMappingRuleNameField: Locator;
  readonly createMappingRuleClaimNameField: Locator;
  readonly createMappingRuleClaimValueField: Locator;
  readonly createMappingRuleModalCancelButton: Locator;
  readonly createMappingRuleModalSaveButton: Locator;
  readonly editMappingRuleModal: Locator;
  readonly editMappingRuleNameField: Locator;
  readonly editMappingRuleClaimNameField: Locator;
  readonly editMappingRuleClaimValueField: Locator;
  readonly editMappingRuleModalCancelButton: Locator;
  readonly editMappingRuleModalSaveButton: Locator;
  readonly deleteMappingRuleModal: Locator;
  readonly deleteMappingRuleModalCancelButton: Locator;
  readonly deleteMappingRuleModalDeleteButton: Locator;
  readonly emptyStateLocator: Locator;
  readonly usersNavItem: Locator;
  readonly rolesNavItem: Locator;
  readonly mappingRulesNavItem: Locator;
  readonly mappingRuleCell: (name: string) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.mappingRulesList = page.getByRole('table');

    this.mappingRuleCell = (name) =>
      this.mappingRulesList.getByRole('cell', {name, exact: true});

    this.createMappingRuleButton = page.getByRole('button', {
      name: /^(Add|Create) mapping rule$/,
    });
    this.rowActionsButton = (rowName) =>
      this.mappingRulesList
        .getByRole('row')
        .filter({hasText: rowName})
        .getByRole('button', {name: 'Row actions'});
    this.editMappingRuleMenuItem = page.getByRole('menuitem', {
      name: 'Edit',
      exact: true,
    });
    this.deleteMappingRuleMenuItem = page.getByRole('menuitem', {
      name: 'Delete',
      exact: true,
    });

    this.createMappingRuleModal = page.getByRole('dialog', {
      name: 'Add mapping rule',
      exact: true,
    });
    this.createMappingRuleIdField = this.createMappingRuleModal.getByRole(
      'textbox',
      {name: 'Mapping rule ID'},
    );
    this.createMappingRuleNameField = this.createMappingRuleModal.getByRole(
      'textbox',
      {name: 'Name', exact: true},
    );
    this.createMappingRuleClaimNameField =
      this.createMappingRuleModal.getByRole('textbox', {name: 'Claim name'});
    this.createMappingRuleClaimValueField =
      this.createMappingRuleModal.getByRole('textbox', {name: 'Claim value'});
    this.createMappingRuleModalCancelButton = this.createMappingRuleModal
      .getByRole('button', {name: 'Cancel'})
      .filter({hasText: 'Cancel'});
    this.createMappingRuleModalSaveButton =
      this.createMappingRuleModal.getByRole('button', {name: 'Save'});

    this.editMappingRuleModal = page.getByRole('dialog', {
      name: 'Edit mapping rule',
      exact: true,
    });
    this.editMappingRuleNameField = this.editMappingRuleModal.getByRole(
      'textbox',
      {name: 'Name', exact: true},
    );
    this.editMappingRuleClaimNameField = this.editMappingRuleModal.getByRole(
      'textbox',
      {name: 'Claim name'},
    );
    this.editMappingRuleClaimValueField = this.editMappingRuleModal.getByRole(
      'textbox',
      {name: 'Claim value'},
    );
    this.editMappingRuleModalCancelButton = this.editMappingRuleModal
      .getByRole('button', {name: 'Cancel'})
      .filter({hasText: 'Cancel'});
    this.editMappingRuleModalSaveButton = this.editMappingRuleModal.getByRole(
      'button',
      {name: 'Save'},
    );

    this.deleteMappingRuleModal = page.getByRole('alertdialog', {
      name: 'Delete mapping rule',
      exact: true,
    });
    this.deleteMappingRuleModalCancelButton =
      this.deleteMappingRuleModal.getByRole('button', {name: 'Cancel'});
    this.deleteMappingRuleModalDeleteButton =
      this.deleteMappingRuleModal.getByRole('button', {
        name: 'Delete',
        exact: true,
      });

    this.emptyStateLocator = page.getByText(
      /^No mapping rules (found\.|created yet)$/,
    );
    this.usersNavItem = page.getByRole('link', {name: 'Users', exact: true});
    this.rolesNavItem = page.getByRole('link', {name: 'Roles', exact: true});
    this.mappingRulesNavItem = page.getByRole('link', {
      name: 'Mapping rules',
      exact: true,
    });
  }

  async navigateToMappingRules() {
    await this.page.goto(relativizePath(Paths.mappingRules()));
  }

  async createMappingRule(
    mappingRuleId: string,
    mappingRuleName: string,
    claimName: string,
    claimValue: string,
  ) {
    await this.createMappingRuleButton.click();
    await expect(this.createMappingRuleModal).toBeVisible();
    await this.createMappingRuleIdField.fill(mappingRuleId);
    await this.createMappingRuleNameField.fill(mappingRuleName);
    await this.createMappingRuleClaimNameField.fill(claimName);
    await this.createMappingRuleClaimValueField.fill(claimValue);

    await this.createMappingRuleModalSaveButton.click();
    await expect(this.createMappingRuleModal).toBeHidden();
  }

  async editMappingRule(
    currentName: string,
    newName: string,
    newClaimName?: string,
    newClaimValue?: string,
  ) {
    await this.rowActionsButton(currentName).click();
    await this.editMappingRuleMenuItem.click();
    await expect(this.editMappingRuleModal).toBeVisible();
    await this.editMappingRuleNameField.fill(newName);
    if (newClaimName) {
      await this.editMappingRuleClaimNameField.fill(newClaimName);
    }
    if (newClaimValue) {
      await this.editMappingRuleClaimValueField.fill(newClaimValue);
    }
    await this.editMappingRuleModalSaveButton.click();
    await expect(this.editMappingRuleModal).toBeHidden();
  }

  async deleteMappingRule(mappingRuleName: string) {
    await expect(this.rowActionsButton(mappingRuleName)).toBeVisible({
      timeout: 20000,
    });
    await expect(async () => {
      await this.rowActionsButton(mappingRuleName).click();
      await this.deleteMappingRuleMenuItem.click({timeout: 5000});
    }).toPass(defaultAssertionOptions);
    await expect(this.deleteMappingRuleModal).toBeVisible();
    await this.deleteMappingRuleModalDeleteButton.click();
    await expect(this.deleteMappingRuleModal).toBeHidden();
  }
}
