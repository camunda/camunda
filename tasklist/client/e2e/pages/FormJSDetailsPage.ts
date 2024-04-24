/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Locator, Page} from '@playwright/test';

class FormJSDetailsPage {
  private page: Page;
  readonly completeTaskButton: Locator;
  readonly addVariableButton: Locator;
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
  readonly dynamicListAddnewBtn: Locator;

  constructor(page: Page) {
    this.page = page;
    this.form = page.getByTestId('embedded-form');
    this.completeTaskButton = page.getByRole('button', {name: 'Complete Task'});
    this.nameInput = page.getByLabel('Name*');
    this.addressInput = page.getByLabel('Address*');
    this.ageInput = page.getByLabel('Age');
    this.numberInput = this.form.getByLabel('Number');
    this.incrementButton = page.getByRole('button', {name: 'Increment'});
    this.decrementButton = page.getByRole('button', {name: 'Decrement'});
    this.dateInput = page.getByPlaceholder('mm/dd/yyyy');
    this.timeInput = page.getByPlaceholder('hh:mm ?m');
    this.checkbox = this.form.getByLabel('Checkbox');
    this.selectDropdown = this.form.getByText('Select').last();
    this.checkbox = this.form.getByLabel('Checkbox');
    this.tagList = page.getByPlaceholder('Search');
    this.dynamicListAddnewBtn = page.getByRole('button', {name: /add new/i});
  }
  async fillDate(date: string) {
    await this.dateInput.click();
    await this.dateInput.fill(date);
    await this.dateInput.press('Enter');
  }

  async fillTextField(label: string, value: string) {
    const locator = await this.getLocatorByLabel(label);
    await locator.waitFor();
    await locator.fill(value);
  }

  async fillTextFields(label: string, value: string, index: number) {
    for (let i = 0; i < index; i++) {
      const locator: Locator = await this.page.getByLabel(label).nth(i);
      await locator.fill(value + (i + 1));
    }
  }
  async fillDateField(label: string, value: string) {
    const locator = await this.getLocatorByLabel(label);
    await locator.click();
    await locator.fill(value);
    await locator.press('Enter');
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

  getLocatorByLabel(label: string) {
    return this.page.getByLabel(label);
  }

  getLocatorByText(label: string) {
    return this.page.getByText(label);
  }

  async selectValueFromDropdown(label: string, value: string) {
    await (await this.getLocatorByText(label)).click();
    await this.page.getByText(value).click();
  }

  async getLocatorsByLabel(label: string, index: number) {
    let labelValues: string[] = new Array();
    var labelVal;
    for (let i = 0; i < index; i++) {
      labelVal = labelValues.push(
        await (await this.getLocatorByLabel(label)).nth(i).inputValue(),
      );
    }
    return labelValues;
  }
}
export {FormJSDetailsPage};
