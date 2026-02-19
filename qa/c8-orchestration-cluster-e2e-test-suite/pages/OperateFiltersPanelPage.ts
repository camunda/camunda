/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

type OptionalFilter =
  | 'Variable'
  | 'Process Instance Key(s)'
  | 'Parent Process Instance Key'
  | 'Operation Id'
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
  readonly operationIdFilter: Locator;
  readonly resetFiltersButton: Locator;
  readonly errorMessageFilter: Locator;
  readonly startDateFilter: Locator;
  readonly variableNameFilter: Locator;
  readonly variableValueFilter: Locator;
  readonly multipleVariablesSwitch: Locator;
  readonly moreFiltersButton: Locator;
  readonly dateFilterDialog: Locator;
  readonly fromTimeInput: Locator;
  readonly toTimeInput: Locator;
  readonly fromDateInput: Locator;
  readonly applyButton: Locator;
  readonly jsonEditorModalButton: Locator;
  readonly variableEditorDialog: Locator;
  readonly dialogEditVariableValueText: Locator;
  readonly dialogEditMultipleVariableValueText: Locator;
  readonly dialogCancelButton: Locator;
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
    this.operationIdFilter = this.page.getByRole('textbox', {
      name: 'operation id',
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
    this.variableNameFilter = this.page.getByTestId(
      'optional-filter-variable-name',
    );
    this.variableValueFilter = this.page.getByTestId(
      'optional-filter-variable-value',
    );
    this.multipleVariablesSwitch = this.page.getByText('multiple');
    this.moreFiltersButton = this.page.getByRole('button', {
      name: 'More Filters',
    });
    this.dateFilterDialog = this.page.getByRole('dialog');
    this.fromTimeInput = page.getByTestId('fromTime');
    this.toTimeInput = page.getByTestId('toTime');
    this.fromDateInput = this.page.getByText('From date');
    this.applyButton = this.page.getByText('Apply');
    this.jsonEditorModalButton = this.page.getByRole('button', {
      name: /open (json )?editor/i,
    });
    this.variableEditorDialog = this.page.getByRole('dialog');
    this.dialogEditVariableValueText = this.variableEditorDialog.getByText(
      'edit variable value',
    );
    this.dialogEditMultipleVariableValueText =
      this.variableEditorDialog.getByText('edit multiple variable values');
    this.dialogCancelButton = this.variableEditorDialog.getByRole('button', {
      name: 'cancel',
    });
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
      await expect(this.processVersionFilter).toBeDisabled();
    }
    await this.processNameFilter.click();
    await this.getOptionByName(option).click({timeout: 30000});
    await expect(this.processVersionFilter).toBeEnabled({timeout: 3000});
  }

  async selectVersion(option: string) {
    await expect(this.processNameFilter).toBeVisible();
    await expect(this.processVersionFilter).toBeEnabled();
    await this.processVersionFilter.click();
    await expect(this.getOptionByName(option)).toBeVisible();
    await this.getOptionByName(option).click({timeout: 30000});
  }

  async selectFlowNode(option: string) {
    await this.flowNodeFilter.click();
    await this.getOptionByName(option, false).click();
  }

  async fillVariableNameFilter(name: string) {
    await expect(this.variableNameFilter).toBeVisible();
    await expect(this.variableNameFilter).toBeEnabled();
    await this.variableNameFilter.fill(name);
  }

  async fillVariableValueFilter(value: string) {
    await expect(this.variableValueFilter).toBeVisible();
    await expect(this.variableValueFilter).toBeEnabled();
    await this.variableValueFilter.fill(value);
    await expect(this.variableValueFilter).toHaveValue(value);
  }

  async fillProcessInstanceKeyFilter(processInstanceKey: string) {
    await expect(this.processInstanceKeysFilter).toBeVisible();
    await expect(this.processInstanceKeysFilter).toBeEnabled();
    await this.processInstanceKeysFilter.fill(processInstanceKey);
    await expect(this.processInstanceKeysFilter).toHaveValue(
      processInstanceKey,
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

  async fillOperationIdFilter(operationId: string) {
    await this.operationIdFilter.fill(operationId);
  }

  async clickJsonEditorModal() {
    await this.jsonEditorModalButton.click();
  }

  async closeModalWithCancel() {
    await this.dialogCancelButton.click();
  }

  async clickMultipleVariablesSwitch() {
    await this.multipleVariablesSwitch.click({force: true});
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
