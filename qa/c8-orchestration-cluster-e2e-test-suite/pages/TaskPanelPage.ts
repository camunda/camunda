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
    this.filterSelectButton = page.getByRole('button', {
      name: 'Filters',
      exact: true,
    });
    this.taskListPageBanner = page
      .getByRole('navigation', {name: 'Camunda context'})
      .getByRole('link', {name: 'Tasklist', exact: true});
    this.completedHeading = this.filterSelectButton.getByText('Completed', {
      exact: true,
    });
  }

  async openTask(name: string, options: {timeout?: number} = {}) {
    const timeout = options.timeout ?? 10000;
    const task = this.taskCardByText(name);

    await waitForAssertion({
      assertion: async () => {
        await this.scrollListToTop();
        await this.walkListFor(task, timeout);
        await task.getByRole('link').click({timeout});
        await expect(this.page).toHaveURL(/\/tasklist\/[^/?]+/);
      },
      onFailure: async () => {
        await this.reloadPage();
      },
    });
  }

  async assertTaskCardVisible(
    name: string,
    options: {timeout?: number} = {},
  ): Promise<void> {
    const card = this.taskCardByText(name);
    await this.waitForTaskCard(card, name, options.timeout ?? 10000);
  }

  private taskCardByText(text: string): Locator {
    return this.taskCards
      .filter({has: this.page.getByText(text, {exact: true})})
      .first();
  }

  private async waitForTaskCard(
    task: Locator,
    name: string,
    timeout: number,
  ): Promise<void> {
    // Only the current task-list viewport exists in the DOM. Walk the actual
    // scroll container so older pages load before falling back to a reload.
    await waitForAssertion({
      assertion: async () => {
        await this.scrollListToTop();
        await this.walkListFor(task, timeout);
        await expect(task).toBeVisible({timeout: 5000});
      },
      onFailure: async () => {
        console.log(
          `Task "${name}" not visible yet, reloading and retrying...`,
        );
        await this.reloadPage();
      },
    });
  }

  private async walkListFor(task: Locator, timeout: number): Promise<void> {
    const deadline = Date.now() + timeout;
    let idleRounds = 0;

    while (Date.now() < deadline) {
      if ((await task.count()) > 0) {
        return;
      }
      if ((await this.taskCards.count()) === 0) {
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
        await expect(this.filterSelectButton).toBeVisible({timeout: 10000});
        await this.filterSelectButton.click();

        const menuItem = this.page.getByRole('menuitem', {
          name: option,
          exact: true,
        });
        await expect(menuItem).toBeVisible({timeout: 10000});
        await menuItem.click();
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
