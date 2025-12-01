/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';
import {OperationEntry} from 'utils/getNewOperationIds';

export class OperateOperationPanelPage {
  private page: Page;
  readonly expandButton: Locator;
  readonly collapseButton: Locator;
  readonly operationList: Locator;
  readonly expandedOperationPanel: Locator;
  readonly collapsedOperationsPanel: Locator;
  beforeOperationOperationPanelEntries: OperationEntry[];
  afterOperationOperationPanelEntries: OperationEntry[];

  constructor(page: Page) {
    this.page = page;
    this.expandButton = page
      .getByLabel('Operations')
      .getByRole('button', {name: 'Expand Operations'});
    this.operationList = page.getByTestId('operations-list');
    this.expandedOperationPanel = page
      .getByLabel('Operations')
      .getByTestId('expanded-panel');
    this.collapseButton = this.expandedOperationPanel.getByRole('button', {
      name: 'Collapse Operations',
    });
    this.collapsedOperationsPanel = page.getByTestId('collapsed-panel');
    this.beforeOperationOperationPanelEntries = [];
    this.afterOperationOperationPanelEntries = [];
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

  async getMigrationOperationId(): Promise<string> {
    const operationEntry = this.getAllOperationEntries()
      .filter({hasText: /^Migrate/i})
      .first();

    return await OperateOperationPanelPage.getOperationID(
      operationEntry,
    ).innerText();
  }

  async clickCollapseButton(): Promise<void> {
    await this.collapseButton.click();
  }

  async clickExpandButton(): Promise<void> {
    await this.expandButton.click();
  }

  async collapseOperationsPanel(): Promise<boolean> {
    const isExpanded = await this.expandedOperationPanel.isVisible();
    if (isExpanded) {
      await this.clickCollapseButton();
      await this.collapsedOperationsPanel.waitFor({state: 'visible'});
    }
    return isExpanded;
  }

  async expandOperationsPanel(): Promise<boolean> {
    const isCollapsed = await this.collapsedOperationsPanel.isVisible();
    if (isCollapsed) {
      await this.clickExpandButton();
      await this.expandedOperationPanel.waitFor({
        state: 'visible',
        timeout: 10000,
      });
    }
    return isCollapsed;
  }

  async operationIdsEntries(): Promise<{id: string; type: string}[]> {
    const wasCollapsed = await this.expandOperationsPanel();
    const operationEntries = this.getAllOperationEntries();
    const operationIds: OperationEntry[] = [];

    const count = await operationEntries.count();
    for (let i = 0; i < count; i++) {
      const operationEntry = operationEntries.nth(i);
      const operationId =
        await OperateOperationPanelPage.getOperationID(
          operationEntry,
        ).innerText();
      const operatioType =
        await OperateOperationPanelPage.getOperationType(
          operationEntry,
        ).innerText();
      operationIds.push({
        id: operationId,
        type: operatioType,
      });
    }
    if (wasCollapsed) {
      await this.collapseOperationsPanel();
    }
    return operationIds;
  }

  async clickOperationEntryById(operationId: string): Promise<void> {
    const operationEntryById = this.page
      .getByTestId('operation-id')
      .filter({hasText: operationId});
    await operationEntryById.click();
  }
}
