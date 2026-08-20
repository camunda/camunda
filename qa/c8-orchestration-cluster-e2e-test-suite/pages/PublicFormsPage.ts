/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page, Locator} from '@playwright/test';

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

  async goToPublicForm(bpmnProcessId: string) {
    await this.page.goto(`/new/${bpmnProcessId}`);
  }
}
export {PublicFormsPage};
