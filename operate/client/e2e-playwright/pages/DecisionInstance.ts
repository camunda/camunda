/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Locator, Page} from '@playwright/test';
import {Paths} from 'modules/Routes';

export class DecisionInstance {
  private page: Page;
  readonly decisionPanel: Locator;
  readonly drdPanel: Locator;
  readonly inputVariables: Locator;
  readonly outputVariables: Locator;
  readonly resultTab: Locator;
  readonly inputsOutputsTab: Locator;
  readonly result: Locator;

  constructor(page: Page) {
    this.page = page;
    this.decisionPanel = this.page.getByRole('region', {
      name: 'decision panel',
    });
    this.drdPanel = this.page.getByRole('region', {name: 'drd panel'});
    this.inputVariables = this.page.getByRole('region', {
      name: 'input variables',
    });
    this.outputVariables = this.page.getByRole('region', {
      name: 'output variables',
    });
    this.result = this.page.getByTestId('results-json-viewer');
    this.resultTab = this.page.getByRole('tab', {name: /result/i});
    this.inputsOutputsTab = this.page.getByRole('tab', {
      name: /inputs and outputs/i,
    });
  }

  async closeDrdPanel() {
    await this.drdPanel.getByRole('button', {name: /close drd panel/i}).click();
  }

  async navigateToDecisionInstance({
    decisionInstanceKey,
  }: {
    decisionInstanceKey: string;
  }) {
    await this.page.goto('.' + Paths.decisionInstance(decisionInstanceKey));
  }
}
