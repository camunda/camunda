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

    // The parent tasklist layout route (routes/_shadcn/_auth/tasklist/_tasks/
    // route.tsx) does poll this query every 5s (refetchInterval: 5000), so a
    // task that isn't indexed yet should normally show up on its own within a
    // few seconds. A page reload is a strictly faster, deterministic way to
    // force an immediate fresh fetch rather than wait out however much of
    // the next 5s tick remains, and it's also the only thing that helps if
    // polling isn't keeping up (e.g. under CI load) -- no amount of waiting
    // on the existing DOM without either will make a just-created task
    // appear. Retry with a reload in between attempts, same pattern as
    // assertCompletedHeadingVisible below, instead of relying on a single
    // fetch plus Playwright's built-in element wait.
    await waitForAssertion({
      assertion: async () => {
        await expect(task).toBeVisible({timeout});
      },
      onFailure: async () => {
        console.log(
          `Task "${name}" not visible yet, reloading and retrying...`,
        );
        await this.reloadPage();
      },
    });

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
