/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page, Locator} from '@playwright/test';

class TasklistProcessesPageV1 {
  private page: Page;
  readonly continueButton: Locator;
  readonly cancelButton: Locator;
  readonly startProcessButton: Locator;
  readonly docsLink: Locator;
  readonly searchProcessesInput: Locator;
  readonly processTile: Locator;
  readonly startProcessSubButton: Locator;

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
}

export {TasklistProcessesPageV1};
