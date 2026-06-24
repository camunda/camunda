/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';

export class IdentityAuditLogPage {
  private page: Page;
  readonly auditLogTable: Locator;
  readonly auditLogHeading: Locator;
  readonly actorFilter: Locator;
  readonly resetFiltersButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.auditLogTable = page.getByRole('table');
    this.auditLogHeading = page.getByRole('heading', {
      name: /operations log/i,
    });
    this.actorFilter = page.getByRole('textbox', {name: /^actor$/i});
    this.resetFiltersButton = page.getByRole('button', {
      name: /reset filters/i,
    });
  }

  async navigateToAuditLog(): Promise<void> {
    await this.page.goto(relativizePath(Paths.operationsLog()));
  }
}
