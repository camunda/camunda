/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

class TaskFormView {
  private page: Page;
  readonly nameInput: Locator;
  readonly addressInput: Locator;
  readonly ageInput: Locator;
  readonly numberInput: Locator;
  readonly incrementButton: Locator;
  readonly decrementButton: Locator;
  readonly dateInput: Locator;
  readonly timeInput: Locator;
  readonly checkbox: Locator;
  readonly selectDropdown: Locator;
  readonly tagList: Locator;
  readonly form: Locator;

  constructor(page: Page) {
    this.page = page;
    this.form = page.getByTestId('embedded-form');
    this.nameInput = this.form.getByLabel('Name*');
    this.addressInput = this.form.getByLabel('Address*');
    this.ageInput = this.form.getByLabel('Age');
    this.numberInput = this.form.getByLabel('Number');
    this.incrementButton = this.form.getByRole('button', {name: 'Increment'});
    this.decrementButton = this.form.getByRole('button', {name: 'Decrement'});
    this.dateInput = this.form.getByPlaceholder('mm/dd/yyyy');
    this.timeInput = this.form.getByPlaceholder('hh:mm --');
    this.checkbox = this.form.getByLabel('Checkbox');
    this.selectDropdown = this.form.getByText('Select').last();
    this.checkbox = this.form.getByLabel('Checkbox');
    this.tagList = this.form.getByPlaceholder('Search');
  }

  async forEachDynamicListItem(
    locator: Locator,
    fn: (value: Locator, index: number, array: Locator[]) => Promise<void>,
  ) {
    const elements = await locator.all();

    for (const element of elements) {
      await fn(element, elements.indexOf(element), elements);
    }
  }
  async fillDatetimeField(name: string, value: string) {
    await this.page.getByRole('textbox', {name}).fill(value);
    await this.page.getByRole('textbox', {name}).press('Enter');
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

  async selectDropdownOption(label: string, value: string) {
    await this.page.getByText(label).click();
    await this.page.getByText(value).click();
  }

  async mapDynamicListItems<MappedValue>(
    locator: Locator,
    fn: (
      value: Locator,
      index: number,
      array: Locator[],
    ) => Promise<MappedValue>,
  ): Promise<Array<MappedValue>> {
    const elements = await locator.all();
    const mapped: Array<MappedValue> = [];

    for (const element of elements) {
      mapped.push(await fn(element, elements.indexOf(element), elements));
    }

    return mapped;
  }
}
export {TaskFormView};
