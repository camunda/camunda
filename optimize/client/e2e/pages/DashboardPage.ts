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
import {setToggle} from './components/toggle';

type Tile =
  | {type: 'Optimize report'; reportName: string}
  | {type: 'External website'; url: string}
  | {type: 'Text'; text: string};

// Dashboard view and edit mode share this page object; edit-only controls are only visible in edit mode.
export class DashboardPage {
  readonly heading: Locator;
  readonly nameInput: Locator;
  readonly editLink: Locator;
  readonly description: Locator;
  readonly textTiles: Locator;
  readonly externalTiles: Locator;

  constructor(private readonly page: Page) {
    const main = page.getByRole('main');
    this.heading = main.getByRole('heading', {level: 1}).first();
    this.nameInput = main.getByRole('textbox', {name: 'Dashboard name'});
    this.editLink = main.getByRole('link', {name: 'Edit', exact: true});
    this.description = main.locator('.EntityDescription');
    this.textTiles = main.getByRole('textbox', {name: 'Text tile'});
    this.externalTiles = main.locator('iframe');
  }

  async goto(collection: Collection, dashboardId: string): Promise<void> {
    await this.page.goto(`${collection.url}dashboard/${dashboardId}/`);
  }

  // The report tile is a link that contains the report name and its result.
  reportTile(reportName: string): Locator {
    return this.page
      .getByRole('main')
      .getByRole('link')
      .filter({has: this.page.getByRole('link', {name: reportName, exact: true})});
  }

  tileNumber(reportName: string): Locator {
    return this.reportTile(reportName).getByTestId('report-number');
  }

  async edit(): Promise<void> {
    await this.editLink.click();
    await expect(this.nameInput).toBeVisible();
  }

  async save(): Promise<void> {
    await this.page.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(this.editLink).toBeVisible();
  }

  async cancel(): Promise<void> {
    await this.page.getByRole('link', {name: 'Cancel', exact: true}).click();
    await expect(this.editLink).toBeVisible();
  }

  async rename(name: string): Promise<void> {
    await this.nameInput.fill(name);
  }

  async setDescription(description: string): Promise<void> {
    await this.page.getByRole('button', {name: /(Add|Edit) description/}).click();
    const dialog = this.page.getByRole('dialog');
    await dialog.getByRole('textbox').fill(description);
    await dialog.getByRole('button', {name: 'Save'}).click();
    await expect(dialog).toBeHidden();
  }

  async addTile(tile: Tile): Promise<void> {
    await this.page.getByRole('button', {name: 'Add a tile'}).click();
    const dialog = this.page.getByRole('dialog', {name: 'Add a tile'});
    await dialog.getByRole('tab', {name: tile.type}).click();

    if (tile.type === 'Optimize report') {
      await dialog.getByRole('combobox', {name: 'Add report'}).click();
      await this.page.getByRole('option', {name: tile.reportName}).click();
    } else if (tile.type === 'External website') {
      await dialog.getByRole('textbox', {name: 'External website'}).fill(tile.url);
    } else {
      await dialog.getByRole('textbox', {name: 'Text'}).fill(tile.text);
    }

    await dialog.getByRole('button', {name: 'Add tile'}).click();
    await expect(dialog).toBeHidden();
    await this.dropTile();
  }

  async tileAction(tileText: string, action: 'Edit' | 'Delete'): Promise<void> {
    const tile = this.page.locator('.react-grid-item').filter({hasText: tileText});
    await tile.hover();
    await tile.getByRole('button', {name: action, exact: true}).click();
  }

  // New tiles stick to the cursor until they are dropped onto the grid.
  private async dropTile(): Promise<void> {
    const draggedTile = this.page.locator('.react-draggable-dragging');
    await expect(draggedTile).toHaveCount(1);
    const grid = await this.page.locator('.DashboardRenderer').boundingBox();
    if (!grid) {
      throw new Error('The dashboard grid is not rendered');
    }
    await this.page.mouse.move(grid.x + 10, grid.y + 10);
    await this.page.mouse.click(grid.x + 10, grid.y + 10);
    await expect(draggedTile).toHaveCount(0);
  }

  async addFilter(filter: 'Instance state' | 'Instance start date' | 'Variable'): Promise<void> {
    await this.page.getByRole('button', {name: 'Add a filter'}).click();
    await this.page.getByRole('menuitem', {name: filter, exact: true}).click();
  }

  async applyInstanceStateFilter(state: string): Promise<void> {
    await this.page
      .getByRole('button', {name: /Open menu$/})
      .first()
      .click();
    const options = this.page.getByRole('group', {name: 'Instance state'});
    await setToggle(options.getByRole('switch', {name: state, exact: true}), true);
    await this.page.keyboard.press('Escape');
  }

  async delete(): Promise<void> {
    await this.page.getByRole('button', {name: 'Delete', exact: true}).click();
    await confirmDialog(this.page, 'Delete Dashboard', 'Delete Dashboard');
  }
}
