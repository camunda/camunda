/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

class MoveModificationModal {
  private readonly modal: Locator;
  readonly confirmButton: Locator;

  constructor(page: Page) {
    this.modal = page.getByRole('dialog', {
      name: /process instance batch move mode/i,
    });

    this.confirmButton = this.modal.locator(
      page.getByRole('button', {name: /continue/i}),
    );
  }
}

export default MoveModificationModal;
