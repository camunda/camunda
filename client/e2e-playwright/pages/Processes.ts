/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator, expect} from '@playwright/test';
import {convertToQueryString} from '../utils/convertToQueryString';
import {Paths} from 'modules/Routes';

type OptionalFilter =
  | 'Variable'
  | 'Process Instance Key(s)'
  | 'Parent Process Instance Key'
  | 'Operation Id'
  | 'Error Message'
  | 'Start Date Range'
  | 'End Date Range';

export class Processes {
  private page: Page;
  readonly activeCheckbox: Locator;
  readonly incidentsCheckbox: Locator;
  readonly runningInstancesCheckbox: Locator;
  readonly completedCheckbox: Locator;
  readonly canceledCheckbox: Locator;
  readonly finishedInstancesCheckbox: Locator;
  readonly processNameFilter: Locator;
  readonly processInstanceKeysFilter: Locator;
  readonly flowNodeFilter: Locator;
  readonly operationSpinner: Locator;
  readonly operationIdFilter: Locator;
  readonly resetFiltersButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.activeCheckbox = page.getByRole('checkbox', {name: 'Active'});
    this.incidentsCheckbox = page.getByRole('checkbox', {name: 'Incidents'});
    this.runningInstancesCheckbox = page.getByRole('checkbox', {
      name: 'Running Instances',
    });
    this.completedCheckbox = page.getByRole('checkbox', {name: 'Completed'});
    this.canceledCheckbox = page.getByRole('checkbox', {name: 'Canceled'});
    this.finishedInstancesCheckbox = page.getByRole('checkbox', {
      name: 'Finished Instances',
    });

    this.processNameFilter = page.getByRole('combobox', {
      name: 'Name',
    });

    this.processInstanceKeysFilter = page.getByLabel(
      /^process instance key\(s\)$/i,
    );

    this.flowNodeFilter = page.getByRole('combobox', {
      name: /flow node/i,
    });

    this.operationSpinner = page.getByTestId('operation-spinner');
    this.operationIdFilter = page.getByRole('textbox', {
      name: /operation id/i,
    });
    this.resetFiltersButton = page.getByRole('button', {
      name: /reset filters/i,
    });
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
    await this.page.getByRole('button', {name: 'More Filters'}).click();
    await this.page.getByText(filterName).click();
  }

  async selectProcess(option: string) {
    const processNameFilter = this.page.getByRole('combobox', {
      name: 'Name',
    });
    await processNameFilter.click();
    await this.page.getByTestId('expanded-panel').getByText(option).click();
  }

  async navigateToProcesses(
    searchParams?: Parameters<typeof convertToQueryString>[0],
  ) {
    if (searchParams === undefined) {
      await this.page.goto(Paths.processes());
      return;
    }

    await this.page.goto(
      `${Paths.processes()}?${convertToQueryString(searchParams)}`,
    );
  }
}
