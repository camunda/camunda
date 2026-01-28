/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

type OptionalFilter =
  | 'Process Instance Key'
  | 'Decision Instance Key(s)'
  | 'Evaluation Date Range';

interface SearchParams {
  evaluated?: string;
  failed?: string;
  [key: string]: string | undefined;
}

class OperateDecisionsPage {
  private page: Page;
  readonly viewDecisionInstanceLink: (decisionInstanceId: string) => Locator;
  readonly decisionNameFilter: Locator;
  readonly decisionVersionFilter: Locator;
  readonly decisionViewer: Locator;
  readonly decisionInstanceKeysFilter: Locator;
  readonly filterRegion: Locator;
  readonly clearSelectedItemButton: Locator;
  readonly moreFiltersButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.viewDecisionInstanceLink = (decisionInstanceId: string) =>
      page.getByRole('link', {
        name: `View decision instance ${decisionInstanceId}`,
      });

    this.decisionNameFilter = page.getByRole('combobox', {
      name: 'Name',
    });
    this.decisionVersionFilter = page.getByRole('combobox', {
      name: 'Version',
    });
    this.decisionInstanceKeysFilter = page.getByLabel(
      /^decision instance key\(s\)$/i,
    );
    this.decisionViewer = page.getByTestId('decision-viewer');
    this.filterRegion = page.getByRole('region', {name: /filter/i});
    this.clearSelectedItemButton = page.getByRole('button', {
      name: 'Clear selected item',
    });
    this.moreFiltersButton = page.getByRole('button', {name: 'More Filters'});
  }

  async clickViewDecisionInstanceLink(
    decisionInstanceId: string,
  ): Promise<void> {
    await this.viewDecisionInstanceLink(decisionInstanceId).click();
  }

  async gotoDecisionsPage(options?: {
    searchParams?: SearchParams;
  }): Promise<void> {
    if (!options?.searchParams) {
      await this.page.goto('/decisions');
      return;
    }

    const searchParams = new URLSearchParams();
    Object.entries(options.searchParams).forEach(([key, value]) => {
      if (value !== undefined) {
        searchParams.append(key, value);
      }
    });

    await this.page.goto(`/decisions?${searchParams.toString()}`);
  }

  async selectDecisionName(option: string): Promise<void> {
    await this.decisionNameFilter.click();
    await this.filterRegion
      .getByRole('option', {name: option, exact: true})
      .click();
  }

  async selectVersion(option: string): Promise<void> {
    await this.decisionVersionFilter.click();
    await this.filterRegion
      .getByRole('option', {name: option, exact: true})
      .click();
  }

  async clearComboBox(): Promise<void> {
    await this.clearSelectedItemButton.click();
  }

  async displayOptionalFilter(filterName: OptionalFilter): Promise<void> {
    await this.moreFiltersButton.click();
    await this.page
      .getByRole('menuitem', {
        name: filterName,
      })
      .click();
  }
}

export {OperateDecisionsPage};
