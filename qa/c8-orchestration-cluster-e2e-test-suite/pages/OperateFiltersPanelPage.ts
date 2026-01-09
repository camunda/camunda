/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {waitForAssertion} from '../utils/waitForAssertion';

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
  readonly processInstanceKeysFilter: Locator;
  readonly processInstanceKeysFilterOption: Locator;
  readonly parentProcessInstanceKey: Locator;
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
  readonly moreFiltersMenu: Locator;
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
    this.runningInstancesCheckbox = this.page
      .locator('label')
      .filter({hasText: 'Running'});
    this.activeInstancesCheckbox = this.page
      .locator('label')
      .filter({hasText: 'Active'});
    this.incidentsInstancesCheckbox = this.page
      .locator('label')
      .filter({hasText: 'Incidents'});
    this.completedInstancesCheckbox = this.page
      .locator('label')
      .filter({hasText: 'Completed'});
    this.canceledInstancesCheckbox = this.page
      .locator('label')
      .filter({hasText: 'Canceled'});
    this.finishedInstancesCheckbox = this.page
      .locator('label')
      .filter({hasText: 'Finished'});
    this.processNameFilter = this.page.getByRole('combobox', {
      name: 'Name',
    });
    this.processVersionFilter = this.page.getByRole('combobox', {
      name: 'Version',
    });
    this.processInstanceKeysFilterOption = this.page.getByRole('menuitem', {
      name: 'Process Instance Key(s)',
    });
    this.processInstanceKeysFilter = page.getByRole('textbox', {
      name: 'process instance key',
    });
    this.parentProcessInstanceKey = page.getByRole('textbox', {
      name: 'parent process instance key',
    });
    this.processInstanceKey = page.getByRole('textbox', {
      name: 'process instance key',
    });
    this.flowNodeFilter = this.page.getByRole('combobox', {
      name: 'flow node',
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
    this.variableNameFilter = this.page.getByRole('textbox', {
      name: 'name',
    });
    this.variableValueFilter = this.page.getByRole('textbox', {
      name: 'value',
    });
    this.multipleVariablesSwitch = this.page.getByText('multiple');
    this.moreFiltersButton = this.page.getByRole('button', {
      name: 'More Filters',
    });
    this.moreFiltersMenu = this.page.getByRole('menu');
    this.dateFilterDialog = this.page.getByRole('dialog');
    this.fromTimeInput = page.getByTestId('fromTime');
    this.toTimeInput = page.getByTestId('toTime');
    this.fromDateInput = this.page.getByText('From date');
    this.applyButton = this.page.getByText('Apply');
    this.jsonEditorModalButton = this.page.getByRole('button', {
      name: /open (json )?editor modal/i,
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
    await this.moreFiltersButton.click();
    await this.moreFiltersMenu.waitFor({state: 'visible'});
    const menuitem = this.page.getByRole('menuitem', {
      name: filterName,
    });
    await menuitem.waitFor({state: 'visible', timeout: 30000});
    await menuitem.click();
  }

  async removeOptionalFilter(filterName: OptionalFilter) {
    await this.page.getByLabel(filterName, {exact: true}).hover();
    await this.page.getByLabel(`Remove ${filterName} Filter`).click();
  }

  async isOptionalFilterDisplayed(filterName: OptionalFilter): Promise<boolean> {
    return await this.page
      .getByLabel(filterName, {exact: true})
      .isVisible();
  }

  async selectProcess(option: string) {
    await waitForAssertion({
      assertion: async () => {
        await expect(this.processNameFilter).toBeVisible();
        await this.processNameFilter.click();
        await this.processNameFilter.clear();
        await this.getOptionByName(option).click();
      },
      onFailure: async () => {
        await this.page.reload();
      },
    });
  }

  async selectVersion(option: string) {
    await waitForAssertion({
      assertion: async () => {
        await this.processVersionFilter.click();
        await this.getOptionByName(option).click();
      },
      onFailure: async () => {
        await this.page.reload();
      },
    });
  }

  async selectFlowNode(option: string) {
    await this.flowNodeFilter.click();
    await this.getOptionByName(option, false).click();
  }

  async fillVariableNameFilter(name: string) {
    await this.variableNameFilter.fill(name);
  }

  async fillVariableValueFilter(value: string) {
    await this.variableValueFilter.fill(value);
  }

  async fillProcessInstanceKeyFilter(processInstanceKey: string) {
    await this.processInstanceKeysFilter.fill(processInstanceKey);
  }

  async fillParentProcessInstanceKeyFilter(parentProcessInstanceKey: string) {
    await this.parentProcessInstanceKey.fill(parentProcessInstanceKey);
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
    await this.errorMessageFilter.fill(errorMessage);
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
