/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

export class IdentityHeader {
  readonly openSettingsButton: Locator;
  readonly logoutButton: Locator;
  readonly rolesTab: Locator;
  readonly tenantsTab: Locator;

  constructor(page: Page) {
    this.openSettingsButton = page.getByRole('button', {
      name: 'Open Settings',
    });
    this.logoutButton = page.getByRole('button', {name: 'Log out'});
    this.rolesTab = page.locator('nav a').filter({hasText: /^Roles$/});
    this.tenantsTab = page.locator('nav a').filter({hasText: /^Tenants$/});
  }

  async logout() {
    await this.openSettingsButton.click();
    await this.logoutButton.click();
  }

  async navigateToRoles() {
    await this.rolesTab.click();
  }

  async navigateToTenants() {
    await this.tenantsTab.click();
  }
}
