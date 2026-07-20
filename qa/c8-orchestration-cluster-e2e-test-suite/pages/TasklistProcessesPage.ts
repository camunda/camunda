/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Page, type Locator} from '@playwright/test';

const START_FORM_FILTER_URL_PARAM = {
  'All Processes': null,
  'Requires form input to start': 'yes',
  'Does not require form input to start': 'no',
} as const;

class TasklistProcessesPage {
  private page: Page;
  readonly continueButton: Locator;
  readonly cancelButton: Locator;
  readonly startProcessButton: Locator;
  readonly docsLink: Locator;
  readonly searchProcessesInput: Locator;
  readonly processTile: Locator;
  readonly startProcessSubButton: Locator;
  readonly processFilterDropdown: Locator;

  constructor(page: Page) {
    this.page = page;
    this.continueButton = page.getByRole('button', {name: 'Continue'});
    this.cancelButton = page.getByRole('button', {name: 'Cancel'});
    this.startProcessButton = page.getByRole('button', {name: 'Start process'});
    this.startProcessSubButton = page
      .getByRole('button', {name: 'Start process'})
      .last();
    this.docsLink = page.getByRole('link', {name: 'here'});
    this.searchProcessesInput = page.getByPlaceholder('Search processes');
    this.processTile = page.getByTestId('process-tile');
    this.processFilterDropdown = page.getByTestId('process-filters');
  }

  processTileByName(name: string): Locator {
    return this.processTile.filter({hasText: name}).first();
  }

  requiresFormInputTagFor(name: string): Locator {
    return this.processTileByName(name).locator('.cds--tag__label', {
      hasText: 'Requires form input',
    });
  }

  async goto() {
    await this.page.goto('/processes', {
      waitUntil: 'load',
    });
  }

  public async searchForProcess(process: string) {
    await this.searchProcessesInput.click();
    await this.searchProcessesInput.fill(process);
    await this.searchProcessesInput.press('Enter');
  }

  async getModalStartProcessButton(processName: string): Promise<Locator> {
    return this.page
      .getByLabel(`Start process ${processName}`)
      .getByRole('button', {name: 'Start process'});
  }

  async clickStartProcessButton(name: string): Promise<void> {
    await this.processTile
      .filter({hasText: name})
      .nth(0)
      .getByRole('button', {name: 'Start process'})
      .click({timeout: 60000});
  }

  async clickStartProcessSubButton(): Promise<void> {
    await this.startProcessSubButton.click();
  }

  async filterByStartForm(
    option: keyof typeof START_FORM_FILTER_URL_PARAM,
  ): Promise<void> {
    await this.processFilterDropdown.click();
    await this.page.getByRole('option', {name: option, exact: true}).click();

    // The search box and this dropdown share one debounced URL updater, so
    // wait for the filter's URL update to commit before a caller searches.
    const paramValue = START_FORM_FILTER_URL_PARAM[option];
    if (paramValue === null) {
      await expect(this.page).not.toHaveURL(/hasStartForm=/);
    } else {
      await expect(this.page).toHaveURL(
        new RegExp(`hasStartForm=${paramValue}`),
      );
    }
  }
}

export {TasklistProcessesPage};
