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
  }
  async fillDate(date: string) {
    await this.dateInput.click();
    await this.dateInput.fill(date);
    await this.dateInput.press('Enter');
  }

  async fillTextFields(label: string, value: string, index: number) {
    for (let i = 0; i < index; i++) {
      const locator: Locator = this.page.getByLabel(label).nth(i);
      await locator.fill(value + (i + 1));
    }
  }
  async fillDateField(label: string, value: string) {
    const field = this.page.getByLabel(label);
    await field.click();
    await field.fill(value);
    await field.press('Enter');
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

  async selectValueFromDropdown(label: string, value: string) {
    await this.page.getByText(label).click();
    await this.page.getByText(value).click();
  }

  async getLocatorsByLabel(label: string, index: number) {
    const labelValues: string[] = [];
    for (let i = 0; i < index; i++) {
      labelValues.push(await this.page.getByLabel(label).nth(i).inputValue());
    }
    return labelValues;
  }
}
export {FormJSDetailsPage};
