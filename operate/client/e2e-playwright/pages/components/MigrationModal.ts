/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Locator, Page} from '@playwright/test';

class MigrationModal {
  private readonly modal: Locator;
  readonly confirmButton: Locator;

  constructor(page: Page) {
    this.modal = page.getByRole('dialog', {
      name: /migrate process instance versions/i,
    });

    this.confirmButton = this.modal.locator(
      page.getByRole('button', {name: /continue/i}),
    );
  }
}

export default MigrationModal;
