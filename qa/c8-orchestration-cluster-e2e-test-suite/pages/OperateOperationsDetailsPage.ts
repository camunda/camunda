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
    this.forbiddenMessage = page.getByText(/403 - You do not have permission/);
    this.backButton = page.getByRole('button', {
      name: 'Back to batch operations',
    });
    this.suspendButton = page.getByRole('button', {name: /^Suspend$/i});
    this.resumeButton = page.getByRole('button', {name: /^Resume$/i});
    this.optionsMenuButton = page.getByRole('button', {name: /Options/i});
    this.itemsTable = page.getByRole('table');
    this.itemsTableRows = this.itemsTable.getByRole('row');
    this.stateBadge = this.state;
  }

  async goto(batchOperationKey: string): Promise<void> {
    await this.page.goto(
      `${process.env.CORE_APPLICATION_URL}/operate/batch-operations/${batchOperationKey}`,
    );
  }

  async clickCancelFromOptionsMenu(): Promise<void> {
    await this.optionsMenuButton.click();
    await this.page.getByRole('menuitem', {name: /^Cancel\b/i}).click();
  }

  async getBatchOperationStatus(): Promise<string> {
    return await this.state.innerText();
  }

  /**
   * Drives the Suspend button until the suspend command is accepted.
   *
   * The button is driven by the read model, so it turns on before the freshly
   * created batch is visible to the suspend command. An immediate click 404s
   * ("Batch operation not found") and the UI does not retry (the original
   * nightly failure). Re-issue Suspend across that eventual-consistency window,
   * reloading between attempts (the detail view does not auto-refresh, #52021),
   * until the state flips to Suspended. Throws immediately if the batch finishes
   * cancelling first — a Completed/Failed batch can no longer be suspended, so
   * further attempts would be pointless.
   */
  async suspendUntilCommandAccepted(maxAttempts = 10): Promise<void> {
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      const status = await this.getBatchOperationStatus();
      if (/suspended/i.test(status)) {
        return;
      }
      if (/completed|failed/i.test(status)) {
        throw new Error(
          `Batch reached "${status}" before the suspend command was accepted; ` +
            `a finished batch can no longer be suspended.`,
        );
      }

      const suspendable = await this.suspendButton
        .isEnabled()
        .catch(() => false);
      if (suspendable) {
        await this.suspendButton.click();
      }
      await this.page.reload();
    }

    throw new Error(
      `Batch did not reach Suspended within ${maxAttempts} attempts.`,
    );
  }

  async getBatchOperationKey(): Promise<string> {
    const url = this.page.url();

    const match = url.match(/\/batch-operations\/([^/?]+)/);
    if (match && match[1]) {
      const batchOperationKey = match[1];
      return batchOperationKey;
    }

    throw new Error(`Could not extract batch operation ID from URL: ${url}`);
  }

  getProcessInstanceLink(processInstanceKey: string): Locator {
    return this.page.getByRole('link', {
      name: `View process instance ${processInstanceKey}`,
    });
  }
}
