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
    // The shadcn AccountMenu button's accessible name is driven by
    // `headerSettingsLabel` ("Settings"), not the old Carbon "Open Settings".
    this.openSettingsButton = page.getByRole('button', {name: 'Settings'});
    // The old Carbon combobox+listbox is gone; the settings dropdown now
    // renders language options as a Radix radio group
    // (aria-label="Language", one `role="radio"` per language).
    this.languageSelector = page.getByRole('radiogroup', {name: 'Language'});
    this.processesTab = page.getByRole('link', {name: 'Processes'});
    // The settings dropdown's "Log out" entry is a Radix DropdownMenuItem,
    // which renders `role="menuitem"`, not a `<button>`.
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
    // Selecting a language is a direct click on its radio item (no separate
    // listbox/option step like the old Carbon combobox required).
    await this.page.getByRole('radio', {name: option, exact: true}).click();
    // Unlike a plain DropdownMenuItem, this radio lives in a bare Radix
    // RadioGroup embedded in the dropdown (see AccountMenu.tsx), so selecting
    // it does not auto-close the menu — intentional, so a user can flip
    // through theme/language without the menu closing on every click. While
    // it stays open, the modal DropdownMenu marks the rest of the page
    // aria-hidden, which hides it from getByRole() queries even though it is
    // still visually on screen. Close it explicitly so callers can assert on
    // the underlying page right after this returns.
    await this.page.keyboard.press('Escape');
    await expect(this.languageSelector).toBeHidden();
  }

  async clickTasksTab() {
    await this.tasksTab.click();
  }

  async clickProcessesTab() {
    await this.processesTab.click();
  }
}

export {TasklistHeader};
