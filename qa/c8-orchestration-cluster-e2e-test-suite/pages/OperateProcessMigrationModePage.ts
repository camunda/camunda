/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

export class OperateProcessMigrationModePage {
  private page: Page;
  readonly targetVersionCombo: Locator;
  readonly nextButton: Locator;
  readonly confirmButton: Locator;
  readonly migrationConfirmationInput: Locator;
  readonly migrationConfirmationButton: Locator;

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
  }

  async clickTargetVersionCombo(): Promise<void> {
    await this.targetVersionCombo.click();
  }

  async selectTargetVersion(version: string): Promise<void> {
    await this.page.getByRole('option', {name: version, exact: true}).click();
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
}
