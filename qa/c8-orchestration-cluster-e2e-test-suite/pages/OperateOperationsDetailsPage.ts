/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

export class OperateOperationsDetailsPage {
  private page: Page;
  private tileElement: Locator;
  readonly state: Locator;
  readonly summaryOfItems: Locator;

  constructor(page: Page) {
    this.page = page;
    this.tileElement = this.page.locator('.cds--tile');
    this.state = this.tileElement
      .filter({hasText: 'State'})
      .getByRole('status', {name: /Batch operation status:/});
    this.summaryOfItems = this.tileElement
      .filter({hasText: 'Summary of items'})
      .getByRole('status');
  }

  async getBatchOperationStatus(): Promise<string> {
    return await this.state.innerText();
  }

  async getBatchOperationId(): Promise<string> {
    const url = this.page.url();

    const match = url.match(/\/batch-operations\/([^/?]+)/);
    if (match && match[1]) {
      const batchOperationId = match[1];
      return batchOperationId;
    }

    throw new Error(`Could not extract batch operation ID from URL: ${url}`);
  }
}
