/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Locator, type Page} from '@playwright/test';

// The "Create new report/dashboard" dialog with process selection and templates.
export class TemplateDialog {
  readonly dialog: Locator;

  constructor(
    private readonly page: Page,
    title: 'Create new report' | 'Create new dashboard'
  ) {
    this.dialog = page.getByRole('dialog', {name: title});
  }

  async selectProcess(name: string): Promise<void> {
    await this.dialog.getByRole('combobox', {name: /Select one or more processes/}).click();
    await this.page.getByRole('option', {name}).click();
    await this.dialog.getByRole('heading').click();
    // Templates only receive the definition once its diagram has loaded.
    await expect(this.dialog.locator('.djs-container')).toBeVisible();
  }

  async selectTemplate(name: string): Promise<void> {
    await this.dialog.getByRole('button', {name: new RegExp(`^${name}`)}).click();
  }

  async confirm(): Promise<void> {
    await this.dialog.getByRole('link', {name: /^Create (report|dashboard)$/}).click();
  }
}
