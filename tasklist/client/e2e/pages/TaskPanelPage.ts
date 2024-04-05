/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';

class TaskPanelPage {
  private page: Page;
  readonly availableTasks: Locator;
  readonly filterOptions: Locator;

  constructor(page: Page) {
    this.page = page;
    this.availableTasks = page.getByTitle('Available tasks');
    this.filterOptions = page.getByRole('combobox', {name: 'Filter options'});
  }

  async openTask(name: string) {
    await this.availableTasks.getByText(name, {exact: true}).nth(0).click();
  }

  async filterBy(
    option:
      | 'All open'
      | 'Unassigned'
      | 'Assigned to me'
      | 'Completed'
      | 'Custom',
  ) {
    await this.filterOptions.click();
    await this.page.getByRole('option', {name: option}).click({
      force: true,
    });
  }

  async scrollToLastTask(name: string) {
    await this.page.getByText(name).last().scrollIntoViewIfNeeded();
  }

  async scrollToFirstTask(name: string) {
    await this.page.getByText(name).first().scrollIntoViewIfNeeded();
  }
}

export {TaskPanelPage};
