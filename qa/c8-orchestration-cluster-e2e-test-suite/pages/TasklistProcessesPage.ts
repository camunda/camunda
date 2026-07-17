/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page, Locator} from '@playwright/test';

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
    return this.processTile.filter({hasText: name});
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
    await this.page
      .getByRole('dialog')
      .waitFor({state: 'hidden', timeout: 30000});
  }

  async filterByStartForm(
    option:
      | 'All Processes'
      | 'Requires form input to start'
      | 'Does not require form input to start',
  ): Promise<void> {
    await this.processFilterDropdown.click();
    await this.page.getByRole('option', {name: option, exact: true}).click();
  }

  // Open the Processes tab with the start-form filter and/or a name search
  // applied via the URL. The suite runs against a shared cluster where many
  // process definitions exist and other tests deploy concurrently, so pinning
  // to a specific process by name (plus the filter) is the only way to make
  // presence/absence assertions deterministic and pagination-proof. Combining
  // both via the search widget and the filter dropdown is unreliable because
  // they share a single debounced URL updater, so the URL is set directly.
  async openProcessesFiltered(params: {
    hasStartForm?: 'yes' | 'no';
    search?: string;
  }): Promise<void> {
    const query = new URLSearchParams();
    if (params.hasStartForm) {
      query.set('hasStartForm', params.hasStartForm);
    }
    if (params.search) {
      query.set('search', params.search);
    }
    const queryString = query.toString();
    await this.page.goto(
      `/tasklist/processes${queryString ? `?${queryString}` : ''}`,
      {waitUntil: 'load'},
    );
    await this.dismissFirstTimeModalIfPresent();
  }

  async dismissFirstTimeModalIfPresent(): Promise<void> {
    if (await this.continueButton.isVisible().catch(() => false)) {
      await this.continueButton.click();
    }
  }
}

export {TasklistProcessesPage};
