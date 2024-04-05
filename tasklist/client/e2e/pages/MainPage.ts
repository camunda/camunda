/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';

class MainPage {
  private page: Page;
  readonly openSettingsButton: Locator;
  readonly processesTab: Locator;
  readonly logoutButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.openSettingsButton = page.getByRole('button', {name: 'Open Settings'});
    this.processesTab = page.getByRole('link', {name: 'Processes'});
    this.logoutButton = page.getByRole('button', {name: 'Log out'});
  }

  async clickOpenSettingsButton() {
    await this.openSettingsButton.click();
  }

  async clickLogoutButton() {
    await this.logoutButton.click();
  }

  async logout() {
    await this.openSettingsButton.click();
    await this.clickLogoutButton();
  }

  async clickProcessesTab() {
    await this.processesTab.click();
  }
}

export {MainPage};
