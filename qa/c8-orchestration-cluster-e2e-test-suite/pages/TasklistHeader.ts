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
  readonly tasksTab: Locator;

  constructor(page: Page) {
    this.page = page;
    this.openSettingsButton = page.getByRole('button', {name: 'Settings'});
    this.languageSelector = page.getByRole('radiogroup', {name: 'Language'});
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
    await this.openSettingsButton.click();
    await expect(this.languageSelector).toBeVisible();
    await this.languageSelector
      .getByRole('radio', {name: option, exact: true})
      .click();
    // Selecting a language no longer dismisses the settings menu (it used to be
    // a combobox that auto-closed). Close the menu so the page behind it — which
    // callers assert against — is no longer covered by the overlay.
    await this.page.keyboard.press('Escape');
    await expect(this.page.getByRole('menu')).toBeHidden();
  }

  async clickTasksTab() {
    await this.tasksTab.click();
  }

  async clickProcessesTab() {
    await this.processesTab.click();
  }
}

export {TasklistHeader};
