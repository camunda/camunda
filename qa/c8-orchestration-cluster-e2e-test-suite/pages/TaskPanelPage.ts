/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';
import {waitForAssertion} from 'utils/waitForAssertion';
import {expect} from '@playwright/test';

class TaskPanelPage {
  readonly availableTasks: Locator;
  readonly collapseSidePanelButton: Locator;
  readonly expandSidePanelButton: Locator;
  private page: Page;
  readonly taskListPageBanner: Locator;
  readonly collapseFilter: Locator;
  readonly completedHeading: Locator;

  constructor(page: Page) {
    this.page = page;
    this.availableTasks = page.getByTitle('Available tasks');
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
    // V1/V2 mode difference:
    // V1: displays process name ("User registration")
    // V2: always displays process ID ("user_registration") regardless of task name

    // Mapping of processes names to process IDs for V2 mode compatibility
    const processNameToIdMapping: Record<string, string> = {
      'User registration': 'user_registration',
      'Some user activity': 'usertask_to_be_completed',
      'User registration with vars': 'user_registration_with_vars',
      'User Task with form rerender 1': 'user_task_with_form_rerender_1',
      'User Task with form rerender 2': 'user_task_with_form_rerender_2',
      'Date and Time Task': 'Date_and_Time_Task',
      'Checkbox Task': 'Checkbox_User_Task',
      'Select User Task': 'Select',
      'Radio Button Task': 'Radio_Button_User_Task',
      'Checklist User Task': 'Checklist_Task',
      'Tag list Task': 'Tag_List_Task',
    };

    const processId = processNameToIdMapping[name] || name;

    const winner = await Promise.race([
      this.availableTasks
        .getByText(name, {exact: true})
        .nth(0)
        .waitFor({state: 'visible', timeout: 5000})
        .then(() => 'v1'),
      this.availableTasks
        .getByText(processId, {exact: true})
        .nth(0)
        .waitFor({state: 'visible', timeout: 5000})
        .then(() => 'v2'),
    ]).catch(() => {
      console.log('Failed to open task with name:', name);
      throw new Error(`Task not found: ${name}`);
    });

    if (winner === 'v1') {
      await this.availableTasks.getByText(name, {exact: true}).nth(0).click();
    } else {
      await this.availableTasks
        .getByText(processId, {exact: true})
        .nth(0)
        .click();
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
    await this.expandSidePanelButton.click();
    await this.page.getByRole('link', {name: option, exact: true}).click();

    const expectedSegment =
      option === 'All open tasks'
        ? 'all-open'
        : option === 'Assigned to me'
          ? 'assigned-to-me'
          : option.toLowerCase().replace(/\s+/g, '-');

    await expect(this.page).toHaveURL(new RegExp(`${expectedSegment}`));

    await this.collapseSidePanelButton.click();
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
}

export {TaskPanelPage};
