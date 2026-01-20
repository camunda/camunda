/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page, Locator} from '@playwright/test';

type FilterParam =
  | 'all-open'
  | 'unassigned'
  | 'assigned-to-me'
  | 'completed'
  | 'custom';
type SortByParam = 'creation' | 'follow-up' | 'due' | 'completion' | 'priority';

class TasksPage {
  private page: Page;
  readonly assignToMeButton: Locator;
  readonly unassignButton: Locator;
  readonly assignee: Locator;
  readonly completeTaskButton: Locator;
  readonly detailsPanel: Locator;
  readonly detailsHeader: Locator;
  readonly detailsNav: Locator;
  readonly pickATaskHeader: Locator;
  readonly emptyTaskMessage: Locator;
  readonly availableTasks: Locator;
  readonly expandSidePanelButton: Locator;
  readonly collapseSidePanelButton: Locator;
  readonly addCustomFilterButton: Locator;
  readonly bpmnDiagram: Locator;
  readonly taskCompletionNotification: Locator;

  constructor(page: Page) {
    this.page = page;
    this.assignToMeButton = page.getByRole('button', {name: 'Assign to me'});
    this.unassignButton = page.getByRole('button', {name: 'Unassign'});
    this.assignee = page.getByTestId('assignee');
    this.completeTaskButton = page.getByRole('button', {name: 'Complete Task'});
    this.detailsPanel = this.page.getByRole('complementary', {
      name: 'Task details right panel',
    });
    this.detailsHeader = page.getByTitle('Task details header');
    this.detailsNav = page.getByLabel('Task Details Navigation');
    this.pickATaskHeader = page.getByRole('heading', {
      name: 'Pick a task to work on',
    });
    this.emptyTaskMessage = page.getByRole('heading', {
      name: /task has no variables/i,
    });
    this.availableTasks = page.getByTitle('Available tasks');
    this.expandSidePanelButton = page.getByRole('button', {
      name: 'Expand to show filters',
    });
    this.collapseSidePanelButton = page.getByRole('button', {
      name: 'Collapse',
    });
    this.addCustomFilterButton = page.getByRole('button', {
      name: 'New filter',
    });
    this.bpmnDiagram = page.getByTestId('diagram');
    this.taskCompletionNotification = page.getByText('Task completed');
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

  async scrollToLastTask(name: string) {
    await this.page.getByText(name).last().scrollIntoViewIfNeeded();
  }

  async scrollToFirstTask(name: string) {
    await this.page.getByText(name).first().scrollIntoViewIfNeeded();
  }

  async gotoTaskDetails(id: string) {
    await this.page.goto(`/${id}`, {waitUntil: 'networkidle'});
  }

  async gotoTaskDetailsProcessTab(id: string) {
    await this.page.goto(`/${id}/process`, {waitUntil: 'networkidle'});
  }

  async gotoTaskDetailsHistoryTab(id: string) {
    await this.page.goto(`/${id}/history`, {waitUntil: 'networkidle'});
  }
}

export {TasksPage};
