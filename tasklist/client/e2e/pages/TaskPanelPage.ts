/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

type FilterParam =
  | 'all-open'
  | 'unassigned'
  | 'assigned-to-me'
  | 'completed'
  | 'custom';
type SortByParam = 'creation' | 'follow-up' | 'due' | 'completion';

class TaskPanelPage {
  private page: Page;
  readonly availableTasks: Locator;
  readonly filterOptions: Locator;
  readonly expandSidePanelButton: Locator;
  readonly addCustomFilterButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.availableTasks = page.getByTitle('Available tasks');
    this.filterOptions = page.getByRole('combobox', {name: 'Filter options'});
    this.expandSidePanelButton = page.getByRole('button', {
      name: 'Expand to show filters',
    });
    this.addCustomFilterButton = page.getByRole('button', {
      name: 'Filter tasks',
    });
  }

  async goto(params?: {filter?: FilterParam; sortBy?: SortByParam}) {
    if (params === undefined) {
      await this.page.goto('/', {
        waitUntil: 'networkidle',
      });
    } else {
      const searchParams = new URLSearchParams(params ?? {});
      await this.page.goto(`/?${searchParams.toString()}`, {
        waitUntil: 'networkidle',
      });
    }
  }

  task(name: string) {
    return this.availableTasks.getByLabel(name).nth(0);
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
