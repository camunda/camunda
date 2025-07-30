/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {sleep} from 'utils/sleep';
import {waitForAssertion} from 'utils/waitForAssertion';

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
  readonly taskCompletedBanner: Locator;
  readonly addDynamicListRowButton: Locator;
  readonly processTab: Locator;
  readonly bpmnDiagram: Locator;
  readonly assignedToMeText: Locator;

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
      name: 'task has no variables',
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
    this.taskCompletedBanner = this.page.getByText('Task completed');
    this.addDynamicListRowButton = page.getByRole('button', {name: 'add new'});
    this.processTab = page.getByRole('link', {
      name: 'show associated bpmn process',
    });
    this.bpmnDiagram = page.getByTestId('diagram');
    this.assignedToMeText = page
      .getByTestId('assignee')
      .getByText('Assigned to me');
  }

  async clickAssignToMeButton() {
    await this.assignToMeButton.click({timeout: 60000});
  }

  async clickUnassignButton() {
    await this.unassignButton.click();
  }

  async clickCompleteTaskButton() {
    await this.completeTaskButton.click({timeout: 60000});
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
    await this.incrementButton.click({timeout: 60000});
  }

  async clickDecrementButton(): Promise<void> {
    await this.decrementButton.click({timeout: 60000});
  }

  async fillDatetimeField(label: string, value: string) {
    const input = this.page.getByRole('textbox', {name: label});
    await expect(input).toBeVisible();
    await input.click();
    await input.fill(value);
    await this.page.keyboard.press('Enter');
    await expect(input).toHaveValue(value);
  }

  async checkCheckbox(): Promise<void> {
    await this.checkbox.check();
  }

  async selectDropdownValue(value: string): Promise<void> {
    await this.selectDropdown.click();
    await this.page.getByText(value).click();
  }

  async selectDropdownOption(label: string, value: string) {
    await this.page.getByText(label).click();
    await this.page.getByText(value).click();
  }

  async clickRadioButton(label: string): Promise<void> {
    await this.page.getByText(label).click();
  }

  async checkChecklistBox(label: string): Promise<void> {
    await this.page.getByLabel(label).check();
  }

  async enterTwoValuesInTagList(value1: string, value2: string): Promise<void> {
    await this.tagList.click();
    await this.page.getByText(value1).click();
    await this.page.getByText(value2, {exact: true}).click();
  }

  async fillTextInput(label: string, value: string): Promise<void> {
    const input = this.page.getByLabel(label, {exact: true});
    const maxRetries = 3;
    let attempt = 0;
    while (attempt < maxRetries) {
      try {
        await input.click({timeout: 120000});
        await input.fill(value);
        await input.blur();
        await expect(input).toHaveValue(value);
        return;
      } catch (error) {
        attempt++;
        console.log(
          `Attempt ${attempt} to fill input "${label}" failed with error: ${error}`,
        );
        if (attempt === maxRetries) {
          throw new Error(
            `Failed to set value "${value}" for label "${label}" after ${maxRetries} attempts.`,
          );
        }
        await sleep(500);
      }
    }
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

  async fillDynamicList(label: string, value: string) {
    const locator = this.page.getByLabel(label);
    const elements = await locator.all();
    if (elements.length === 0) {
      throw new Error(
        `No elements found for label "${label}" in the dynamic list`,
      );
    }

    for (const [index, element] of elements.entries()) {
      const expectedValue = `${value}${index + 1}`;
      await element.fill(expectedValue);

      // Assert that the value was added correctly
      await expect(element).toHaveValue(expectedValue);
    }
  }

  async getDynamicListValues(label: string): Promise<string[]> {
    const locator = this.page.getByLabel(label);
    const elements = await locator.all();
    if (elements.length === 0) {
      throw new Error(`No elements found for label "${label}"`);
    }

    return Promise.all(elements.map((element) => element.inputValue()));
  }

  async addDynamicListRow(): Promise<void> {
    await this.addDynamicListRowButton.click();
  }

  async assertFieldValue(label: string, expectedValue: string): Promise<void> {
    const input = this.page.getByLabel(label, {exact: true});
    await waitForAssertion({
      assertion: async () => {
        const actualValue = input;
        await expect(actualValue).toHaveValue(expectedValue);
      },
      onFailure: async () => {
        console.log(`Retrying assertion for field "${label}"...`);
      },
    });
  }

  async assertItemChecked(label: string): Promise<void> {
    await expect(this.page.getByLabel(label)).toBeChecked();
  }

  async selectTaglistValues(values: string[]) {
    await this.tagList.click();
    for (const value of values) {
      await this.page.getByText(value, {exact: true}).click();
    }
  }

  async clickProcessTab(): Promise<void> {
    await this.processTab.click();
  }
}

export {TaskDetailsPage};
