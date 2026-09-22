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
  readonly processesTab: Locator;
  readonly logoutButton: Locator;
  readonly tasksTab: Locator;

  constructor(page: Page) {
    this.page = page;
    this.openSettingsButton = page.getByRole('button', {name: 'Settings'});
    this.processesTab = page.getByRole('link', {name: 'Processes'});
    this.logoutButton = page.getByRole('menuitem', {name: 'Log out'});
    this.tasksTab = page
      .getByRole('navigation')
      .getByRole('link', {name: 'Tasks', exact: true});
  }

  async logout() {
    await this.openSettingsButton.click();
    await this.logoutButton.click();
  }

  async changeLanguage(option: 'Français' | 'English' | 'Deutsch' | 'Español') {
    // The language picker is no longer a combobox: it's a design-system
    // radio group inside the Settings menu, with one radio per language.
    await this.openSettingsButton.click();
    const languageOption = this.page.getByRole('radio', {name: option});
    await expect(languageOption).toBeVisible();
    await languageOption.click();
    await expect(languageOption).toBeChecked();
    // Leaves the Settings menu open -- callers that need to assert on the
    // rest of the page must close it first (see settings.spec.ts): Radix's
    // DropdownMenu is modal by default, so everything outside it is
    // aria-hidden while it's open.
  }

  async clickTasksTab() {
    await this.tasksTab.click();
  }

  async clickProcessesTab() {
    await this.processesTab.click();
  }
}

export {TasklistHeader};
