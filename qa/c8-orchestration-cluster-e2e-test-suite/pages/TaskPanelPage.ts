/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {waitForAssertion} from 'utils/waitForAssertion';

export type TaskCard = {
  readonly name: string;
  readonly assignee?: string;
};

class TaskPanelPage {
  readonly availableTasks: Locator;
  readonly taskCards: Locator;
  readonly filterSelectButton: Locator;
  private page: Page;
  readonly taskListPageBanner: Locator;
  readonly completedHeading: Locator;

  constructor(page: Page) {
    this.page = page;
    this.availableTasks = page.getByTitle('Available tasks');
    this.taskCards = this.availableTasks.locator('article');
    // The old Carbon expandable filter sidebar (`[aria-label="Filter
    // controls"]`, "Expand to show filters") no longer exists. Filtering is
    // now a single dropdown-trigger button (id="filter-select") whose
    // accessible name is the `taskFiltersHeaderAria` string ("Filters");
    // clicking it opens a Radix DropdownMenu of filter options.
    this.filterSelectButton = page.getByRole('button', {
      name: 'Filters',
      exact: true,
    });
    // The header logo's accessible name is now just "Camunda logo" (the old
    // Carbon header appended the product name, "Camunda logo Tasklist").
    this.taskListPageBanner = page.getByRole('link', {
      name: 'Camunda logo',
    });
    // There is no page heading for the "Completed" filter post-redesign --
    // that assumption dates back to the old Carbon sidebar, where each
    // filter link routed to a page with its own <h1>. FilterSelect.tsx now
    // renders every filter (including "Completed") as plain visible text
    // inside the filter-select trigger button (id="filter-select"); the
    // button's accessible name is the static `taskFiltersHeaderAria` string
    // ("Filters"), not the filter's label, because it carries a static
    // aria-label, so this has to be a text lookup scoped to that button
    // rather than an accessible-name lookup.
    this.completedHeading = this.filterSelectButton.getByText('Completed', {
      exact: true,
    });
  }

  async openTask(name: string, options: {timeout?: number} = {}) {
    const timeout = options.timeout ?? 10000;
    const task = this.availableTasks.getByText(name, {exact: true}).nth(0);

    // The available-tasks list is virtualized and infinite-scrolls, sorted
    // newest-first. Under the parallel nightly load a task created earlier gets
    // pushed below the initially loaded page, so it is not in the DOM at all --
    // and reloading just re-fetches the same newest-first first page, so it
    // never reveals an older task (this is why openTask timed out even at 60s).
    // The only way to reach an older task is to scroll the list so it pulls the
    // next page via fetchNextPage. Between attempts, scroll the last rendered
    // card into view to load older tasks; when scrolling can no longer grow the
    // list, reload to pick up anything newly indexed (the just-created case the
    // route's 5s poll would otherwise cover). Scale the retry budget to the
    // caller's timeout so slow-indexing callers keep their wait.
    const perAttemptTimeout = Math.min(timeout, 10000);
    const maxRetries = Math.max(3, Math.ceil(timeout / perAttemptTimeout) + 3);

    await waitForAssertion({
      assertion: async () => {
        await expect(task).toBeVisible({timeout: perAttemptTimeout});
      },
      onFailure: async () => {
        const before = await this.taskCards.count();
        if (before > 0) {
          await this.taskCards
            .last()
            .scrollIntoViewIfNeeded()
            .catch(() => {});
          // Wait for the next (older) page to attach instead of a fixed pause;
          // the wait simply times out (and is ignored) once the list can grow
          // no further.
          await this.taskCards
            .nth(before)
            .waitFor({state: 'attached', timeout: perAttemptTimeout})
            .catch(() => {});
        }
        // If scrolling could not grow the list and the target still isn't
        // present, force a fresh fetch -- covers the just-created/not-yet-
        // indexed case a reload (or the route's 5s poll) resolves.
        if (
          (await this.taskCards.count()) <= before &&
          (await task.count()) === 0
        ) {
          console.log(
            `Task "${name}" not visible yet, reloading and retrying...`,
          );
          await this.reloadPage();
        }
      },
      maxRetries,
    });

    await task.scrollIntoViewIfNeeded().catch(() => {});
    await task.click({timeout});
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
        // Open the filter dropdown, then pick the option from the menu that
        // appears. Unlike the old Carbon sidebar, the trigger is always
        // visible — there's no separate expand/collapse step.
        await expect(this.filterSelectButton).toBeVisible({timeout: 10000});
        await this.filterSelectButton.click();

        const menuItem = this.page.getByRole('menuitem', {
          name: option,
          exact: true,
        });
        await expect(menuItem).toBeVisible({timeout: 10000});
        await menuItem.click();

        // Selecting an item closes the dropdown menu on its own.
        await expect(menuItem).toBeHidden({timeout: 10000});

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

  async assertCompletedHeadingVisible() {
    await waitForAssertion({
      assertion: async () => {
        await expect(this.completedHeading).toBeVisible();
      },
      onFailure: async () => {
        // Re-selecting "Completed" here is a no-op when it's already the
        // active filter: filterBy() only waits for the URL to reflect the
        // filter, and the URL already does, so it returns immediately
        // without ever forcing a new fetch. Reload instead, same as
        // openTask() above, to force one.
        console.log(
          'Completed filter not reflected yet, reloading and retrying...',
        );
        await this.reloadPage();
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
