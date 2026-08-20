/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page, Locator} from '@playwright/test';

export class BatchOperation {
  private page: Page;
  readonly batchItemsTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.batchItemsTable = page.getByRole('table');
  }

  async gotoBatchOperationPage(
    batchOperationKey: string,
    options?: Parameters<Page['goto']>[1],
  ) {
    await this.page.goto(
      `/operate/batch-operations/${batchOperationKey}`,
      options,
    );
  }
}
