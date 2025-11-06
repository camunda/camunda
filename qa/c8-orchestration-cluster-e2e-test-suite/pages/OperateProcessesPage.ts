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

class OperateProcessesPage {
  private page: Page;
  readonly processResultCount: Locator;
  readonly processActiveCheckbox: Locator;
  readonly processCompletedCheckbox: Locator;
  readonly processRunningInstancesCheckbox: Locator;
  readonly processIncidentsCheckbox: Locator;
  readonly processPageHeading: Locator;
  readonly noMatchingInstancesMessage: Locator;
  readonly processFinishedInstancesCheckbox: Locator;
  readonly processNameFilter: Locator;
  readonly processInstanceLink: Locator;
  readonly startDateSortButton: Locator;
  readonly processInstanceKeySortButton: Locator;
  readonly versionSortButton: Locator;
  readonly processNameSortButton: Locator;
  readonly dataList: Locator;
  readonly processInstancesTable: Locator;
  readonly parentInstanceIdCell: Locator;
  readonly endDateCell: Locator;
  readonly versionCell: Locator;
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

  readonly diagram: InstanceType<typeof OperateDiagramPage>;

  constructor(page: Page) {
    this.page = page;
    this.diagram = new OperateDiagramPage(page);
    this.processResultCount = page.getByTestId('result-count');
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
    this.processPageHeading = page
      .getByTestId('expanded-panel')
      .getByRole('heading', {name: 'Process'});
    this.noMatchingInstancesMessage = page.getByText(
      'There are no Instances matching this filter set',
    );
    this.processFinishedInstancesCheckbox = page
      .getByTestId('filter-finished-instances')
      .getByRole('checkbox');
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
    this.dataList = page.getByTestId('data-list');
    this.processInstancesTable = this.dataList.getByRole('row');
    this.parentInstanceIdCell = this.dataList
      .getByTestId('cell-parentInstanceId')
      .first();
    this.endDateCell = this.dataList.getByTestId('cell-endDate').first();
    this.versionCell = page.getByTestId('process-version-select');
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
  }

  async selectProcessInstances(count: number): Promise<void> {
    for (let i = 0; i < count; i++) {
      await this.processInstancesPanel
        .getByRole('row', {name: 'select row'})
        .nth(i)
        .locator('label')
        .click();
      await sleep(100);
    }
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
    // New operations appear at the bottom while in progress, then move to top when complete
    // Look for any progress bar with aria-busy="true" in the operations list
    const inProgressBar = this.operationsList.locator(
      '[role="progressbar"][aria-busy="true"]',
    );

    // Wait for the in-progress bar to appear (operation started)
    await expect(inProgressBar).toBeVisible({timeout: 10000});

    // Wait for it to disappear (operation completed and moved to top)
    await expect(inProgressBar).not.toBeVisible({timeout: 120000});
  }
}

export {OperateProcessesPage};
