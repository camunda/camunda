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
  readonly forbiddenMessage: Locator;
  readonly backButton: Locator;
  readonly suspendButton: Locator;
  readonly resumeButton: Locator;
  readonly optionsMenuButton: Locator;
  readonly itemsTable: Locator;
  readonly itemsTableRows: Locator;
  readonly stateBadge: Locator;
  readonly summaryTile: Locator;
  readonly actorTile: Locator;
  readonly startDateTile: Locator;

  constructor(page: Page) {
    this.page = page;
    this.tileElement = this.page.locator('.cds--tile');
    this.state = this.tileElement
      .filter({hasText: 'State'})
      .getByRole('status', {name: /Batch operation status:/});
    this.summaryOfItems = this.tileElement
      .filter({hasText: 'Summary of items'})
      .getByRole('status');
    this.summaryTile = this.tileElement.filter({hasText: 'Summary of items'});
    this.actorTile = this.tileElement.filter({hasText: 'Actor'});
    this.startDateTile = this.tileElement.filter({hasText: 'Start date'});
    this.forbiddenMessage = page.getByText(
      /403 - You do not have permission/,
    );
    this.backButton = page.getByRole('button', {
      name: 'Back to batch operations',
    });
    this.suspendButton = page.getByRole('button', {name: /^Suspend$/i});
    this.resumeButton = page.getByRole('button', {name: /^Resume$/i});
    this.optionsMenuButton = page.getByRole('button', {name: /Options/i});
    this.itemsTable = page.getByRole('table');
    this.itemsTableRows = this.itemsTable.getByRole('row');
    this.stateBadge = page.getByRole('status', {
      name: /Batch operation status:/,
    });
  }

  async goto(batchOperationKey: string): Promise<void> {
    await this.page.goto(
      `${process.env.CORE_APPLICATION_URL}/operate/batch-operations/${batchOperationKey}`,
    );
  }

  async clickCancelFromOptionsMenu(): Promise<void> {
    await this.optionsMenuButton.click();
    await this.page.getByRole('menuitem', {name: /^Cancel$/i}).click();
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

  getProcessInstanceLink(processInstanceKey: string): Locator {
    return this.page.getByRole('link', {
      name: `View process instance ${processInstanceKey}`,
    });
  }
}
