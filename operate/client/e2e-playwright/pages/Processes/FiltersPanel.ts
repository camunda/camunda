/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page, type Locator, expect} from '@playwright/test';

type OptionalFilter =
  | 'Variable'
  | 'Process Instance Key(s)'
  | 'Parent Process Instance Key'
  | 'Operation Id'
  | 'Error Message'
  | 'Start Date Range'
  | 'End Date Range'
  | 'Failed job but retries left';

export class FiltersPanel {
  private page: Page;
  readonly panel: Locator;
  readonly activeCheckbox: Locator;
  readonly incidentsCheckbox: Locator;
  readonly runningInstancesCheckbox: Locator;
  readonly completedCheckbox: Locator;
  readonly canceledCheckbox: Locator;
  readonly finishedInstancesCheckbox: Locator;
  readonly processNameFilter: Locator;
  readonly processVersionFilter: Locator;
  readonly processInstanceKeysFilter: Locator;
  readonly parentProcessInstanceKey: Locator;
  readonly flowNodeFilter: Locator;
  readonly operationIdFilter: Locator;
  readonly resetFiltersButton: Locator;
  readonly errorMessageFilter: Locator;
  readonly startDateFilter: Locator;
  readonly variableNameFilter: Locator;
  readonly variableValueFilter: Locator;
  readonly multipleVariablesSwitch: Locator;

  constructor(page: Page) {
    this.page = page;
    this.panel = page.getByRole('region', {name: 'Filter'});
    this.activeCheckbox = this.panel.getByRole('checkbox', {name: 'Active'});
    this.incidentsCheckbox = this.panel.getByRole('checkbox', {
      name: 'Incidents',
    });
    this.runningInstancesCheckbox = this.panel.getByRole('checkbox', {
      name: 'Running Instances',
    });
    this.completedCheckbox = this.panel.getByRole('checkbox', {
      name: 'Completed',
    });
    this.canceledCheckbox = this.panel.getByRole('checkbox', {
      name: 'Canceled',
    });
    this.finishedInstancesCheckbox = this.panel.getByRole('checkbox', {
      name: 'Finished Instances',
    });

    this.processNameFilter = this.panel.getByRole('combobox', {
      name: 'Name',
    });

    this.processVersionFilter = this.panel.getByRole('combobox', {
      name: 'Version',
    });

    this.processInstanceKeysFilter = this.panel.getByLabel(
      /^process instance key\(s\)$/i,
    );

    this.parentProcessInstanceKey = page.getByRole('textbox', {
      name: /parent process instance key/i,
    });

    this.flowNodeFilter = this.panel.getByRole('combobox', {
      name: /element/i,
    });

    this.operationIdFilter = this.panel.getByRole('textbox', {
      name: /operation id/i,
    });
    this.resetFiltersButton = this.panel.getByRole('button', {
      name: /reset filters/i,
    });

    this.errorMessageFilter = this.panel.getByRole('textbox', {
      name: /error message/i,
    });

    this.startDateFilter = this.panel.getByRole('textbox', {
      name: /start date range/i,
    });

    this.variableNameFilter = this.panel.getByRole('textbox', {
      name: /name/i,
    });

    this.variableValueFilter = this.panel.getByRole('textbox', {
      name: /value/i,
    });

    this.multipleVariablesSwitch = this.panel.getByText(/^multiple$/i);
  }

  async validateCheckedState({
    checked,
    unChecked,
  }: {
    checked: Array<Locator>;
    unChecked: Array<Locator>;
  }) {
    checked.forEach(async (filter) => {
      await expect(filter).toBeChecked();
    });
    unChecked.forEach(async (filter) => {
      await expect(filter).not.toBeChecked();
    });
  }

  async displayOptionalFilter(filterName: OptionalFilter) {
    await this.panel.getByRole('button', {name: 'More Filters'}).click();
    await this.page
      .getByRole('menuitem', {
        name: filterName,
      })
      .click();
  }

  async removeOptionalFilter(filterName: OptionalFilter) {
    await this.panel.getByLabel(filterName, {exact: true}).hover();
    await this.panel.getByLabel(`Remove ${filterName} Filter`).click();
  }

  async selectProcess(option: string) {
    await this.processNameFilter.click();
    await this.panel.getByRole('option', {name: option, exact: true}).click();
  }

  async selectVersion(option: string) {
    await this.processVersionFilter.click();
    await this.panel.getByRole('option', {name: option, exact: true}).click();
  }

  async selectFlowNode(option: string) {
    await this.flowNodeFilter.click();
    await this.panel.getByRole('option', {name: option}).click();
  }

  pickDateTimeRange = async ({
    fromDay,
    toDay,
    fromTime,
    toTime,
  }: {
    fromDay: string;
    toDay: string;
    fromTime?: string;
    toTime?: string;
  }) => {
    await expect(this.page.getByRole('dialog')).toBeVisible();

    const date = new Date();

    const monthName = date.toLocaleString('default', {month: 'long'});
    const year = date.getFullYear();

    await this.page.getByText('From date').click();
    await this.page.getByLabel(`${monthName} ${fromDay}, ${year}`).click();
    await this.page.getByLabel(`${monthName} ${toDay}, ${year}`).click();

    if (fromTime !== undefined) {
      await this.page.getByTestId('fromTime').clear();
      await this.page.getByTestId('fromTime').type(fromTime);
    }

    if (toTime !== undefined) {
      await this.page.getByTestId('toTime').clear();
      await this.page.getByTestId('toTime').type(toTime);
    }
  };
}
