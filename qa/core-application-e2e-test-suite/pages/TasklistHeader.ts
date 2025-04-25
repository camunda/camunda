/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

class TasklistHeader {
  private page: Page;
  readonly openSettingsButton: Locator;
  readonly languageSelector: Locator;
  readonly processesTab: Locator;
  readonly logoutButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.openSettingsButton = page.getByRole('button', {name: 'Open Settings'});
    this.languageSelector = page.getByRole('combobox', {name: 'Language'});
    this.processesTab = page.getByRole('link', {name: 'Processes'});
    this.logoutButton = page.getByRole('button', {name: 'Log out'});
  }

  async logout() {
    await this.openSettingsButton.click();
    await this.logoutButton.click();
  }

  async changeLanguage(option: 'Français' | 'English' | 'Deutsch' | 'Español') {
    await this.openSettingsButton.click();
    await expect(this.languageSelector).toBeVisible();
    await this.languageSelector.click();
    await this.page.getByRole('option', {name: option, exact: true}).click();
  }
}

export {TasklistHeader};
