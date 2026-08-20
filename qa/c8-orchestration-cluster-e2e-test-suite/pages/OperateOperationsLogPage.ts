/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

export class OperateOperationsLogPage {
  private page: Page;
  readonly operationsLogTable: Locator;
  readonly operationsLogTableRows: Locator;
  readonly operationsLogHeading: Locator;
  readonly processInstanceKeyFilter: Locator;
  readonly processInstanceCells: Locator;
  readonly operationTypeColumnHeader: Locator;
  readonly entityTypeColumnHeader: Locator;
  readonly entityKeyColumnHeader: Locator;
  readonly actorColumnHeader: Locator;
  readonly dateColumnHeader: Locator;
  readonly resetFiltersButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.operationsLogTable = page.getByRole('table');
    this.operationsLogTableRows = this.operationsLogTable.getByRole('row');
    this.operationsLogHeading = page.getByRole('heading', {
      name: /operations log/i,
    });
    this.processInstanceKeyFilter = page.getByLabel('Process instance key');
    this.processInstanceCells = this.operationsLogTable.getByRole('cell', {
      name: /process instance/i,
    });
    this.operationTypeColumnHeader = page.getByRole('columnheader', {
      name: 'Operation type',
    });
    this.entityTypeColumnHeader = page.getByRole('columnheader', {
      name: 'Entity type',
    });
    this.entityKeyColumnHeader = page.getByRole('columnheader', {
      name: 'Entity key',
    });
    this.actorColumnHeader = page.getByRole('columnheader', {name: 'Actor'});
    this.dateColumnHeader = page.getByRole('columnheader', {name: 'Date'});
    this.resetFiltersButton = page.getByRole('button', {
      name: /reset filters/i,
    });
  }

  async gotoOperationsLogPage(options?: {
    searchParams?: Record<string, string>;
  }): Promise<void> {
    const {searchParams} = options ?? {};
    if (searchParams) {
      const params = new URLSearchParams(searchParams);
      await this.page.goto(
        `${process.env.CORE_APPLICATION_URL}/operate/operations-log?${params.toString()}`,
      );
    } else {
      await this.page.goto(
        `${process.env.CORE_APPLICATION_URL}/operate/operations-log`,
      );
    }
  }

  async getRowCount(): Promise<number> {
    return this.operationsLogTableRows.count();
  }
}
