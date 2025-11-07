/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

export class OperateOperationPanelPage {
  private page: Page;
  readonly expandButton: Locator;
  readonly collapseButton: Locator;
  readonly operationEntry: Locator;
  
  constructor(page: Page) {
    this.page = page;
    this.expandButton = page.getByRole('button', { name: 'Expand Operations' })
    this.collapseButton = page.getByRole('button', { name: 'Collapse Operations' })
    this.operationEntry = page.getByTestId('operations-entry');
  }

   selectLastOperationEntry(): Locator {
    const meow = this.operationEntry.last();
    // await meow.getByTestId('operation-id').last().click( {timeout: 10000} );
    return meow;
  }

   static getOperationEntryProgressBarValue(operationEntry: Locator): number {
    const progressBarValue = operationEntry.getByRole('progressbar').getAttribute('aria-valuenow');
    return Number(progressBarValue);
  }

  static getOperationEntrySuccess(operationEntry: Locator): Locator {
    return operationEntry.getByText('1 operation succeeded');
  }

  async expandOperationIdField(): Promise<void> {
    await this.expandButton.click({timeout: 30000});
  }

  async collapseOperationIdField(): Promise<void> {
    await this.collapseButton.click();
  }

  

}