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
  readonly historyTabButton: Locator;
  readonly historyTable: Locator;
  readonly historyTableRow: Locator;
  readonly historyTableOperationTypeHeader: Locator;
  readonly historyTableDetailsHeader: Locator;
  readonly historyTableActorHeader: Locator;
  readonly historyTableDateHeader: Locator;
  readonly historyTableAssignCell: Locator;

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
    this.historyTabButton = page.getByRole('link', {
      name: 'Show task history',
    });
    this.historyTable = page
      .getByTestId('history-tab-content')
      .getByRole('table');
    this.historyTableRow = this.historyTable.getByRole('row');
    this.historyTableOperationTypeHeader = this.historyTable.getByRole(
      'columnheader',
      {
        name: 'Operation type',
      },
    );
    this.historyTableDetailsHeader = this.historyTable.getByRole(
      'columnheader',
      {
        name: 'Details',
      },
    );
    this.historyTableActorHeader = this.historyTable.getByRole('columnheader', {
      name: 'Actor',
    });
    this.historyTableDateHeader = this.historyTable.getByRole('columnheader', {
      name: 'Date',
    });
    this.historyTableAssignCell = this.historyTable.getByRole('cell', {
      name: 'Assign task',
    });
  }

  async clickAssignToMeButton() {
    if (!(await this.assignedToMeText.isVisible())) {
      await expect(this.assignToMeButton).toBeVisible({timeout: 60000});
      await this.assignToMeButton.click({timeout: 60000});
      await expect(this.unassignButton).toBeVisible({timeout: 30000});
    }
  }

  async clickUnassignButton() {
    await expect(this.unassignButton).toBeVisible({timeout: 30000});
    await this.unassignButton.click();
    // Unassigning is processed asynchronously; the Assign-to-me button can take
    // a while to reappear under load, so match the assign path's 60s budget.
    await expect(this.assignToMeButton).toBeVisible({timeout: 60000});
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

  /**
   * Repeatedly press increment until the input shows the target value.
   * Tolerates the form-js number button registering a click as two
   * increments on slow runners.
   */
  async incrementUntilValue(target: string): Promise<void> {
    await this.driveNumberInputToValue(target, 'increment');
  }

  /**
   * Repeatedly press decrement until the input shows the target value.
   */
  async decrementUntilValue(target: string): Promise<void> {
    await this.driveNumberInputToValue(target, 'decrement');
  }

  private async driveNumberInputToValue(
    target: string,
    direction: 'increment' | 'decrement',
  ): Promise<void> {
    const button =
      direction === 'increment' ? this.incrementButton : this.decrementButton;
    const targetNum = Number(target);
    for (let attempt = 0; attempt < 6; attempt++) {
      const currentValue = (await this.numberInput.inputValue()) || '0';
      const currentNum = Number(currentValue);
      if (currentNum === targetNum) {
        return;
      }
      const wrongDirection =
        (direction === 'increment' && currentNum > targetNum) ||
        (direction === 'decrement' && currentNum < targetNum);
      const correctButton = wrongDirection
        ? direction === 'increment'
          ? this.decrementButton
          : this.incrementButton
        : button;
      await correctButton.click({timeout: 60000});
      try {
        await expect(this.numberInput).not.toHaveValue(currentValue, {
          timeout: 5000,
        });
      } catch {
        // If the input value didn't change in 5s the next iteration will
        // re-read it and decide whether another click is needed.
      }
    }
    await expect(this.numberInput).toHaveValue(target);
  }

  async fillDatetimeField(label: string, value: string) {
    const input = this.page.getByRole('textbox', {name: label});
    await expect(input).toBeVisible();
    await input.click();
    // Form-js datetime sub-fields (Date and Time) route input through
    // flatpickr. Setting the value with .fill() updates only the visible
    // text — it does not feed characters through flatpickr's keydown parser,
    // so flatpickr's internal date model is never updated. The visible value
    // therefore passes the toHaveValue check below but is dropped on the
    // component's next re-render and on task completion, leaving the reopened
    // completed task with a blank field. Type the value character by
    // character so flatpickr parses and commits each keystroke, then press
    // Enter to confirm. This is also the only path that works for the Time
    // sub-field, whose <input> renders readonly (making .fill() throw
    // "element is not editable").
    // ControlOrMeta+A is cross-platform (Cmd on macOS, Ctrl elsewhere).
    await this.page.keyboard.press('ControlOrMeta+A');
    await this.page.keyboard.press('Backspace');
    await this.page.keyboard.type(value, {delay: 30});
    await this.page.keyboard.press('Enter');
    await expect(input).toHaveValue(value);
  }

  async checkCheckbox(): Promise<void> {
    // Wait for form-js to clear the post-assignment readonly state first.
    await expect(this.checkbox).not.toHaveAttribute('readonly', {
      timeout: 60000,
    });
    // Toggle via the visible label, not a direct input .check(): a raw input
    // click sets the DOM checked state (so toBeChecked passes) but form-js can
    // fail to serialize it into the completion payload, dropping the value on
    // completion. The label click is the interaction form-js reliably commits
    // (mirrors the radio path, which persists).
    await this.form.getByText('Checkbox', {exact: true}).click();
    await expect(this.checkbox).toBeChecked();
  }

  async selectDropdownValue(value: string): Promise<void> {
    // Wait for form-js to make the select editable (readonly cleared); a
    // readonly select accepts a visual selection but never commits it, so
    // completion drops the value.
    const selectInput = this.form.getByRole('textbox', {name: 'Select'});
    await expect(selectInput).not.toHaveAttribute('readonly', {timeout: 60000});
    await this.selectDropdown.click();
    // Prefer the explicit option role so the click doesn't land on a
    // substring match elsewhere on the page (e.g. the dropdown's own
    // label or the placeholder when the menu hasn't opened yet).
    const option = this.page.getByRole('option', {name: value, exact: true});
    try {
      await option.click({timeout: 5000});
    } catch {
      await this.page.getByText(value).click();
    }
    // The form-js select is a text-input combobox and, like the text fields,
    // commits its value on blur — without it the selection renders but is
    // dropped from the completion payload. Blur, then confirm the committed
    // value before the caller completes the task.
    await selectInput.blur();
    await expect(selectInput).toHaveValue(value);
  }

  async selectDropdownOption(label: string, value: string) {
    await this.page.getByText(label).click();
    await this.page.getByText(value).click();
  }

  async clickRadioButton(label: string): Promise<void> {
    // form-js hides the real radio <input> (custom-styled), so .check() on it
    // fails as "not visible" — click the visible label instead. Gate on the
    // input's readonly clearing so form-js actually records the selection.
    const radio = this.form.getByRole('radio', {name: label});
    await expect(radio).not.toHaveAttribute('readonly', {timeout: 60000});
    await this.form.getByText(label, {exact: true}).click();
    await expect(radio).toBeChecked();
  }

  async checkChecklistBox(label: string): Promise<void> {
    // Scope to the embedded form: an unscoped getByLabel(label) also matches
    // the task nav list, whose accessible name embeds the task title (e.g. a
    // "Confirm ..." task collides with a "Confirm" checkbox). Wait for form-js
    // to clear readonly so the toggle is recorded, then confirm it took.
    const box = this.form.getByRole('checkbox', {name: label});
    await expect(box).not.toHaveAttribute('readonly', {timeout: 60000});
    await box.check();
    await expect(box).toBeChecked();
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
    await sleep(500);
    const locator = this.page.getByLabel(label);
    const elements = await locator.all();
    if (elements.length === 0) {
      throw new Error(
        `No elements found for label "${label}" in the dynamic list`,
      );
    }

    for (const [index, element] of elements.entries()) {
      await this.fillElementWithRetry(element, index, value);
    }
  }

  private async fillElementWithRetry(
    locator: Locator,
    index: number,
    value: string,
  ): Promise<void> {
    let retryCount = 0;
    const maxRetries = 3;
    while (retryCount < maxRetries) {
      try {
        const expectedValue = `${value}${index + 1}`;
        await locator.fill(expectedValue);
        await expect(locator).toHaveValue(expectedValue);
        return;
      } catch {
        retryCount++;
        console.log(`Attempt ${retryCount} failed. Retrying...`);
        await sleep(1000);
        await locator.click();
        await locator.clear();
      }
    }
    throw new Error(
      `${locator} could not be filled with value "${value}" after ${maxRetries} attempts.`,
    );
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
        await expect(input).toHaveValue(expectedValue);
      },
      onFailure: async () => {
        console.log(
          `Assertion for field "${label}" failed, reloading page and retrying...`,
        );
        await this.page.reload();
        await expect(this.form).toBeVisible({timeout: 30000});
      },
    });
  }

  async assertItemChecked(
    label: string,
    timeout: number = 60000,
  ): Promise<void> {
    await expect(this.page.getByLabel(label)).toBeChecked({timeout});
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

  async clickHistoryTab(): Promise<void> {
    await this.historyTabButton.click();
  }

  getHistoryTableRowCount(): Promise<number> {
    return this.historyTableRow.count();
  }

  getHistoryTableAssignCellCount(): Promise<number> {
    return this.historyTableAssignCell.count();
  }

  async unassignReassignToMeAndComplete(): Promise<void> {
    // Unassign from the current assignee
    await this.clickUnassignButton();

    // Assign to the logged-in user and verify assignment
    await this.clickAssignToMeButton();

    // Complete the task, wait for the banner to appear, then disappear
    await expect(this.completeTaskButton).toBeEnabled({timeout: 15000});
    await this.clickCompleteTaskButton();
    await expect(this.taskCompletedBanner).toBeVisible();
    await expect(this.taskCompletedBanner).toBeHidden({timeout: 15000});
  }
}

export {TaskDetailsPage};
