/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

class TasksFilterModal {
  readonly modal: Locator;
  readonly advancedFiltersToggle: Locator;
  readonly businessIdField: Locator;
  constructor(page: Page) {
    this.modal = page.getByRole('dialog', {name: 'Custom filters modal'});

    // NOTE: Locating the actual switch would be superior, but not clickable in Carbon....
    this.advancedFiltersToggle = this.modal.locator(
      'label[for="toggle-advanced-filters"]',
    );
    this.businessIdField = this.modal.getByRole('textbox', {
      name: 'Business ID',
    });
  }
}

export {TasksFilterModal};
