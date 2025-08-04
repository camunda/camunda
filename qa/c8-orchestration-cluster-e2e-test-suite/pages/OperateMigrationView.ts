/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

export class OperateMigrationView {
  private page: Page;

  readonly targetProcessComboBox: Locator;
  readonly targetVersionDropdown: Locator;
  readonly nextButton: Locator;
  readonly confirmButton: Locator;
  readonly continueButton: Locator;
  readonly summaryNotification: Locator;
  readonly migrationConfirmationModal: Locator;
  readonly operationsList: Locator;

  constructor(page: Page) {
    this.page = page;
    this.migrationConfirmationModal = page.getByRole('dialog', {
      name: /migration confirmation/i,
    });

    this.targetProcessComboBox = page.getByRole('combobox', {
      name: 'Target',
      exact: true,
    });

    this.targetVersionDropdown = page.getByRole('combobox', {
      name: 'Target Version',
    });

    this.nextButton = page.getByRole('button', {
      name: /^next$/i,
    });

    this.confirmButton = page.getByRole('button', {
      name: /^confirm$/i,
    });

    this.continueButton = page.getByRole('button', {
      name: /^continue$/i,
    });

    this.summaryNotification = page.getByRole('main').getByRole('status');
    this.operationsList = page.getByTestId('operations-list');
  }

  async selectTargetProcess(option: string) {
    await this.targetProcessComboBox.click();
    await this.page.getByRole('option', {name: option, exact: true}).click();
  }

  async selectTargetVersion(option: string) {
    await this.targetVersionDropdown.click();
    await this.page.getByRole('option', {name: option, exact: true}).click();
  }

  mapFlowNode({
    sourceFlowNodeName,
    targetFlowNodeName,
  }: {
    sourceFlowNodeName: string;
    targetFlowNodeName: string;
  }) {
    return this.page
      .getByLabel(`Target flow node for ${sourceFlowNodeName}`, {exact: true})
      .selectOption(targetFlowNodeName);
  }

  confirmMigration() {
    this.migrationConfirmationModal.getByRole('textbox').fill('MIGRATE');
    return this.migrationConfirmationModal
      .getByRole('button', {name: /confirm/i})
      .click();
  }

  selectTargetSourceFlowNode(flowNodeName: string) {
    return this.page
      .getByRole('cell', {name: flowNodeName, exact: true})
      .click();
  }
}
