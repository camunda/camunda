/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';

class ProcessesPage {
  private page: Page;
  readonly continueButton: Locator;
  readonly cancelButton: Locator;
  readonly startProcessButton: Locator;
  readonly docsLink: Locator;
  readonly searchProcessesInput: Locator;
  readonly processTile: Locator;
  readonly tasksTab: Locator;

  constructor(page: Page) {
    this.page = page;
    this.continueButton = page.getByRole('button', {name: 'Continue'});
    this.cancelButton = page.getByRole('button', {name: 'Cancel'});
    this.startProcessButton = page.getByRole('button', {name: 'Start process'});
    this.docsLink = page.getByRole('link', {name: 'here'});
    this.searchProcessesInput = page.getByPlaceholder('Search processes');
    this.processTile = page.getByTestId('process-tile');
    this.tasksTab = page.getByRole('link', {name: 'Tasks'});
  }

  async clickContinueButton() {
    await this.continueButton.click();
  }

  async clickCancelButton() {
    await this.cancelButton.click();
  }

  async clickStartProcessButton() {
    await this.startProcessButton.click();
  }

  public async clickDocsLink() {
    await this.docsLink.click();
  }

  public async searchForProcess(process: string) {
    await this.searchProcessesInput.click();
    await this.searchProcessesInput.fill(process);
    await this.searchProcessesInput.press('Enter');
  }
}
export {ProcessesPage};
