/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';

class TaskDetailsPage {
  private page: Page;
  readonly assignToMeButton: Locator;
  readonly completeButton: Locator;
  readonly unassignButton: Locator;
  readonly assignee: Locator;
  readonly completeTaskButton: Locator;
  readonly addVariableButton: Locator;
  readonly detailsPanel: Locator;
  readonly detailsHeader: Locator;
  readonly pendingTaskDescription: Locator;
  readonly pickATaskHeader: Locator;
  readonly emptyTaskMessage: Locator;
  readonly nameInput: Locator;
  readonly addressInput: Locator;
  readonly ageInput: Locator;

  constructor(page: Page) {
    this.page = page;
    this.assignToMeButton = page.getByRole('button', {name: 'Assign to me'});
    this.completeButton = page.getByRole('button', {name: 'Complete'});
    this.unassignButton = page.getByRole('button', {name: 'Unassign'});
    this.assignee = page.getByTestId('assignee');
    this.completeTaskButton = page.getByRole('button', {name: 'Complete Task'});
    this.addVariableButton = page.getByRole('button', {name: 'Add Variable'});
    this.detailsPanel = this.page.getByRole('complementary', {
      name: 'Task details right panel',
    });
    this.detailsHeader = page.getByTitle('Task details header');
    this.pendingTaskDescription = page.getByText('Pending task');
    this.pickATaskHeader = page.getByRole('heading', {
      name: 'Pick a task to work on',
    });
    this.emptyTaskMessage = page.getByRole('heading', {
      name: /task has no variables/i,
    });
    this.nameInput = page.getByLabel('Name*');
    this.addressInput = page.getByLabel('Address*');
    this.ageInput = page.getByLabel('Age');
  }

  async clickAssignToMeButton(): Promise<void> {
    await this.assignToMeButton.click();
  }

  async clickUnassignButton(): Promise<void> {
    await this.unassignButton.click();
  }

  async clickCompleteTaskButton(): Promise<void> {
    await this.completeTaskButton.click();
  }

  async clickAddVariableButton(): Promise<void> {
    await this.addVariableButton.click();
  }
}

export {TaskDetailsPage};
