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
    this.expandButton = page.getByRole('button', {name: 'Expand Operations'});
    this.operationList = page.getByTestId('operations-list');
    this.expandedOperationPanel = page
      .getByLabel('Operations')
      .getByTestId('expanded-panel');
    this.collapseButton = page.getByRole('button', {
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

    // Read every entry's id and type together in a single atomic DOM pass.
    // operations-list is a cluster-wide feed that other concurrently-running
    // specs mutate continuously (e.g. operations.spec floods Cancel entries).
    // Resolving the id and the type as two separate locator queries — each
    // re-resolving nth(i) — lets a refetch reorder the list between the two
    // reads, so nth(i) points at a different entry each time and one entry's
    // id gets paired with another entry's type (e.g. a Cancel id tagged
    // "Migrate"). That mislabeled pair then leaks into the wrong operationId
    // filter downstream. evaluateAll reads both fields from the same DOM
    // snapshot, so id and type always come from the same entry.
    const operationIds = await this.getAllOperationEntries().evaluateAll(
      (entries) =>
        entries.map((entry) => ({
          id:
            entry
              .querySelector('[data-testid="operation-id"]')
              ?.textContent?.trim() ?? '',
          type:
            entry
              .querySelector('h1, h2, h3, h4, h5, h6')
              ?.textContent?.trim() ?? '',
        })),
    );

    if (wasCollapsed) {
      await this.collapseOperationsPanel();
    }
    return operationIds.filter((entry) => entry.id.length > 0);
  }

  async clickOperationEntryById(operationId: string): Promise<void> {
    const operationEntryById = this.page
      .getByTestId('operation-id')
      .filter({hasText: operationId});
    await operationEntryById.click();
  }

  // operations-list is a cluster-wide feed (backed by /api/batch-operations):
  // every operation from every concurrently-running spec lands in it. Filtering
  // by type + "N success" text can match another spec's operation of the same
  // type and count instead of this test's own. Scope to the specific operation
  // ID (diffed via getNewOperationIds against a before/after snapshot) instead.
  getOperationEntryById(operationId: string): Locator {
    return this.getAllOperationEntries().filter({
      has: this.page.getByTestId('operation-id').filter({hasText: operationId}),
    });
  }

  async clickOperationLink(operationEntry: Locator): Promise<void> {
    await OperateOperationPanelPage.getOperationID(operationEntry)
      .first()
      .click({timeout: 30000});
  }
}
