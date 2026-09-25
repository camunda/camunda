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
import {setCheckbox, setToggle} from './components/toggle';

type SetupItem = 'View' | 'Measure' | 'Group by' | 'and' | 'Visualization';

// Report view and edit mode share this page object; edit-only controls are only visible in edit mode.
export class ReportPage {
  readonly heading: Locator;
  readonly nameInput: Locator;
  readonly editLink: Locator;
  readonly result: Locator;
  readonly number: Locator;
  readonly resultTable: Locator;
  readonly chart: Locator;
  readonly filterList: Locator;
  readonly warning: Locator;

  constructor(private readonly page: Page) {
    const main = page.getByRole('main');
    this.heading = main.getByRole('heading', {level: 1});
    this.nameInput = main.getByRole('textbox', {name: 'Report name'});
    this.editLink = main.getByRole('link', {name: 'Edit', exact: true});
    this.result = page.getByTestId('report-renderer');
    this.number = page.getByTestId('report-number');
    this.resultTable = this.result.getByRole('table');
    this.chart = this.result.locator('canvas');
    this.filterList = main.locator('.ActionItem');
    this.warning = main.getByRole('status');
  }

  async goto(url: string): Promise<void> {
    await this.page.goto(url);
  }

  // Opens an existing report in edit mode with the preview updating on every change.
  async gotoEdit(collection: Collection, reportId: string): Promise<void> {
    await this.page.goto(`${collection.url}report/${reportId}/edit`);
    await this.enableAutoPreview();
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

  async delete(): Promise<void> {
    await this.page.getByRole('button', {name: 'Delete', exact: true}).click();
    await confirmDialog(this.page, 'Delete Report', 'Delete Report');
  }

  async rename(name: string): Promise<void> {
    await this.nameInput.fill(name);
  }

  async enableAutoPreview(): Promise<void> {
    await setToggle(this.page.getByRole('switch', {name: 'Update preview automatically'}), true);
  }

  async addDataSources(...processNames: string[]): Promise<void> {
    await this.page
      .getByRole('main')
      .getByRole('button', {name: 'Add', exact: true})
      .first()
      .click();
    const dialog = this.page.getByRole('dialog', {name: 'Add Process definition'});
    for (const name of processNames) {
      await setCheckbox(dialog.getByRole('checkbox', {name, exact: true}), true);
    }
    await dialog.getByRole('button', {name: 'Add', exact: true}).click();
    await expect(dialog).toBeHidden();
  }

  async selectView(view: string, measure?: string): Promise<void> {
    await this.choose('View', view);
    if (measure) {
      await this.choose('Measure', measure);
    }
  }

  async selectGroupBy(option: string, subOption?: string): Promise<void> {
    await this.choose('Group by', option, subOption);
  }

  async selectDistributedBy(option: string, subOption?: string): Promise<void> {
    await this.choose('and', option, subOption);
  }

  async openGroupByMenu(): Promise<void> {
    await this.setupItem('Group by').getByRole('button').first().click();
  }

  async selectVisualization(visualization: string): Promise<void> {
    await this.choose('Visualization', visualization);
  }

  async addMeasure(measure: string): Promise<void> {
    await this.page.getByRole('button', {name: '+ Add measure'}).click();
    await this.pickMenuItem(measure);
  }

  // Opens the "Add" menu of the process instance or flow node filters and picks an entry.
  async openFilter(
    level: 'instance' | 'flow node',
    filter: string,
    subFilter?: string
  ): Promise<Locator> {
    await this.expandSection('Filters');
    const addButtons = this.page.getByRole('button', {name: 'Add Open menu'});
    await (level === 'instance' ? addButtons.first() : addButtons.last()).click();
    await this.pickMenuItem(filter, subFilter);
    return this.page.getByRole('dialog');
  }

  async removeFilter(text: string): Promise<void> {
    await this.filterList.filter({hasText: text}).getByRole('button').last().click();
  }

  async chooseOption(dialog: Locator, label: string): Promise<void> {
    await setCheckbox(dialog.getByRole('radio', {name: label, exact: true}), true);
  }

  async chooseValue(dialog: Locator, value: string): Promise<void> {
    await setCheckbox(dialog.getByRole('checkbox', {name: value, exact: true}), true);
  }

  async chooseDateRange(
    dialog: Locator,
    type: string,
    {value, unit}: {value: string; unit: string}
  ): Promise<void> {
    await dialog
      .getByRole('button', {name: /Open menu$/})
      .first()
      .click();
    await menuItemNamed(this.page, type).click();
    await dialog.getByRole('textbox').fill(value);
    await dialog
      .getByRole('button', {name: /Open menu$/})
      .last()
      .click();
    await menuItemNamed(this.page, unit).click();
  }

  async applyFilter(dialog: Locator): Promise<void> {
    await dialog.getByRole('button', {name: 'Add filter'}).click();
    await expect(dialog).toBeHidden();
  }

  async selectFlowNode(dialog: Locator, flowNodeId: string): Promise<void> {
    await dialog.locator(`.djs-element[data-element-id="${flowNodeId}"]`).click();
  }

  async openConfiguration(): Promise<Locator> {
    await this.page.getByRole('button', {name: 'Configuration options'}).click();
    return this.page.getByRole('dialog');
  }

  private async expandSection(name: string): Promise<void> {
    const section = this.page.getByRole('button', {name, exact: true});
    if ((await section.getAttribute('aria-expanded')) !== 'true') {
      await section.click();
    }
  }

  private async choose(item: SetupItem, option: string, subOption?: string): Promise<void> {
    const button = this.setupItem(item).getByRole('button').first();
    // The current selection is a disabled menu entry, so selecting it again is a no-op.
    if (!subOption && (await button.innerText()).trim() === option) {
      return;
    }
    await button.click();
    // A late evaluation response overwrites newer changes, so let each change settle first.
    const evaluated = this.page.waitForResponse(
      (response) => response.request().method() === 'POST' && response.url().includes('/evaluate')
    );
    await this.pickMenuItem(option, subOption);
    await evaluated;
  }

  private async pickMenuItem(option: string, subOption?: string): Promise<void> {
    const menuItem = menuItemNamed(this.page, option);
    if (subOption) {
      await menuItem.hover();
      await menuItemNamed(this.page, subOption).click();
      return;
    }
    await menuItem.click();
  }

  private setupItem(item: SetupItem): Locator {
    if (item === 'Visualization') {
      return this.page.locator('.Visualization');
    }
    return this.page.locator('li', {has: this.page.locator(':scope > .label', {hasText: item})});
  }
}

function menuItemNamed(page: Page, name: string): Locator {
  return page
    .getByRole('menuitemcheckbox', {name, exact: true})
    .or(page.getByRole('menuitem', {name, exact: true}));
}
