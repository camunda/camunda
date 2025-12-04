/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

export class OperateProcessMigrationModePage {
  private page: Page;
  readonly targetVersionCombo: Locator;
  readonly nextButton: Locator;
  readonly confirmButton: Locator;
  readonly migrationConfirmationInput: Locator;
  readonly migrationConfirmationButton: Locator;
  readonly targetProcessCombobox: Locator;
  readonly targetVersionDropdown: Locator;
  readonly modificationConfirmationInput: Locator;
  readonly summaryNotification: Locator;

  constructor(page: Page) {
    this.page = page;
    this.targetVersionCombo = page.getByRole('combobox', {
      name: 'Target Version',
    });
    this.nextButton = this.page.getByRole('button', {name: 'Next'});
    this.confirmButton = this.page.getByRole('button', {name: 'Confirm'});
    this.migrationConfirmationInput = this.page
      .getByRole('dialog')
      .locator('input');
    this.migrationConfirmationButton = page
      .getByLabel('Migration confirmation')
      .getByRole('button', {name: 'Confirm'});
    this.targetProcessCombobox = page.getByRole('combobox', {
      name: 'Target',
      exact: true,
    });
    this.targetVersionDropdown = page.getByRole('combobox', {
      name: 'Target Version',
    });
    this.modificationConfirmationInput = page.locator(
      '#modification-confirmation',
    );
    this.summaryNotification = page.getByRole('main').getByRole('status');
  }

  async clickTargetVersionCombo(): Promise<void> {
    await this.targetVersionCombo.click();
  }

  getOptionByName(name: string): Locator {
    return this.page.getByRole('option', {name, exact: true});
  }

  async selectTargetVersion(version: string): Promise<void> {
    await this.getOptionByName(version).click();
  }

  async clickNextButton(): Promise<void> {
    await this.nextButton.click();
  }

  async clickConfirmButton(): Promise<void> {
    await this.confirmButton.click();
  }

  async fillMigrationConfirmation(text: string): Promise<void> {
    await this.migrationConfirmationInput.fill(text);
  }

  async clickMigrationConfirmationButton(): Promise<void> {
    await this.migrationConfirmationButton.click();
  }

  async migrateProcessToVersion(version: string): Promise<void> {
    await this.clickTargetVersionCombo();
    await this.selectTargetVersion(version);
    await this.clickNextButton();
    await this.clickConfirmButton();
    await this.fillMigrationConfirmation('MIGRATE');
    await this.clickMigrationConfirmationButton();
  }

  async completeProcessInstanceMigration(): Promise<void> {
    await this.clickNextButton();
    await this.clickConfirmButton();
    await this.fillMigrationConfirmation('MIGRATE');
    await this.clickMigrationConfirmationButton();
  }

  async mapFlowNode(
    sourceFlowNodeName: string,
    targetFlowNodeName: string,
  ): Promise<void> {
    await this.page
      .getByLabel(`Target element for ${sourceFlowNodeName}`, {exact: true})
      .selectOption(targetFlowNodeName);
  }

  async verifyFlowNodeMappings(
    mappings: Array<{label: string | RegExp; targetValue: string}>,
  ): Promise<void> {
    for (const {label, targetValue} of mappings) {
      await expect(this.page.getByLabel(label)).toHaveValue(targetValue);
    }
  }
}
