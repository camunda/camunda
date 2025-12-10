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
  readonly resultsText: Locator;
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
  readonly singleOperationSpinner: Locator;
  readonly diagram: InstanceType<typeof OperateDiagramPage>;
  readonly processActiveCheckbox: Locator;
  readonly processCompletedCheckbox: Locator;
  readonly processRunningInstancesCheckbox: Locator;
  readonly processIncidentsCheckbox: Locator;
  readonly processFinishedInstancesCheckbox: Locator;
  readonly dataList: Locator;
  readonly continueButton: Locator;
  readonly processInstancesPanel: Locator;
  readonly migrateButton: Locator;
  readonly operationsPanel: Locator;
  readonly operationsList: Locator;
  readonly latestOperationEntry: Locator;
  readonly latestOperationLink: Locator;
  readonly latestOperationMigrateHeading: Locator;
  readonly latestOperationProgressBar: Locator;
  readonly latestOperationEntryBeforeCompletion: Locator;
  readonly operationSuccessMessage: Locator;
  readonly collapsedOperationsPanel: Locator;
  readonly expandOperationsButton: Locator;
  readonly inProgressBar: Locator;
  readonly selectAllRowsCheckbox: Locator;
  readonly retryButton: Locator;
  readonly cancelButton: Locator;
  readonly applyButton: Locator;
  readonly resultsCount: Locator;
  readonly scheduledOperationsIcons: Locator;
  processInstanceLinkByKey: (processInstanceKey: string) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.diagram = new OperateDiagramPage(page);
    this.processResultCount = page.getByTestId('result-count');
    this.resultsText = page.getByText('results');
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
      .getByRole('row')
      .first()
      .getByTestId('cell-parentInstanceId')
      .getByRole('link');
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
    this.singleOperationSpinner = page.getByTestId('operation-spinner');
    this.processActiveCheckbox = page
      .locator('label')
      .filter({hasText: 'Active'});
    this.processCompletedCheckbox = page
      .locator('label')
      .filter({hasText: 'Completed'});
    this.processRunningInstancesCheckbox = page
      .locator('label')
      .filter({hasText: 'Running Instances'});
    this.processIncidentsCheckbox = page
      .locator('label')
      .filter({hasText: 'Incidents'});
    this.processFinishedInstancesCheckbox = page
      .getByTestId('filter-finished-instances')
      .getByRole('checkbox');
    this.dataList = page.getByTestId('data-list');
    this.continueButton = page.getByRole('button', {name: 'continue'});
    this.processInstancesPanel = page.getByRole('region', {
      name: 'process instances panel',
    });
    this.migrateButton = this.processInstancesPanel.getByRole('button', {
      name: /^migrate$/i,
    });
    this.operationsPanel = page.getByRole('region', {
      name: 'Operations',
    });
    this.operationsList = page.getByTestId('operations-list');
    this.latestOperationEntry = this.operationsList
      .getByRole('listitem')
      .first();
    this.latestOperationEntryBeforeCompletion = this.operationsList
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
    this.inProgressBar = this.operationsList.locator(
      '[role="progressbar"][aria-busy="true"]',
    );
    this.selectAllRowsCheckbox = page.getByRole('columnheader', {
      name: 'Select all rows',
    });
    this.retryButton = page.getByRole('button', {name: 'Retry', exact: true});
    this.cancelButton = page.getByRole('button', {name: 'Cancel', exact: true});
    this.applyButton = page.getByRole('button', {name: 'Apply'});
    this.resultsCount = page.getByText(/\d+ results/);
    this.scheduledOperationsIcons = page.getByTitle(
      /has scheduled operations/i,
    );
    this.processInstanceLinkByKey = (processInstanceKey: string) =>
      page.getByRole('link', {
        name: processInstanceKey,
      });
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

  async visibleKeys(): Promise<string[]> {
    const texts = await this.page
      .getByTestId('cell-processInstanceKey')
      .allInnerTexts();
    return texts.map((t) => t.trim());
  }

  static getProcessVersion(row: Locator): Locator {
    return row.getByTestId('cell-processVersion');
  }

  getInstanceRow(index: number): Locator {
    return this.dataList.getByRole('row').nth(index);
  }

  getCanceledIcon(processInstanceKey: string): Locator {
    return this.page.getByTestId(`CANCELED-icon-${processInstanceKey}`);
  }

  getRetryInstanceButton(processInstanceKey: string): Locator {
    return this.page.getByRole('button', {
      name: `Retry Instance ${processInstanceKey}`,
    });
  }

  getCancelInstanceButton(processInstanceKey: string): Locator {
    return this.page.getByRole('button', {
      name: `Cancel Instance ${processInstanceKey}`,
    });
  }

  async clickRetryInstanceButton(processInstanceKey: string): Promise<void> {
    const button = this.getRetryInstanceButton(processInstanceKey);
    try {
      await button.click({timeout: 30000});
    } catch (error) {
      await button.scrollIntoViewIfNeeded({timeout: 60000});
      await button.click({timeout: 60000});
    }
  }

  async clickCancelInstanceButton(processInstanceKey: string): Promise<void> {
    const button = this.getCancelInstanceButton(processInstanceKey);
    await button.scrollIntoViewIfNeeded({timeout: 30000});
    await button.click({timeout: 30000});
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

  async selectProcessInstances(count: number): Promise<void> {
    for (let i = 0; i < count; i++) {
      const maxRetries = 3;
      let retryCount = 0;

      while (retryCount < maxRetries) {
        try {
          const checkbox = this.processInstancesPanel
            .getByRole('row', {name: 'select row'})
            .nth(i)
            .locator('label');

          // Wait for the element to be attached and stable
          await checkbox.waitFor({state: 'attached', timeout: 5000});
          if (!(await checkbox.isChecked())) {
            await checkbox.click({timeout: 10000});
          }
          await sleep(100);
          break;
        } catch (error) {
          retryCount++;
          if (retryCount === maxRetries) {
            console.error(
              `Failed to select process instance ${i} after ${maxRetries} attempts`,
            );
            throw error;
          }
          console.log(
            `Attempt ${retryCount} to select process instance ${i} failed. Retrying...`,
          );
          await sleep(500);
        }
      }
    }
    await expect(
      this.page.getByText(`${count} items selected`).first(),
    ).toBeVisible();
  }

  async clickProcessActiveCheckbox(): Promise<void> {
    await this.processActiveCheckbox.click();
  }

  async clickProcessCompletedCheckbox(): Promise<void> {
    await this.processCompletedCheckbox.click({timeout: 120000});
  }

  async clickProcessIncidentsCheckbox(): Promise<void> {
    await this.processIncidentsCheckbox.click({timeout: 90000});
  }

  async clickRunningProcessInstancesCheckbox(): Promise<void> {
    await this.processRunningInstancesCheckbox.click({timeout: 90000});
  }

  async clickFinishedProcessInstancesCheckbox(): Promise<void> {
    await this.processFinishedInstancesCheckbox.click({timeout: 90000});
  }

  async clickMigrateButton(): Promise<void> {
    await this.migrateButton.click();
  }
  async clickContinueButton(): Promise<void> {
    await this.continueButton.click();
  }

  async startMigration(): Promise<void> {
    await this.clickMigrateButton();
    await this.clickContinueButton();
  }

  async clickLatestOperationLink(): Promise<void> {
    await this.latestOperationLink.click({timeout: 60000});
  }

  getVersionCells(version: string): Locator {
    return this.dataList.getByRole('cell', {name: version, exact: true});
  }

  async expandOperationsPanel(): Promise<void> {
    const isCollapsed = await this.collapsedOperationsPanel.isVisible();
    if (isCollapsed) {
      await this.expandOperationsButton.click();
      await this.operationsList.waitFor({state: 'visible', timeout: 10000});
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

  getMigrationOperationEntry(successCount: number): Locator {
    return this.page
      .locator('[data-testid="operations-entry"]')
      .filter({hasText: 'Migrate'})
      .filter({hasText: `${successCount} operations succeeded`});
  }

  getRetryOperationEntry(successCount: number): Locator {
    return this.page
      .locator('[data-testid="operations-entry"]')
      .filter({hasText: 'Retry'})
      .filter({hasText: `${successCount} retries succeeded`})
      .first();
  }

  getCancelOperationEntry(successCount: number): Locator {
    return this.page
      .locator('[data-testid="operations-entry"]')
      .filter({hasText: 'Cancel'})
      .filter({hasText: `${successCount} operations succeeded`});
  }

  async clickOperationLink(operationEntry: Locator): Promise<void> {
    await operationEntry
      .locator('[data-testid="operation-id"]')
      .click({timeout: 30000});
  }
}

export {OperateProcessesPage};
