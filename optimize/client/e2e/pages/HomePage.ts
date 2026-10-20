/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Page} from '@playwright/test';

import {EntityList} from './components/EntityList';

// The "Collections" overview listing collections and entities outside of any collection.
export class HomePage {
  readonly list: EntityList;

  constructor(private readonly page: Page) {
    this.list = new EntityList(page, page.getByRole('main'));
  }

  async goto(): Promise<void> {
    await this.page.goto('/#/collections');
  }

  async createNew(item: 'Collection' | 'Dashboard' | 'Report'): Promise<void> {
    await this.page.getByRole('button', {name: 'Create new', exact: true}).click();
    await this.page.getByRole('menuitem', {name: item, exact: true}).click();
  }

  // Creates a collection with all available processes as data sources.
  async createCollection(name: string): Promise<void> {
    await this.createNew('Collection');
    const dialog = this.page.getByRole('dialog');
    await dialog.getByRole('textbox', {name: 'Collection name'}).fill(name);
    await dialog.getByRole('button', {name: 'Add data sources'}).click();
    await dialog.getByRole('button', {name: 'Create collection'}).click();
    await expect(dialog).toBeHidden();
  }
}
