/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

export class OperateOperationPanelPage {
  private page: Page;
  readonly expandButton: Locator;
  readonly collapseButton: Locator;
  readonly operationList: Locator;

  constructor(page: Page) {
    this.page = page;
    this.expandButton = page.getByRole('button', {name: 'Expand Operations'});
    this.collapseButton = page.getByRole('button', {
      name: 'Collapse Operations',
    });
    this.operationList = page.getByTestId('operations-list');
  }

  getAllOperationEntries(): Locator {
    return this.operationList.getByTestId('operations-entry');
  }

  static getOperationEntrySuccess(operationEntry: Locator): Locator {
    return operationEntry.getByText('1 operation succeeded');
  }

  static getOperationID(operationEntry: Locator): Locator {
    return operationEntry.getByTestId('operation-id');
  }

  static getOperationType(operationEntry: Locator): Locator {
    return operationEntry.getByRole('heading');
  }

  async expandOperationIdField(): Promise<void> {
    await this.expandButton.click({timeout: 30000});
  }

  async collapseOperationIdField(): Promise<void> {
    await this.collapseButton.click();
  }
}
