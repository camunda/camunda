/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

export class OperateBatchOperationsPage {
  private page: Page;
  readonly heading: Locator;
  readonly table: Locator;
  readonly tableRows: Locator;
  readonly emptyStateMessage: Locator;
  readonly forbiddenMessage: Locator;
  readonly operationColumnHeader: Locator;
  readonly batchStateColumnHeader: Locator;
  readonly actorColumnHeader: Locator;
  readonly startDateColumnHeader: Locator;
  readonly itemsColumnHeader: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.getByRole('heading', {
      name: 'Batch Operations',
      level: 3,
    });
    this.table = page.getByRole('table');
    this.tableRows = this.table.getByRole('row');
    this.emptyStateMessage = page.getByText('No batch operations found');
    this.forbiddenMessage = page.getByText(/403 - You do not have permission/);
    this.operationColumnHeader = page.getByRole('columnheader', {
      name: 'Sort by Operation',
    });
    this.batchStateColumnHeader = page.getByRole('columnheader', {
      name: 'Sort by Batch state',
    });
    this.actorColumnHeader = page.getByRole('columnheader', {
      name: 'Sort by Actor',
    });
    this.startDateColumnHeader = page.getByRole('columnheader', {
      name: 'Sort by Start date',
    });
    this.itemsColumnHeader = page.getByRole('columnheader', {name: 'Items'});
  }

  async goto(options?: {searchParams?: Record<string, string>}): Promise<void> {
    const base = `${process.env.CORE_APPLICATION_URL}/operate/batch-operations`;
    if (options?.searchParams) {
      const params = new URLSearchParams(options.searchParams);
      await this.page.goto(`${base}?${params.toString()}`);
    } else {
      await this.page.goto(base);
    }
  }

  getRowLinkByKey(batchOperationKey: string): Locator {
    return this.page.locator(`a[href$="/${batchOperationKey}"]`);
  }

  async clickFirstRowLink(): Promise<void> {
    await this.table.getByRole('link').first().click();
  }
}
