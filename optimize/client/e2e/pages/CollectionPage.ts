/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Locator, type Page} from '@playwright/test';

import type {Collection} from '../fixtures';
import {confirmDialog} from './components/dialog';
import {EntityList} from './components/EntityList';
import {TemplateDialog} from './components/TemplateDialog';
import {setCheckbox} from './components/toggle';

type Tab = 'Dashboards & reports' | 'Alerts' | 'Users' | 'Data sources';

export class CollectionPage {
  readonly heading: Locator;
  readonly list: EntityList;

  constructor(private readonly page: Page) {
    this.heading = page.getByRole('main').getByRole('heading', {level: 2});
    this.list = new EntityList(page, page.getByRole('tabpanel'));
  }

  async goto(collection: Collection): Promise<void> {
    await this.page.goto(collection.url);
  }

  async openTab(tab: Tab): Promise<void> {
    await this.page.getByRole('tab', {name: tab}).click();
  }

  async createNew(item: 'Dashboard' | 'Report' | 'Process KPI'): Promise<void> {
    await this.page.getByRole('button', {name: 'Create new', exact: true}).click();
    await this.page.getByRole('menuitem', {name: item, exact: true}).click();
  }

  async createBlankReport(): Promise<void> {
    await this.createNew('Report');
    const dialog = new TemplateDialog(this.page, 'Create new report');
    await dialog.selectTemplate('Blank report');
    await dialog.confirm();
  }

  async createBlankDashboard(): Promise<void> {
    await this.createNew('Dashboard');
    const dialog = new TemplateDialog(this.page, 'Create new dashboard');
    await dialog.selectTemplate('Blank dashboard');
    await dialog.confirm();
  }

  async collectionAction(action: 'Edit' | 'Copy' | 'Delete'): Promise<void> {
    await this.page.getByRole('button', {name: 'Options'}).click();
    await this.page.getByRole('menuitem', {name: action}).click();
  }

  async rename(name: string): Promise<void> {
    await this.collectionAction('Edit');
    const dialog = this.page.getByRole('dialog');
    await dialog.getByRole('textbox', {name: 'Collection name'}).fill(name);
    await dialog.getByRole('button', {name: 'Edit collection'}).click();
    await expect(dialog).toBeHidden();
  }

  async copy(name: string): Promise<void> {
    await this.collectionAction('Copy');
    const dialog = this.page.getByRole('dialog');
    await dialog.getByRole('textbox').fill(name);
    await dialog.getByRole('button', {name: 'Copy'}).click();
    await expect(dialog).toBeHidden();
  }

  async delete(): Promise<void> {
    await this.collectionAction('Delete');
    await confirmDialog(this.page, /Delete/, /Delete/);
  }

  async addDataSources(...processNames: string[]): Promise<void> {
    await this.page.getByRole('tabpanel').getByRole('button', {name: 'Add', exact: true}).click();
    const dialog = this.page.getByRole('dialog');
    for (const name of processNames) {
      await setCheckbox(dialog.getByRole('checkbox', {name, exact: true}), true);
    }
    await dialog.getByRole('button', {name: /^Add/}).last().click();
    await expect(dialog).toBeHidden();
  }

  async addUser(username: string, role: 'Viewer' | 'Editor' | 'Manager'): Promise<void> {
    await this.page.getByRole('tabpanel').getByRole('button', {name: 'Add', exact: true}).click();
    const dialog = this.page.getByRole('dialog', {name: 'Add user'});
    // The user search only reacts to typed input, and each keystroke re-renders the results.
    const search = dialog.getByRole('combobox', {name: /Users/});
    const searchDone = this.page.waitForResponse(
      (response) =>
        response.url().includes('/api/identity/search') &&
        response.url().includes(`terms=${username}`)
    );
    await search.pressSequentially(username);
    await searchDone;
    // The first option only echoes the typed text; the search result carries the user id.
    await this.page.getByRole('option', {name: new RegExp(`^${username}.*USER:`)}).click();
    await expect(search).toHaveAccessibleName(/Total items selected: 1/);
    await setCheckbox(dialog.getByRole('radio', {name: new RegExp(`^${role}`)}), true);
    await dialog.getByRole('button', {name: 'Add', exact: true}).click();
    await expect(dialog).toBeHidden();
  }

  async changeUserRole(username: string, role: 'Viewer' | 'Editor' | 'Manager'): Promise<void> {
    await this.list.rowAction(username, 'Edit');
    const dialog = this.page.getByRole('dialog');
    await setCheckbox(dialog.getByRole('radio', {name: new RegExp(`^${role}`)}), true);
    await dialog.getByRole('button', {name: 'Apply'}).click();
    await expect(dialog).toBeHidden();
    await expect(this.list.row(username)).toContainText(role);
  }

  async removeUser(username: string): Promise<void> {
    await this.list.rowAction(username, 'Remove');
    await confirmDialog(this.page, /Remove/, /Remove/);
    await expect(this.list.row(username)).toBeHidden();
  }
}
