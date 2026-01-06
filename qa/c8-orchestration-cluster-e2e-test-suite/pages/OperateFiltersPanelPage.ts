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
  readonly activeCheckbox: Locator;
  readonly incidentsCheckbox: Locator;
  readonly runningInstancesCheckbox: Locator;
  readonly completedCheckbox: Locator;
  readonly canceledCheckbox: Locator;
  readonly finishedInstancesCheckbox: Locator;
  readonly processNameFilter: Locator;
  readonly processVersionFilter: Locator;
  readonly processInstanceKeysFilter: Locator;
  readonly processInstanceKeysFilterOption: Locator;
  readonly parentProcessInstanceKey: Locator;
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

  constructor(page: Page) {
    this.page = page;
    this.activeCheckbox = this.page.getByRole('checkbox', {name: 'Active'});
    this.incidentsCheckbox = this.page.getByRole('checkbox', {
      name: 'Incidents',
    });
    this.runningInstancesCheckbox = this.page.getByRole('checkbox', {
      name: 'Running Instances',
    });
    this.completedCheckbox = this.page.getByRole('checkbox', {
      name: 'Completed',
    });
    this.canceledCheckbox = this.page.getByRole('checkbox', {
      name: 'Canceled',
    });
    this.finishedInstancesCheckbox = this.page.getByRole('checkbox', {
      name: 'Finished Instances',
    });
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

  async selectProcess(option: string) {
    await this.processNameFilter.click();
    await this.page.getByRole('option', {name: option, exact: true}).click();
  }

  async selectVersion(option: string) {
    await this.processVersionFilter.click();
    await this.page.getByRole('option', {name: option, exact: true}).click();
  }

  async selectFlowNode(option: string) {
    await this.flowNodeFilter.click();
    await this.page.getByRole('option', {name: option}).click();
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

  async clickJsonEditorModal() {
    await this.jsonEditorModalButton.click();
  }

  async closeModalWithCancel() {
    await this.dialogCancelButton.click();
  }

  async clickMultipleVariablesSwitch() {
    await this.multipleVariablesSwitch.click({force: true});
  }
}
