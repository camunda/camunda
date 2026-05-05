/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

export class OptimizeHomePage {
  private page: Page;
  readonly createNewButton: Locator;
  readonly noDataNotice: Locator;
  readonly entityList: Locator;
  readonly searchField: Locator;
  readonly bulkDeleteButton: Locator;
  readonly selectAllCheckbox: Locator;
  readonly modalNameInput: Locator;
  readonly modalConfirmButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.createNewButton = page.locator('.CreateNewButton');
    this.noDataNotice = page.locator('.NoDataNotice');
    this.entityList = page.locator('.EntityList');
    this.searchField = page.locator('input.cds--search-input');
    this.bulkDeleteButton = page
      .locator('.cds--action-list button')
      .filter({hasText: 'Delete'});
    this.selectAllCheckbox = page.locator('thead .cds--checkbox--inline');
    this.modalNameInput = page.locator('.Modal.is-visible input[type="text"]');
    this.modalConfirmButton = page.locator(
      '.Modal.is-visible .cds--modal-footer .cds--btn:last-child:not([disabled])',
    );
  }

  listItem(type: string): Locator {
    return this.page
      .locator('.EntityList tbody tr td:nth-child(2) span')
      .filter({hasText: new RegExp(type, 'i')})
      .locator('..')
      .locator('..');
  }

  listItemLink(type: string): Locator {
    return this.listItem(type).locator('td:nth-child(2) a');
  }

  listItemContextMenu(item: Locator): Locator {
    return item.locator('button.cds--overflow-menu');
  }

  menuOption(text: string): Locator {
    return this.page
      .locator('.cds--menu-item')
      .filter({hasText: text})
      .filter({hasNot: this.page.locator('[hidden]')});
  }

  async clickCreateNew(entityType: 'Collection' | 'Dashboard' | 'Report'): Promise<void> {
    await this.createNewButton.click();
    await this.menuOption(entityType).click();
  }

  async bulkDeleteAll(): Promise<void> {
    await this.selectAllCheckbox.click();
    await this.bulkDeleteButton.click();
    await this.modalConfirmButton.click();
  }
}
