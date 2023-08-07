/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';

class PublicFormsPage {
  private page: Page;
  readonly nameInput: Locator;
  readonly emailInput: Locator;
  readonly submitButton: Locator;
  readonly successMessage: Locator;

  constructor(page: Page) {
    this.page = page;
    this.nameInput = page.getByLabel('Name');
    this.emailInput = page.getByLabel('Email');
    this.submitButton = page.getByRole('button', {name: 'Submit'});
    this.successMessage = page.getByRole('heading', {
      name: 'Success!',
    });
  }

  async clickSubmitButton() {
    await this.submitButton.click();
  }

  async goToPublicForm(bpmnProcessId: string) {
    await this.page.goto(`/new/${bpmnProcessId}`);
  }
}
export {PublicFormsPage};
