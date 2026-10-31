/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

import {setCheckbox} from './toggle';

// Any list of entities (reports, dashboards, collections, users, data sources, alerts).
export class EntityList {
  readonly searchBox: Locator;

  constructor(
    private readonly page: Page,
    private readonly root: Locator
  ) {
    this.searchBox = root.getByRole('searchbox');
  }

  get rows(): Locator {
    return this.root.getByRole('rowgroup').nth(1).getByRole('row');
  }

  row(text: string): Locator {
    return this.rows.filter({hasText: text});
  }

  link(name: string): Locator {
    return this.root.getByRole('link', {name, exact: true});
  }

  async open(name: string): Promise<void> {
    await this.link(name).click();
  }

  async rowAction(text: string, action: string): Promise<void> {
    await this.row(text).getByRole('button', {name: 'Options'}).click();
    await this.page.getByRole('menuitem', {name: action, exact: true}).click();
  }

  // Rows render up to two actions as inline buttons instead of an overflow menu.
  async inlineRowAction(text: string, action: string): Promise<void> {
    await this.row(text).getByRole('button', {name: action, exact: true}).click();
  }

  async select(text: string): Promise<void> {
    await setCheckbox(this.row(text).getByRole('checkbox', {name: 'Select row'}), true);
  }

  async selectAll(): Promise<void> {
    await setCheckbox(this.root.getByRole('checkbox', {name: 'Select all rows'}), true);
  }

  async bulkAction(action: string): Promise<void> {
    await this.root
      .getByRole('region', {name: 'data table toolbar'})
      .getByRole('button', {name: action, exact: true})
      .click();
  }
}
