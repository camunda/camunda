/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {waitForAssertion} from 'utils/waitForAssertion';
import {sleep} from 'utils/sleep';

export type TaskCard = {
  readonly name: string;
  readonly assignee?: string;
};

class TaskPanelPage {
  readonly availableTasks: Locator;
  readonly taskCards: Locator;
  private page: Page;
  readonly taskListPageBanner: Locator;
  readonly collapseFilter: Locator;
  readonly filtersButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.availableTasks = page.getByTitle('Available tasks');
    this.taskCards = this.availableTasks.locator('article');
    this.taskListPageBanner = page
      .getByRole('navigation', {name: 'Camunda context'})
      .getByRole('link', {name: 'Tasklist', exact: true});
    this.collapseFilter = page.locator(
      'button[aria-controls="task-nav-bar"][aria-expanded="true"]',
    );
    this.filtersButton = page.getByRole('button', {name: 'Filters'});
  }

  async openTask(name: string, options: {timeout?: number} = {}) {
    const timeout = options.timeout ?? 10000;
    const task = this.availableTasks.getByText(name, {exact: true}).nth(0);
    await this.scrollUntilTaskRendered(task, timeout);
    await task.click({timeout});
  }

  /**
   * The available-tasks list is virtualized (@tanstack/react-virtual), so a
   * task outside the currently rendered window doesn't exist in the DOM at
   * all — no amount of waiting brings it in. Scroll the list container down
   * incrementally until the task mounts, or give up after `timeout`.
   *
   * The list container itself can be briefly absent right after navigation
   * (the query hasn't resolved yet, so the "no tasks" empty state renders in
   * its place) -- that's not the same as "there are no tasks to scroll
   * through", so keep polling rather than giving up the moment it's missing.
   */
  private async scrollUntilTaskRendered(
    task: Locator,
    timeout: number,
  ): Promise<void> {
    const scrollableList = this.page.getByTestId('scrollable-list');
    const deadline = Date.now() + timeout;

    while (Date.now() < deadline) {
      if ((await task.count()) > 0) {
        return;
      }
      if ((await scrollableList.count()) > 0) {
        await scrollableList.evaluate((element) =>
          element.scrollBy(0, element.clientHeight),
        );
      }
      await sleep(200);
    }
  }

  async filterBy(
    option:
      | 'All open tasks'
      | 'Unassigned'
      | 'Assigned to me'
      | 'Completed'
      | 'Custom',
  ) {
    let retryCount = 0;
    const maxRetries = 5;
    while (retryCount < maxRetries) {
      try {
        // Filters moved from a collapsible side panel of links to a
        // design-system dropdown: click the "Filters" button, then pick the
        // option from the menu.
        await expect(this.filtersButton).toBeVisible({timeout: 10000});
        await this.filtersButton.click();
        const menuOption = this.page.getByRole('menuitem', {
          name: option,
          exact: true,
        });
        await expect(menuOption).toBeVisible({timeout: 10000});
        await menuOption.click();

        if (option === 'All open tasks') {
          // "All open tasks" is the default filter, so the router omits it
          // from the URL — this tab lands on /tasklist with no `filter=`
          // param and there is no `all-open` segment to wait for.
          await expect(this.page).toHaveURL(
            /\/tasklist(?:\?(?!.*\bfilter=).*)?$/,
            {timeout: 15000},
          );
        } else {
          const expectedSegment =
            option === 'Assigned to me'
              ? 'assigned-to-me'
              : option.toLowerCase().replace(/\s+/g, '-');

          // Use regex to match the filter parameter to avoid matcher function issues
          const filterRegex = new RegExp(`filter=${expectedSegment}(?:&|$)`);
          await expect(this.page).toHaveURL(filterRegex, {timeout: 15000});
        }
        return;
      } catch (error) {
        retryCount++;
        console.log(`Attempt ${retryCount} failed. Retrying...`, error);
      }
    }
    throw new Error(
      `Failed to apply filter "${option}" after ${maxRetries} attempts.`,
    );
  }

  async clickCollapseFilter(): Promise<void> {
    await this.collapseFilter.click({timeout: 45000});
  }

  async assertCompletedHeadingVisible() {
    await waitForAssertion({
      assertion: async () => {
        // The completed view no longer renders a "Completed" heading; the
        // applied filter is reflected by the Filters dropdown trigger label.
        await expect(this.filtersButton).toContainText('Completed');
      },
      onFailure: async () => {
        console.log('Filter not applied, retrying...');
        await this.filterBy('Completed'); // Reapply the filter if necessary
      },
    });
  }

  async scrollToLastTask(name: string) {
    await this.page.getByText(name).last().scrollIntoViewIfNeeded();
  }

  async scrollToFirstTask(name: string) {
    await this.page.getByText(name).first().scrollIntoViewIfNeeded();
  }

  goToTaskDetails(taskKey: string) {
    return this.page.goto(`/tasklist/${taskKey}`);
  }

  private getTaskCards(task: TaskCard): Locator {
    let taskCards = this.taskCards.filter({
      has: this.page.getByText(task.name, {exact: true}),
    });

    if (task.assignee) {
      taskCards = taskCards.filter({
        has: this.page.getByText(task.assignee, {exact: true}),
      });
    }

    return taskCards;
  }

  async assertTaskCardsPresent(
    tasks: TaskCard[],
    options: {expectedCount?: number} = {},
  ): Promise<void> {
    const {expectedCount} = options;

    for (const task of tasks) {
      const taskCards = this.getTaskCards(task);

      if (expectedCount === undefined) {
        await expect(taskCards.first()).toBeVisible();
      } else {
        await expect(taskCards).toHaveCount(expectedCount);
      }
    }
  }

  async assertTaskCardsAbsent(tasks: TaskCard[]): Promise<void> {
    for (const task of tasks) {
      await expect(this.getTaskCards(task)).toHaveCount(0);
    }
  }

  async reloadPage(): Promise<void> {
    await this.page.reload();
  }
}

export {TaskPanelPage};
