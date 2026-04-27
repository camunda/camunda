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
  readonly collapseSidePanelButton: Locator;
  readonly expandSidePanelButton: Locator;
  private page: Page;
  readonly taskListPageBanner: Locator;
  readonly collapseFilter: Locator;
  readonly completedHeading: Locator;

  constructor(page: Page) {
    this.page = page;
    this.availableTasks = page.getByTitle('Available tasks');
    this.taskCards = this.availableTasks.locator('article');
    this.collapseSidePanelButton = page.locator(
      'button[aria-controls="task-nav-bar"][aria-expanded="true"]',
    );
    this.expandSidePanelButton = page
      .locator('[aria-label="Filter controls"] li')
      .filter({hasText: 'Expand to show filters'});
    this.taskListPageBanner = page.getByRole('link', {
      name: 'Camunda logo Tasklist',
    });
    this.collapseFilter = page.locator(
      'button[aria-controls="task-nav-bar"][aria-expanded="true"]',
    );
    this.completedHeading = page.getByRole('heading', {
      name: 'completed',
    });
  }

  async openTask(name: string) {
    await this.availableTasks
      .getByText(name, {exact: true})
      .nth(0)
      .click({timeout: 10000});
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
        const link = this.page.getByRole('link', {name: option, exact: true});
        if (!(await link.isVisible())) {
          await expect(this.expandSidePanelButton).toBeVisible();
          await this.expandSidePanelButton.click();
        }
        await expect(link).toBeVisible({timeout: 10000});
        await link.click();

        const expectedSegment =
          option === 'All open tasks'
            ? 'all-open'
            : option === 'Assigned to me'
              ? 'assigned-to-me'
              : option.toLowerCase().replace(/\s+/g, '-');

        await expect(this.page).toHaveURL(new RegExp(`${expectedSegment}`), {
          timeout: 15000,
        });
        await this.collapseSidePanelButton.click();
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
        await expect(this.completedHeading).toBeVisible();
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
}

export {TaskPanelPage};
