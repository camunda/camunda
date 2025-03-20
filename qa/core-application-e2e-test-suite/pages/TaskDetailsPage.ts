/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {sleep} from 'utils/sleep';

function cardinalToOrdinal(numberValue: number): string {
  const realOrderIndex = numberValue.toString();

  if (['11', '12', '13'].includes(realOrderIndex.slice(-2))) {
    return `${realOrderIndex}th`;
  }

  switch (realOrderIndex.slice(-1)) {
    case '1':
      return `${realOrderIndex}st`;
    case '2':
      return `${realOrderIndex}nd`;
    case '3':
      return `${realOrderIndex}rd`;
    default:
      return `${realOrderIndex}th`;
  }
}

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
  readonly variablesTable: Locator;
  readonly nameColumnHeader: Locator;
  readonly valueColumnHeader: Locator;
  readonly form: Locator;
  readonly numberInput: Locator;
  readonly incrementButton: Locator;
  readonly decrementButton: Locator;
  readonly dateInput: Locator;
  readonly timeInput: Locator;
  readonly checkbox: Locator;
  readonly selectDropdown: Locator;
  readonly tagList: Locator;
  readonly detailsInfo: Locator;
  readonly textInput: Locator;
  readonly taskCompletedBanner: Locator;

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
    this.variablesTable = page.getByTestId('variables-table');
    this.nameColumnHeader = this.variablesTable.getByRole('columnheader', {
      name: 'Name',
    });
    this.valueColumnHeader = this.variablesTable.getByRole('columnheader', {
      name: 'Value',
    });
    this.form = page.getByTestId('embedded-form');
    this.numberInput = this.form.getByLabel('Number');
    this.incrementButton = page.getByRole('button', {name: 'Increment'});
    this.decrementButton = page.getByRole('button', {name: 'Decrement'});
    this.dateInput = page.getByPlaceholder('mm/dd/yyyy');
    this.timeInput = page.getByPlaceholder('hh:mm ?m');
    this.checkbox = this.form.getByLabel('Checkbox');
    this.selectDropdown = this.form.getByText('Select').last();
    this.tagList = page.getByPlaceholder('Search');
    this.detailsInfo = page.getByTestId('details-info');
    this.textInput = page.locator('[class="fjs-input"]');
    this.taskCompletedBanner = this.page.getByText('Task completed');
  }

  async clickAssignToMeButton() {
    await this.assignToMeButton.click({timeout: 60000});
  }

  async clickUnassignButton() {
    await this.unassignButton.click();
  }

  async clickCompleteTaskButton() {
    await this.completeTaskButton.click({timeout: 60000});
    await expect(this.taskCompletedBanner).toBeVisible({
      timeout: 200000,
    });
  }

  async clickAddVariableButton() {
    await this.addVariableButton.click({timeout: 60000});
  }

  async replaceExistingVariableValue(values: {name: string; value: string}) {
    const {name, value} = values;
    await this.page.getByTitle(name).clear();
    await this.page.getByTitle(name).fill(value);
  }

  getNthVariableNameInput(nth: number) {
    return this.page.getByRole('textbox', {
      name: `${cardinalToOrdinal(nth)} variable name`,
    });
  }

  getNthVariableValueInput(nth: number) {
    return this.page.getByRole('textbox', {
      name: `${cardinalToOrdinal(nth)} variable value`,
    });
  }

  async addVariable(payload: {name: string; value: string}) {
    const {name, value} = payload;

    await this.clickAddVariableButton();
    await this.getNthVariableNameInput(1).fill(name);
    await this.getNthVariableValueInput(1).fill(value);
  }

  async fillNumber(number: string): Promise<void> {
    await this.numberInput.fill(number);
  }

  async clickIncrementButton(): Promise<void> {
    await this.incrementButton.click();
  }

  async clickDecrementButton(): Promise<void> {
    await this.decrementButton.click();
  }

  async fillDate(date: string): Promise<void> {
    await this.dateInput.click();
    await this.dateInput.fill(date);
    await this.dateInput.press('Enter');
  }

  async enterTime(time: string): Promise<void> {
    await this.timeInput.click();
    await this.page.getByText(time).click();
  }

  async checkCheckbox(): Promise<void> {
    await this.checkbox.check();
  }

  async selectDropdownValue(value: string): Promise<void> {
    await this.selectDropdown.click();
    await this.page.getByText(value).click();
  }

  async clickRadioButton(radioBtnLabel: string): Promise<void> {
    await this.page.getByText(radioBtnLabel).click();
  }

  async checkChecklistBox(label: string): Promise<void> {
    await this.page.getByLabel(label).check();
  }

  async enterTwoValuesInTagList(value1: string, value2: string): Promise<void> {
    await this.tagList.click();
    await this.page.getByText(value1).click();
    await this.page.getByText(value2, {exact: true}).click();
  }

  async clickTextInput(): Promise<void> {
    await this.textInput.click({timeout: 60000});
  }

  async fillTextInput(value: string): Promise<void> {
    await this.textInput.fill(value);
  }
  async priorityAssertion(priority: string): Promise<void> {
    let retryCount = 0;
    const maxRetries = 2;
    while (retryCount < maxRetries) {
      try {
        await expect(this.detailsPanel.getByText(priority)).toBeVisible({
          timeout: 45000,
        });
        return; // Exit the function if the expectation is met
      } catch {
        retryCount++;
        console.log(`Attempt ${retryCount} failed. Retrying...`);
        await this.page.reload();
        await sleep(10000);
      }
    }
    throw new Error(`Active icon not visible after ${maxRetries} attempts.`);
  }

  async taskAssertion(name: string): Promise<void> {
    let retryCount = 0;
    const maxRetries = 2;
    while (retryCount < maxRetries) {
      try {
        await expect(this.detailsInfo.getByText(name)).toBeVisible({
          timeout: 45000,
        });
        return; // Exit the function if the expectation is met
      } catch {
        retryCount++;
        console.log(`Attempt ${retryCount} failed. Retrying...`);
        await this.page.reload();
        await sleep(10000);
      }
    }
    throw new Error(`Active icon not visible after ${maxRetries} attempts.`);
  }

  async assertVariableValue(
    variableName: string,
    variableValue: string,
  ): Promise<void> {
    await expect(this.page.getByTitle(variableName + ' Value')).toHaveValue(
      variableValue,
    );
  }
}
export {TaskDetailsPage};
