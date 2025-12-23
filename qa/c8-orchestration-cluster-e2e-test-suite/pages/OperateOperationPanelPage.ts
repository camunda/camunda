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
  readonly collapseButton: Locator;
  readonly operationList: Locator;
  readonly operationsPanel: Locator;
  readonly latestOperationEntry: Locator;
  readonly latestOperationEntryBeforeCompletion: Locator;
  readonly latestOperationLink: Locator;
  readonly latestOperationMigrateHeading: Locator;
  readonly latestOperationProgressBar: Locator;
  readonly operationSuccessMessage: Locator;
  readonly collapsedOperationsPanel: Locator;
  readonly expandOperationsButton: Locator;
  readonly inProgressBar: Locator;

  constructor(page: Page) {
    this.page = page;
    this.collapseButton = page.getByRole('button', {
      name: 'Collapse Operations',
    });
    this.operationList = page.getByTestId('operations-list');
    this.operationsPanel = page.getByRole('region', {
      name: 'Operations',
    });
    this.latestOperationEntry = this.operationList
      .getByRole('listitem')
      .first();
    this.latestOperationEntryBeforeCompletion = this.operationList
      .getByRole('listitem')
      .last();
    this.latestOperationLink = page.getByTestId('operation-id').first();
    this.latestOperationMigrateHeading = this.latestOperationEntry.getByRole(
      'heading',
      {name: 'Migrate'},
    );
    this.latestOperationProgressBar =
      this.latestOperationEntry.getByRole('progressbar');
    this.operationSuccessMessage = page
      .getByText(/\d+ operations? succeeded/)
      .first();
    this.collapsedOperationsPanel = page.getByTestId('collapsed-panel');
    this.expandOperationsButton = page.getByRole('button', {
      name: 'Expand Operations',
    });
    this.inProgressBar = this.operationList.locator(
      '[role="progressbar"][aria-busy="true"]',
    );
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
    await this.expandOperationsButton.click({timeout: 30000});
  }

  async collapseOperationIdField(): Promise<void> {
    await this.collapseButton.click();
  }

  async getMigrationOperationId(): Promise<string> {
    const operationEntry = this.getAllOperationEntries()
      .filter({hasText: /^Migrate/i})
      .first();

    return await OperateOperationPanelPage.getOperationID(
      operationEntry,
    ).innerText();
  }

  getMigrationOperationEntry(successCount: number): Locator {
    const operationText = successCount === 1 ? 'operation' : 'operations';
    return this.getAllOperationEntries()
      .filter({hasText: 'Migrate'})
      .filter({hasText: `${successCount} ${operationText} succeeded`});
  }

  getModificationOperationEntry(successCount: number): Locator {
    const operationText = successCount === 1 ? 'operation' : 'operations';
    return this.getAllOperationEntries()
      .filter({hasText: 'Modify'})
      .filter({hasText: `${successCount} ${operationText} succeeded`});
  }

  getRetryOperationEntry(successCount: number): Locator {
    const retryText = successCount === 1 ? 'retry' : 'retries';
    return this.getAllOperationEntries()
      .filter({hasText: 'Retry'})
      .filter({hasText: `${successCount} ${retryText} succeeded`})
      .first();
  }

  getCancelOperationEntry(successCount: number): Locator {
    const operationText = successCount === 1 ? 'operation' : 'operations';
    return this.getAllOperationEntries()
      .filter({hasText: 'Cancel'})
      .filter({hasText: `${successCount} ${operationText} succeeded`});
  }

  async clickOperationLink(operationEntry: Locator): Promise<void> {
    await OperateOperationPanelPage.getOperationID(operationEntry).click({
      timeout: 30000,
    });
  }

  async expandOperationsPanel(): Promise<void> {
    const isCollapsed = await this.collapsedOperationsPanel.isVisible();
    if (isCollapsed) {
      await this.expandOperationsButton.click();
      await this.operationList.waitFor({state: 'visible', timeout: 10000});
    }
  }

  async waitForOperationToComplete(): Promise<void> {
    try {
      await expect(this.inProgressBar).toBeVisible({timeout: 5000});
      await expect(this.inProgressBar).not.toBeVisible({timeout: 120000});
    } catch {
      console.log(
        'Progress bar did not appear or disappeared too quickly - operation likely completed fast',
      );
    }
  }

  async collapseOperationsPanel(): Promise<void> {
    const isVisible = await this.operationsPanel.isVisible();
    if (isVisible) {
      await this.collapseButton.click();
    }
  }

  get operationsList(): Locator {
    return this.operationList;
  }
}
