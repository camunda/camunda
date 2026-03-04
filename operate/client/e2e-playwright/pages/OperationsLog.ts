/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page, Locator} from '@playwright/test';
import {convertToQueryString} from '../utils/convertToQueryString';

export class OperationsLog {
  private page: Page;
  readonly operationsLogTable: Locator;
  readonly filtersPanel: Locator;
  readonly processInstanceKeyFilter: Locator;
  readonly resetFiltersButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.operationsLogTable = page.getByRole('table');
    this.filtersPanel = page.getByRole('region', {name: /filter/i});
    this.processInstanceKeyFilter = page.getByLabel('Process instance key');
    this.resetFiltersButton = page.getByRole('button', {
      name: /reset filters/i,
    });
  }

  async gotoOperationsLogPage(options?: {
    searchParams?: Parameters<typeof convertToQueryString>[0];
    gotoOptions?: Parameters<Page['goto']>[1];
  }) {
    const {searchParams, gotoOptions} = options ?? {};
    if (searchParams) {
      await this.page.goto(
        `/operate/operations-log?${convertToQueryString(searchParams)}`,
        gotoOptions,
      );
    } else {
      await this.page.goto('/operate/operations-log', gotoOptions);
    }
  }
}
