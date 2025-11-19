/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {OperateDiagramPage} from './OperateDiagramPage';
import {sleep} from '../utils/sleep';
import {checkUpdateOnVersion} from 'utils/zeebeClient';

class OperateProcessesPage {
  private page: Page;
  readonly processResultCount: Locator;
  readonly processPageHeading: Locator;
  readonly noMatchingInstancesMessage: Locator;
  readonly processNameFilter: Locator;
  readonly processInstanceLink: Locator;
  readonly startDateSortButton: Locator;
  readonly processInstanceKeySortButton: Locator;
  readonly versionSortButton: Locator;
  readonly processNameSortButton: Locator;
  readonly processInstancesTable: Locator;
  readonly parentInstanceIdCell: Locator;
  readonly endDateCell: Locator;
  readonly versionCell: Locator;
  readonly processInstanceKeyCell: Locator;
  readonly migrateBatchOperationButton: Locator;
  readonly cancelBatchOperationButton: Locator;
  readonly applyCancelBatchOperationDialogButton: Locator;
  readonly continueMigrationDialogButton: Locator;
  readonly cancelProcessInstanceButton: Locator;
  readonly cancelProcessInstanceDialogButton: Locator;
  readonly singleCancellationSpinner: Locator;
  readonly tableLoadingSpinner: Locator;
  readonly diagram: InstanceType<typeof OperateDiagramPage>;

  constructor(page: Page) {
    this.page = page;
    this.diagram = new OperateDiagramPage(page);
    this.processResultCount = page.getByTestId('result-count');
    this.processPageHeading = page
      .getByTestId('expanded-panel')
      .getByRole('heading', {name: 'Process'});
    this.noMatchingInstancesMessage = page.getByText(
      'There are no Instances matching this filter set',
    );
    this.processNameFilter = page.getByRole('combobox', {name: 'name'});
    this.processInstanceLink = page
      .getByRole('link', {
        name: 'view instance',
      })
      .first();
    this.startDateSortButton = page.getByRole('button', {
      name: 'sort by start date',
    });
    this.processInstanceKeySortButton = page.getByRole('button', {
      name: 'sort by process instance key',
    });
    this.versionSortButton = page.getByRole('button', {
      name: 'sort by version',
    });
    this.processNameSortButton = page.getByRole('button', {
      name: 'sort by name',
    });
    this.processInstancesTable = page.getByTestId('data-list').getByRole('row');
    this.processInstanceKeyCell = page
      .getByTestId('data-list')
      .getByTestId('cell-processInstanceKey')
      .first();
    this.parentInstanceIdCell = page
      .getByTestId('data-list')
      .getByTestId('cell-parentInstanceId')
      .first();
    this.endDateCell = page
      .getByTestId('data-list')
      .getByTestId('cell-endDate')
      .first();
    this.versionCell = page.getByTestId('cell-processVersion');
    this.migrateBatchOperationButton = page.getByRole('button', {
      name: 'Migrate',
    });
    this.cancelBatchOperationButton = page.getByTestId(
      'cancel-batch-operation',
    );
    this.applyCancelBatchOperationDialogButton = page
      .getByRole('dialog')
      .getByRole('button', {name: 'Apply'});
    this.continueMigrationDialogButton = page
      .getByRole('dialog')
      .getByRole('button', {name: 'Continue'});
    this.cancelProcessInstanceButton = page
      .getByRole('button', {name: 'Cancel Instance'})
      .first();
    this.cancelProcessInstanceDialogButton = page
      .getByRole('dialog')
      .getByRole('button', {name: 'Apply'});
    this.singleCancellationSpinner = page.getByTestId('operation-spinner');
    this.tableLoadingSpinner = page.getByTestId('data-table-loader');
  }

  async filterByProcessName(name: string): Promise<void> {
    await this.processNameFilter.click();
    await this.processNameFilter.fill(name);
    await this.page.keyboard.press('Enter');
    await this.page.getByRole('heading', {name}).waitFor({state: 'visible'});
  }

  async clickProcessInstanceLink(): Promise<void> {
    const maxRetries = 3;
    let retryCount = 0;
    while (retryCount < maxRetries) {
      try {
        await sleep(5_000);
        await this.processInstanceLink.click();
        return;
      } catch {
        retryCount++;
        console.log(`Attempt ${retryCount} failed. Retrying...`);
        await this.page.reload();
      }
    }
    throw new Error(
      `Failed to click on process instance link after ${maxRetries} attempts.`,
    );
  }

  async checkVersion(processInstanceKey: string): Promise<void> {
    const maxRetries = 10;
    let retryCount = 0;
    while (retryCount < maxRetries) {
      try {
        await checkUpdateOnVersion('2', processInstanceKey);
        return;
      } catch {
        retryCount++;
        console.log(`Attempt ${retryCount} failed. Retrying...`);
        await this.page.reload();
      }
    }
    throw new Error(`Failed to check version after ${maxRetries} attempts.`);
  }

  async assertProcessInstanceLink(processInstanceKey: string): Promise<void> {
    await expect(
      this.page.getByRole('link', {
        name: `View instance ${processInstanceKey}`,
      }),
    ).toBeVisible();
  }

  async clickStartDateSortButton(): Promise<void> {
    await this.startDateSortButton.click();
  }

  async clickProcessInstanceKeySortButton(): Promise<void> {
    await this.processInstanceKeySortButton.click();
  }

  async clickVersionSortButton(): Promise<void> {
    await this.versionSortButton.click();
  }

  async clickProcessNameSortButton(): Promise<void> {
    await this.processNameSortButton.click();
  }

  async selectProcessCheckboxByPIK(...PIK: string[]): Promise<void> {
    for (const key of PIK) {
      await this.page.locator(`label[for$="${key}"]`).click();
    }
  }

  async clickCancelBatchOperationButton(): Promise<void> {
    await this.cancelBatchOperationButton.click();
  }

  async clickApplyCancelBatchOperationDialogButton(): Promise<void> {
    await this.applyCancelBatchOperationDialogButton.click();
  }

  async clickMigrateBatchOperationButton(): Promise<void> {
    await this.migrateBatchOperationButton.click();
  }

  async clickContinueMigrationDialogButton(): Promise<void> {
    await this.continueMigrationDialogButton.click();
  }

  async clickCancelProcessInstanceButton(): Promise<void> {
    await this.cancelProcessInstanceButton.click();
  }

  async clickCancelProcessInstanceDialogButton(): Promise<void> {
    await this.cancelProcessInstanceDialogButton.click();
  }

  async tableHasInstanceKey(keyStr: string): Promise<boolean> {
    const meow = this.processInstancesTable
      .getByTestId('cell-processInstanceKey')
      .getByText(keyStr);
    if (await meow.count()) {
      return true;
    }
    return false;
  }

  async visibleKeys(): Promise<string[]> {
    const texts = await this.page
      .getByTestId('cell-processInstanceKey')
      .allInnerTexts();
    return texts.map((t) => t.trim());
  }

  static getProcessVersion(row: Locator): Locator {
    return row.getByTestId('cell-processVersion');
  }

  static getRowByProcessInstanceKey(page: Page, keyStr: string): Locator {
    return page
      .getByTestId('data-list')
      .getByRole('row')
      .filter({
        has: page
          .getByTestId('cell-processInstanceKey')
          .filter({hasText: keyStr}),
      });
  }
}

export {OperateProcessesPage};
