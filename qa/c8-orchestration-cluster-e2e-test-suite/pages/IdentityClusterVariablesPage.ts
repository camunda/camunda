/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, Locator, Page} from '@playwright/test';
import {Paths, relativizePath} from 'utils/relativizePath';
import {defaultAssertionOptions} from '../utils/constants';

export class IdentityClusterVariablesPage {
  private page: Page;
  readonly clusterVariablesList: Locator;
  readonly createClusterVariableButton: Locator;
  readonly detailsClusterVariableButton: (rowName?: string) => Locator;
  readonly deleteClusterVariableButton: (rowName?: string) => Locator;
  readonly createClusterVariableModal: Locator;
  readonly closeCreateClusterVariableModal: Locator;
  readonly clusterVariableNameField: Locator;
  readonly clusterVariableScopeField: Locator;
  readonly clusterVariableTenentIdField: Locator;
  readonly clusterVariableValueField: Locator;
  readonly createClusterVariableModalCancelButton: Locator;
  readonly createClusterVariableModalButton: Locator;
  readonly deleteClusterVariableModal: Locator;
  readonly closeDeleteClusterVariableModal: Locator;
  readonly deleteClusterVariableModalCancelButton: Locator;
  readonly deleteClusterVariableModalDeleteButton: Locator;
  readonly detailsClusterVariableModal: Locator;
  readonly closeDetailsClusterVariableModal: Locator;
  readonly detailsClusterVariableModalCopyButton: Locator;
  readonly clusterVariableCell: (clusterVariableName: string) => Locator;
  readonly clusterVariableRow: (clusterVariableName: string) => Locator;
  readonly clusterVariablesHeading: Locator;

  constructor(page: Page) {
    this.page = page;

    this.clusterVariablesList = page.getByRole('table');
    this.createClusterVariableButton = page.getByRole('button', {
      name: 'Create cluster variable',
    });
    this.detailsClusterVariableButton = (rowName) =>
        this.clusterVariablesList
            .getByRole('row', {name: rowName})
            .getByLabel('View', {exact: true});
    this.deleteClusterVariableButton = (rowName) =>
        this.clusterVariablesList
            .getByRole('row', {name: rowName})
            .getByLabel('Delete', {exact: true});

    this.createClusterVariableModal = page.getByRole('dialog', {
      name: 'Create cluster variable',
    });
    this.closeCreateClusterVariableModal = this.createClusterVariableModal.getByRole('button', {
      name: 'Close',
    });
    this.clusterVariableNameField = this.createClusterVariableModal.getByRole('textbox', {
      name: 'Name',
    });
    this.clusterVariableScopeField = this.createClusterVariableModal.getByRole('radio', {
      name: 'Scope',
    });
    this.clusterVariableTenentIdField = this.createClusterVariableModal.getByRole('combobox', {
      name: 'Tenant ID',
    });
    this.clusterVariableValueField = this.createClusterVariableModal.getByRole('textbox', {
      name: 'Value',
    });
    this.createClusterVariableModalCancelButton = this.createClusterVariableModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.createClusterVariableModalButton = this.createClusterVariableModal.getByRole('button', {
      name: 'Create cluster variable',
    });

    this.detailsClusterVariableModal = page.getByRole('dialog', {
      name: 'Cluster Variable',
    });
    this.closeDetailsClusterVariableModal = this.detailsClusterVariableModal.getByRole('button', {
      name: 'Close',
    });

    this.deleteClusterVariableModal = page.getByRole('dialog', {
      name: 'Delete cluster variable',
    });
    this.closeDeleteClusterVariableModal = this.deleteClusterVariableModal.getByRole('button', {
      name: 'Close',
    });
    this.deleteClusterVariableModalCancelButton = this.deleteClusterVariableModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.deleteClusterVariableModalDeleteButton = this.deleteClusterVariableModal.getByRole(
      'button',
      {name: 'Delete cluster variable'},
    );

    this.clusterVariableCell = (clusterVariableName) =>
      this.clusterVariablesList.getByRole('cell', {name: clusterVariableName});
    this.clusterVariableRow = (clusterVariableName) =>
      this.clusterVariablesList.getByRole('row', {name: clusterVariableName});
    this.clusterVariablesHeading = this.page.getByRole('heading', {name: 'Cluster Variables'});
  }

  async navigateToClusterVariables() {
    await this.page.goto(relativizePath(Paths.clusterVariables()));
  }

  async fillClusterVariableName(name: string) {
    await this.clusterVariableNameField.fill(name);
  }

  async fillClusterVariableScope(scope: string) {
    await this.clusterVariableScopeField.fill(scope);
  }

  async fillClusterVariableTenantId(tenantId: string) {
    await this.clusterVariableTenentIdField.fill(tenantId);
  }

  async fillClusterVariableValue(value: string) {
    await this.clusterVariableValueField.fill(value);
  }

  async detailsClusterVariable(clusterVariableName: string) {
    await expect(async () => {
      await expect(this.detailsClusterVariableButton(clusterVariableName)).toBeVisible({
        timeout: 20000,
      });
      await this.clusterVariablesHeading.click();
      await this.detailsClusterVariableButton(clusterVariableName).click();
    }).toPass(defaultAssertionOptions);

    await expect(this.detailsClusterVariableModal).toBeVisible();
    await this.closeDetailsClusterVariableModal.click();
    await expect(this.detailsClusterVariableModal).toBeHidden();
  }

  async deleteClusterVariable(clusterVariableName: string) {
    await expect(async () => {
      await expect(this.deleteClusterVariableButton(clusterVariableName)).toBeVisible({
        timeout: 20000,
      });
      await this.clusterVariablesHeading.click();
      await this.deleteClusterVariableButton(clusterVariableName).click();
    }).toPass(defaultAssertionOptions);

    await expect(this.deleteClusterVariableModal).toBeVisible();
    await this.deleteClusterVariableModalDeleteButton.click();
    await expect(this.deleteClusterVariableModal).toBeHidden();
  }

  async createClusterVariable(clusterVariable: {
    name: string;
    scope: string;
    tenantId?: string;
    value: string;
  }) {
    await this.createClusterVariableButton.click();
    await expect(this.createClusterVariableModal).toBeVisible();
    await this.fillClusterVariableName(clusterVariable.name);
    await this.fillClusterVariableScope(clusterVariable.scope);
    if (clusterVariable.tenantId) {
      await this.fillClusterVariableTenantId(clusterVariable.tenantId);
    }
    await this.fillClusterVariableValue(clusterVariable.value);
    await this.createClusterVariableModalButton.click();
    await expect(this.createClusterVariableModal).toBeHidden();
  }
}
