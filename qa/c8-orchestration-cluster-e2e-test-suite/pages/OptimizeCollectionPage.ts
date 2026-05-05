/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

export class OptimizeCollectionPage {
  private page: Page;
  readonly collectionTitle: Locator;
  readonly collectionContextMenu: Locator;
  readonly entitiesTab: Locator;
  readonly userTab: Locator;
  readonly alertTab: Locator;
  readonly sourcesTab: Locator;
  readonly addButton: Locator;
  readonly emptyStateAddButton: Locator;
  readonly sourceModalSearchField: Locator;
  readonly selectAllCheckbox: Locator;
  readonly bulkRemoveButton: Locator;
  readonly modalNameInput: Locator;
  readonly modalConfirmButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.collectionTitle = page.locator('.Collection h2');
    this.collectionContextMenu = page.locator(
      '.Collection .cds--overflow-menu__wrapper button',
    );
    const tabButton = page.locator('.Collection .cds--tabs__nav-item');
    this.entitiesTab = tabButton.filter({hasText: 'Dashboards'});
    this.userTab = tabButton.filter({hasText: 'Users'});
    this.alertTab = tabButton.filter({hasText: 'Alerts'});
    this.sourcesTab = tabButton.filter({hasText: 'Data sources'});
    const activeTab = page.locator(
      '.Collection .cds--tab-content:not([hidden])',
    );
    this.addButton = activeTab.locator(
      '.cds--toolbar-content > .cds--btn--primary',
    );
    this.emptyStateAddButton = activeTab.locator(
      '.EmptyState .cds--btn--primary',
    );
    this.sourceModalSearchField = page.locator(
      '.SourcesModal .cds--search-input',
    );
    this.selectAllCheckbox = page.locator(
      '.Table thead .cds--table-column-checkbox label',
    );
    this.bulkRemoveButton = activeTab
      .locator('.cds--action-list button')
      .filter({hasText: 'Remove'});
    this.modalNameInput = page.locator('.Modal.is-visible input[type="text"]');
    this.modalConfirmButton = page.locator(
      '.Modal.is-visible .cds--modal-footer .cds--btn:last-child:not([disabled])',
    );
  }

  overflowMenuOption(text: string): Locator {
    return this.page
      .locator('.cds--overflow-menu-options__option')
      .filter({hasText: text});
  }

  itemCheckbox(index: number): Locator {
    return this.page
      .locator('.Table tbody tr')
      .nth(index)
      .locator('.cds--table-column-checkbox label');
  }

  async editName(newName: string): Promise<void> {
    await this.collectionContextMenu.click();
    await this.overflowMenuOption('Edit').click();
    await this.modalNameInput.fill(newName);
    await this.modalConfirmButton.click();
  }

  async copyCollection(copyName: string): Promise<void> {
    await this.collectionContextMenu.click();
    await this.overflowMenuOption('Copy').click();
    await this.modalNameInput.fill(copyName);
    await this.modalConfirmButton.click();
  }

  async deleteCollection(): Promise<void> {
    await this.collectionContextMenu.click();
    await this.overflowMenuOption('Delete').click();
    await this.modalConfirmButton.click();
  }
}
