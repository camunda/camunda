/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {Locator, Page} from '@playwright/test';

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
  }

  async goto(id: string) {
    await this.page.goto(`/${id}`, {
      waitUntil: 'networkidle',
    });
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

    this.addVariableButton.click();
    await this.getNthVariableNameInput(1).fill(name);
    await this.getNthVariableValueInput(1).fill(value);
  }

  async fillDate(date: string) {
    await this.dateInput.click();
    await this.dateInput.fill(date);
    await this.dateInput.press('Enter');
  }

  async enterTime(time: string) {
    await this.timeInput.click();
    await this.page.getByText(time).click();
  }

  async selectDropdownValue(value: string) {
    await this.selectDropdown.click();
    await this.page.getByText(value).click();
  }

  async selectTaglistValues(values: string[]) {
    await this.tagList.click();

    for (const value of values) {
      await this.page.getByText(value, {exact: true}).click();
    }
  }
}
export {TaskDetailsPage};
