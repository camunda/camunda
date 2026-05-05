/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

export class OptimizeDashboardPage {
  private page: Page;
  readonly dashboardName: Locator;
  readonly editButton: Locator;
  readonly saveButton: Locator;
  readonly autoRefreshButton: Locator;
  readonly reportTile: Locator;
  readonly fullscreenButton: Locator;
  readonly addTileButton: Locator;
  readonly createTileModal: Locator;
  readonly modalConfirmButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.dashboardName = page.locator('.DashboardView .name');
    this.editButton = page.locator('.edit-button');
    this.saveButton = page.locator('button').filter({hasText: 'Save'});
    this.autoRefreshButton = page.locator('.tools .AutoRefreshSelect button');
    this.reportTile = page.locator('.OptimizeReportTile');
    this.fullscreenButton = page.locator('.fullscreen-button');
    this.addTileButton = page.locator('.CreateTileModal button').filter({hasText: 'Add tile'});
    this.createTileModal = page.locator('.CreateTileModal');
    this.modalConfirmButton = page.locator(
      '.Modal.is-visible .cds--modal-footer .cds--btn:last-child:not([disabled])',
    );
  }

  reportTileAt(index: number): Locator {
    return this.reportTile.nth(index);
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }

  async clickAutoRefreshOption(option: string): Promise<void> {
    await this.autoRefreshButton.click();
    await this.page
      .locator('.cds--menu-item')
      .filter({hasText: option})
      .click();
  }
}
