/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Locator, Page} from '@playwright/test';

export class DeleteResourceModal {
  private readonly modal: Locator;
  readonly confirmCheckbox: Locator;
  readonly confirmButton: Locator;

  constructor(page: Page, {name}: {name: string | RegExp}) {
    this.modal = page.getByRole('dialog', {
      name,
    });

    this.confirmButton = this.modal.locator(
      page.getByRole('button', {name: 'Delete'}),
    );

    this.confirmCheckbox = this.modal.locator(page.getByRole('checkbox'));
  }
}
