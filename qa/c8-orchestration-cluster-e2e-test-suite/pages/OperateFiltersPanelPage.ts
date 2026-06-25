/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

type OptionalFilter =
  | 'Variables'
  | 'Process Instance Key(s)'
  | 'Parent Process Instance Key'
  | 'Batch Operation Key'
  | 'Error Message'
  | 'Start Date Range'
  | 'End Date Range'
  | 'Failed job but retries left';

export class OperateFiltersPanelPage {
  private page: Page;
  readonly activeInstancesCheckbox: Locator;
  readonly incidentsInstancesCheckbox: Locator;
  readonly runningInstancesCheckbox: Locator;
  readonly completedInstancesCheckbox: Locator;
  readonly canceledInstancesCheckbox: Locator;
  readonly finishedInstancesCheckbox: Locator;
  readonly processNameFilter: Locator;
  readonly processVersionFilter: Locator;
  readonly processNameClearButton: Locator;
  readonly processInstanceKeysFilter: Locator;
  readonly processInstanceKeysFilterOption: Locator;
  readonly parentProcessInstanceKeyFilter: Locator;
  readonly processInstanceKey: Locator;
  readonly flowNodeFilter: Locator;
  readonly batchOperationKeyFilter: Locator;
  readonly resetFiltersButton: Locator;
  readonly errorMessageFilter: Locator;
  readonly startDateFilter: Locator;
  readonly openVariableFilterModalButton: Locator;
  readonly variableFilterDialog: Locator;
  readonly singleConditionNameInput: Locator;
  readonly singleConditionValueInput: Locator;
  readonly moreFiltersButton: Locator;
  readonly dateFilterDialog: Locator;
  readonly fromTimeInput: Locator;
  readonly toTimeInput: Locator;
  readonly fromDateInput: Locator;
  readonly applyButton: Locator;
  readonly getOptionByName: (name: string, exact?: boolean) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.activeInstancesCheckbox = page
      .locator('label')
      .filter({hasText: 'Active'});
    this.completedInstancesCheckbox = page
      .locator('label')
      .filter({hasText: 'Completed'});
    this.canceledInstancesCheckbox = page
      .locator('label')
      .filter({hasText: 'Canceled'});
    this.runningInstancesCheckbox = page
      .locator('label')
      .filter({hasText: 'Running Instances'});
    this.incidentsInstancesCheckbox = page
      .locator('label')
      .filter({hasText: 'Incidents'});
    this.finishedInstancesCheckbox = page
      .locator('label')
      .filter({hasText: 'Finished Instances'});
    this.processNameFilter = this.page.getByRole('combobox', {
      name: 'Name',
    });
    this.processVersionFilter = this.page.getByRole('combobox', {
      name: 'Version',
    });
    this.processNameClearButton = this.processNameFilter
      .locator('..')
      .getByRole('button', {
        name: 'Clear selected item',
      });
    this.processInstanceKeysFilterOption = this.page.getByRole('menuitem', {
      name: 'Process Instance Key(s)',
    });
    this.processInstanceKeysFilter = page.getByRole('textbox', {
      name: 'process instance key',
    });
    this.parentProcessInstanceKeyFilter = page.getByRole('textbox', {
      name: 'parent process instance key',
    });
    this.processInstanceKey = page.getByRole('textbox', {
      name: 'process instance key',
    });
    this.flowNodeFilter = this.page.getByRole('combobox', {
      name: 'element',
    });
    this.batchOperationKeyFilter = this.page.getByRole('textbox', {
      name: 'batch operation key',
    });
    this.resetFiltersButton = this.page.getByRole('button', {
      name: 'reset filters',
    });
    this.errorMessageFilter = this.page.getByRole('textbox', {
      name: 'error message',
    });
    this.startDateFilter = this.page.getByRole('textbox', {
      name: 'start date range',
    });
    this.openVariableFilterModalButton = page.getByTestId(
      'open-variable-filter-modal',
    );
    this.variableFilterDialog = page.getByRole('dialog', {
      name: 'Filter by variable',
    });
    this.singleConditionNameInput = page.getByTestId('single-condition-name');
    this.singleConditionValueInput = page.getByTestId('single-condition-value');
    this.moreFiltersButton = this.page.getByRole('button', {
      name: 'More Filters',
    });
    this.dateFilterDialog = this.page.getByRole('dialog');
    this.fromTimeInput = page.getByTestId('fromTime');
    this.toTimeInput = page.getByTestId('toTime');
    this.fromDateInput = this.page.getByText('From date');
    this.applyButton = this.page.getByText('Apply');
    this.getOptionByName = (name: string, exact = true) =>
      this.page.getByRole('option', {name, exact});
  }

  async validateCheckedState(
    checked: Array<Locator>,
    unChecked: Array<Locator>,
  ) {
    for (const filter of checked) {
      await expect(filter).toBeChecked();
    }

    for (const filter of unChecked) {
      await expect(filter).not.toBeChecked();
    }
  }

  async displayOptionalFilter(filterName: OptionalFilter) {
    if (await this.isOptionalFilterDisplayed(filterName)) {
      return;
    }
    await this.moreFiltersButton.click();
    await this.page
      .getByRole('menuitem', {
        name: filterName,
      })
      .click();
  }

  async removeOptionalFilter(filterName: OptionalFilter) {
    await this.page.getByLabel(filterName, {exact: true}).hover();
    await this.page.getByLabel(`Remove ${filterName} Filter`).click();
  }

  async isOptionalFilterDisplayed(
    filterName: OptionalFilter,
  ): Promise<boolean> {
    return await this.page.getByLabel(filterName, {exact: true}).isVisible();
  }

  async selectProcess(option: string) {
    if (await this.processNameClearButton.isVisible()) {
      await this.processNameClearButton.click();
      await expect(this.processVersionFilter).toBeDisabled({timeout: 30000});
      await expect
        .poll(() => this.page.url())
        .not.toContain('processDefinitionId');
      await expect
        .poll(() => this.page.url())
        .not.toContain('processDefinitionVersion');
    }
    await this.processNameFilter.click();
    await this.getOptionByName(option).click({timeout: 30000});
    await expect(this.processVersionFilter).toBeEnabled({timeout: 3000});
  }

  async selectVersion(option: string) {
    await expect(this.processNameFilter).toBeVisible();
    await expect(this.processVersionFilter).toBeEnabled();
    await this.processVersionFilter.click();
    await expect(this.getOptionByName(option)).toBeVisible({timeout: 30000});
    await this.getOptionByName(option).click({timeout: 30000});
  }

  async selectFlowNode(option: string) {
    await this.flowNodeFilter.click();
    await this.getOptionByName(option, false).click();
  }

  async openVariableFilterModal() {
    await this.openVariableFilterModalButton.click();
    await expect(this.variableFilterDialog).toBeVisible();
  }

  async fillSingleConditionInline(name: string, value: string) {
    await this.singleConditionNameInput.fill(name);
    await this.singleConditionValueInput.fill(value);
    await this.page.waitForTimeout(900);
  }

  async fillConditionRow(index: number, name: string, value: string) {
    await this.variableFilterDialog
      .getByTestId(`variable-filter-name-${index}`)
      .fill(name);
    await this.variableFilterDialog
      .getByTestId(`variable-filter-value-${index}`)
      .fill(value);
  }

  async addCondition() {
    await this.variableFilterDialog
      .getByRole('button', {name: 'Add condition'})
      .click();
  }

  async applyVariableFilter() {
    await this.variableFilterDialog
      .getByRole('button', {name: 'Apply'})
      .click();
  }

  async selectOperator(index: number, operatorLabel: string) {
    await this.variableFilterDialog
      .getByTestId(`variable-filter-operator-${index}`)
      .click();
    await this.page.getByRole('option', {name: operatorLabel}).click();
  }

  async openJsonEditorForRow(index: number) {
    await this.variableFilterDialog
      .getByRole('button', {name: 'Open JSON editor'})
      .nth(index)
      .click();
  }

  async cancelVariableFilterModal() {
    await this.variableFilterDialog
      .getByRole('button', {name: 'Cancel'})
      .click();
  }

  async fillProcessInstanceKeyFilter(processInstanceKey: string) {
    await expect(this.processInstanceKeysFilter).toBeVisible();
    await expect(this.processInstanceKeysFilter).toBeEnabled();
    await this.processInstanceKeysFilter.click();
    await this.processInstanceKeysFilter.pressSequentially(processInstanceKey);
    await expect(this.processInstanceKeysFilter).toHaveValue(
      processInstanceKey,
      {timeout: 30000},
    );
  }

  async fillParentProcessInstanceKeyFilter(parentProcessInstanceKey: string) {
    await expect(this.parentProcessInstanceKeyFilter).toBeVisible();
    await expect(this.parentProcessInstanceKeyFilter).toBeEnabled();
    await this.parentProcessInstanceKeyFilter.fill(parentProcessInstanceKey);
    await expect(this.parentProcessInstanceKeyFilter).toHaveValue(
      parentProcessInstanceKey,
    );
  }

  async fillFromTimeInput(fromTime: string) {
    await this.fromTimeInput.clear();
    await this.fromTimeInput.fill(fromTime);
  }

  async fillToTimeInput(toTime: string) {
    await this.toTimeInput.clear();
    await this.toTimeInput.fill(toTime);
  }

  async pickDateTimeRange({
    fromDay,
    toDay,
    fromTime,
    toTime,
  }: {
    fromDay: string;
    toDay: string;
    fromTime?: string;
    toTime?: string;
  }) {
    await expect(this.dateFilterDialog).toBeVisible();

    const date = new Date();
    const monthName = date.toLocaleString('default', {month: 'long'});
    const year = date.getFullYear();

    await this.fromDateInput.click();
    await this.page.getByLabel(`${monthName} ${fromDay}, ${year}`).click();
    await this.page.getByLabel(`${monthName} ${toDay}, ${year}`).click();

    if (fromTime !== undefined) {
      await this.fillFromTimeInput(fromTime);
    }

    if (toTime !== undefined) {
      await this.fillToTimeInput(toTime);
    }
  }

  async clickApply() {
    await this.applyButton.click();
  }

  async clickResetFilters() {
    await this.resetFiltersButton.click();
  }

  async fillErrorMessageFilter(errorMessage: string) {
    await expect(this.errorMessageFilter).toBeVisible();
    await expect(this.errorMessageFilter).toBeEnabled();
    await this.errorMessageFilter.fill(errorMessage);
    await expect(this.errorMessageFilter).toHaveValue(errorMessage);
  }

  async fillBatchOperationKeyFilter(batchOperationKey: string) {
    await expect(this.batchOperationKeyFilter).toBeVisible();
    await expect(this.batchOperationKeyFilter).toBeEnabled();
    await this.batchOperationKeyFilter.fill(batchOperationKey);
    await expect(this.batchOperationKeyFilter).toHaveValue(batchOperationKey);
  }

  async clickRunningInstancesCheckbox(): Promise<void> {
    await this.runningInstancesCheckbox.click({timeout: 60000});
  }

  async clickActiveInstancesCheckbox(): Promise<void> {
    await this.activeInstancesCheckbox.click();
  }

  async clickIncidentsInstancesCheckbox(): Promise<void> {
    await this.incidentsInstancesCheckbox.click({timeout: 60000});
  }

  async clickFinishedInstancesCheckbox(): Promise<void> {
    await this.finishedInstancesCheckbox.click({timeout: 60000});
  }

  async clickCompletedInstancesCheckbox(): Promise<void> {
    await this.completedInstancesCheckbox.click({timeout: 60000});
  }
  async clickCanceledInstancesCheckbox(): Promise<void> {
    await this.canceledInstancesCheckbox.click({timeout: 60000});
  }
}
