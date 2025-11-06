/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

class OperateProcessesMigrationPage {
  private page: Page;
  readonly nextButton: Locator;
  readonly confirmButton: Locator;
  readonly modificationConfirmationInput: Locator;
  readonly confirmSubButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.nextButton = page.getByRole('button', {name: 'next'});
    this.confirmButton = page.getByRole('button', {name: 'confirm'});
    this.modificationConfirmationInput = page.locator(
      '#modification-confirmation',
    );
    this.confirmSubButton = page
      .getByRole('dialog', {name: 'Migration confirmation'})
      .getByRole('button', {name: 'Confirm'});
  }

  async migrateProcessInstance(): Promise<void> {
    await this.nextButton.click();
    await this.confirmButton.click();
    await this.modificationConfirmationInput.fill('MIGRATE');
    await this.confirmSubButton.click();
    await expect(this.page).toHaveURL(/.*migrate.*/);
  }
}

export {OperateProcessesMigrationPage};
