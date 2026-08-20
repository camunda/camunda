/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page, Locator} from '@playwright/test';

export class BatchOperations {
  private page: Page;
  readonly batchOperationsTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.batchOperationsTable = page.getByRole('table');
  }

  async gotoBatchOperationsPage(options?: Parameters<Page['goto']>[1]) {
    await this.page.goto('/operate/batch-operations', options);
  }
}
