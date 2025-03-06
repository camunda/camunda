/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

class TaskPanelPage {
  readonly assignmentTag: Locator;
  readonly assignToMeButton: Locator;
  readonly availableTasks: Locator;
  readonly completeTaskButton: Locator;
  readonly collapseSidePanelButton: Locator;
  readonly emptyTaskMessage: Locator;
  readonly expandSidePanelButton: Locator;
  private page: Page;
  readonly taskListPageBanner: Locator;
  readonly collapseFilter: Locator;

  constructor(page: Page) {
    this.page = page;
    this.assignmentTag = page.locator(' header > div').nth(2);
    this.assignToMeButton = page.getByRole('button', {name: 'Assign to me'});
    this.availableTasks = page.getByTitle('Available tasks');
    this.completeTaskButton = page.getByRole('button', {name: 'Complete Task'});
    this.collapseSidePanelButton = page.locator(
      'button[aria-controls="task-nav-bar"][aria-expanded="true"]',
    );
    this.emptyTaskMessage = this.emptyTaskMessage = page.getByRole('heading', {
      name: /Task has no variables/i,
    });
    this.expandSidePanelButton = page
      .locator('[aria-label="Filter controls"] li')
      .filter({hasText: 'Expand to show filters'});
    this.taskListPageBanner = page.getByRole('link', {
      name: 'Camunda logo Tasklist',
    });
    this.collapseFilter = page.locator(
      'button[aria-controls="task-nav-bar"][aria-expanded="true"]',
    );
  }

  async assertAssignmentStatus(status: string): Promise<void> {
    await expect(this.assignmentTag).toContainText(status);
  }

  async assignTaskToMe(): Promise<void> {
    await this.filterBy('Unassigned');
    await this.openTask('usertask_to_be_assigned');
    await this.assignToMeButton.click();
  }

  async completeATask(): Promise<void> {
    await this.filterBy('Unassigned');
    await this.openTask('usertask_to_be_assigned');
    await this.assignToMeButton.click();
    await this.completeTaskButton.click();
  }

  async openTask(name: string) {
    await this.availableTasks
      .getByText(name, {exact: true})
      .nth(0)
      .click({timeout: 20000});
  }

  async asssertUnnassignedTaskEmptyMessage(): Promise<void> {
    await expect(this.emptyTaskMessage).toBeVisible();
  }

  async filterBy(
    option:
      | 'All open tasks'
      | 'Unassigned'
      | 'Assigned to me'
      | 'Completed'
      | 'Custom',
  ) {
    await this.expandSidePanelButton.click();
    await this.page.getByRole('link', {name: option, exact: true}).click();
    await this.collapseSidePanelButton.click();
  }

  async clickCollapseFilter(): Promise<void> {
    await this.collapseFilter.click({timeout: 45000});
  }
}

export {TaskPanelPage};
