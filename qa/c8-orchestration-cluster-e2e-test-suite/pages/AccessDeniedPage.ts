/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, expect, Locator} from '@playwright/test';

export class AccessDeniedPage {
  readonly page: Page;
  readonly accessDeniedMessage: Locator;

  constructor(page: Page) {
    this.page = page;
    this.accessDeniedMessage = this.page.getByText(
      'You don’t have access to this component',
    );
  }

  async expectAccessDenied(): Promise<void> {
    await expect(this.page).toHaveURL(/\/forbidden/);
    await expect(this.accessDeniedMessage).toBeVisible({timeout: 60000});
  }
}
