/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

class OperateDecisionsPage {
  private page: Page;
  readonly decisionViewer: Locator;
  readonly decisionNameFilter: Locator;
  readonly decisionVersionFilter: Locator;
  readonly viewDecisionInstanceLink: (decisionInstanceId: string) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.decisionViewer = page.getByTestId('decision-viewer');
    this.decisionNameFilter = page.getByRole('combobox', {name: 'Name'});
    this.decisionVersionFilter = page.getByRole('combobox', {
      name: 'Version',
    });
    this.viewDecisionInstanceLink = (decisionInstanceId: string) =>
      page.getByRole('link', {
        name: `View decision instance ${decisionInstanceId}`,
      });
  }

  async selectDecisionName(name: string): Promise<void> {
    await this.decisionNameFilter.click();
    await this.page.getByRole('option', {name, exact: true}).click();
  }

  async selectVersion(version: string): Promise<void> {
    await this.decisionVersionFilter.click();
    await this.page.getByRole('option', {name: version, exact: true}).click();
  }

  async clearComboBox(): Promise<void> {
    await this.page.getByRole('button', {name: 'Clear selected item'}).click();
  }

  async clickViewDecisionInstanceLink(
    decisionInstanceId: string,
  ): Promise<void> {
    await this.viewDecisionInstanceLink(decisionInstanceId).click();
  }
}

export {OperateDecisionsPage};
