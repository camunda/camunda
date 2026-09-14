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
  // The element that actually scrolls the virtualized list -- scrolling the
  // page itself does nothing here, and neither does scrolling the wrapper the
  // "Available tasks" title sits on.
  readonly scrollableList: Locator;
  readonly filterSelectButton: Locator;
  private page: Page;
  readonly taskListPageBanner: Locator;
  readonly completedHeading: Locator;

  constructor(page: Page) {
    this.page = page;
    this.availableTasks = page.getByTitle('Available tasks');
    this.taskCards = this.availableTasks.locator('article');
    this.scrollableList = this.availableTasks.getByTestId('scrollable-list');
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

    await this.waitForTaskCard(task, name, timeout);

    await task.scrollIntoViewIfNeeded().catch(() => {});
    await task.click({timeout});
  }

  /**
   * Waits for a card matching `name` (a task name or the process name below
   * it) to be rendered in the available-tasks list. Use this instead of
   * asserting on the card directly: the list is virtualized, so a card outside
   * the rendered window has to be scrolled to before it exists in the DOM.
   */
  async assertTaskCardVisible(name: string, options: {timeout?: number} = {}) {
    const card = this.availableTasks.getByText(name, {exact: true}).nth(0);
    await this.waitForTaskCard(card, name, options.timeout ?? 10000);
  }

  private async waitForTaskCard(
    task: Locator,
    name: string,
    timeout: number,
  ): Promise<void> {
    // The available-tasks list is virtualized: only the cards around the
    // current scroll offset exist in the DOM, and the next (older) page is
    // pulled in via fetchNextPage as the end of the loaded range is scrolled
    // into view. So a task outside that window has to be *scrolled to* before
    // it can be asserted on -- waiting on the locator alone, or reloading,
    // only ever re-renders the same handful of cards at the top of the list.
    // Under the parallel nightly load both cases occur: a task created in
    // beforeAll is pushed down by newer ones, and a task that was just created
    // or completed lands at the top of these newest-first lists only once it
    // has been indexed. walkListFor() covers both -- it walks the list from
    // the top to the end, then starts over from the top, so a task that shows
    // up at the top mid-walk is picked up on the next pass without the reload
    // that would throw away the pages walked so far.
    await waitForAssertion({
      assertion: async () => {
        await this.scrollListToTop();
        await this.walkListFor(task, timeout);
        await expect(task).toBeVisible({timeout: 5000});
      },
      onFailure: async () => {
        // Nothing in the list matched: force a fresh fetch in case the route's
        // 5s poll is not keeping up under CI load.
        console.log(
          `Task "${name}" not visible yet, reloading and retrying...`,
        );
        await this.reloadPage();
      },
    });
  }

  /**
   * Scrolls the virtualized task list until `task` is rendered or `timeout`
   * runs out. Each round scrolls only as far as the last rendered card, so the
   * list is never scrolled past cards that have not been fetched yet; once the
   * end of the list is reached the walk restarts from the top.
   */
  private async walkListFor(task: Locator, timeout: number): Promise<void> {
    const deadline = Date.now() + timeout;
    let idleRounds = 0;

    while (Date.now() < deadline) {
      if ((await task.count()) > 0) {
        return;
      }
      if ((await this.taskCards.count()) === 0) {
        // List is still loading, or there are no tasks at all.
        await sleep(500);
        continue;
      }

      const offsetBefore = await this.listScrollOffset();
      await this.taskCards
        .last()
        .scrollIntoViewIfNeeded()
        .catch(() => {});

      if ((await this.listScrollOffset()) > offsetBefore) {
        idleRounds = 0;
        continue;
      }

      // The list did not move: either the next page is still being fetched or
      // this is the end of the list. Wait for the fetch, and after a few idle
      // rounds treat it as the end and walk the list again from the top, where
      // anything indexed in the meantime will have landed.
      idleRounds++;
      if (idleRounds > 3) {
        await this.scrollListToTop();
        idleRounds = 0;
      }
      await sleep(500);
    }
  }

  private async scrollListToTop(): Promise<void> {
    await this.scrollableList
      .evaluate((list) => {
        list.scrollTop = 0;
      })
      .catch(() => {});
  }

  private async listScrollOffset(): Promise<number> {
    return await this.scrollableList
      .evaluate((list) => list.scrollTop)
      .catch(() => 0);
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
