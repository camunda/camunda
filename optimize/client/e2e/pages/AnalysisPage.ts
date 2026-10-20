/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

export class AnalysisPage {
  readonly result: Locator;

  constructor(private readonly page: Page) {
    this.result = page.getByRole('main');
  }

  async gotoBranchAnalysis(): Promise<void> {
    await this.page.goto('/#/analysis/branchAnalysis');
  }

  async selectProcess(name: string): Promise<void> {
    await this.page.getByRole('button', {name: 'Select process Open menu'}).click();
    await this.page.getByRole('combobox', {name: 'Name'}).click();
    await this.page.getByRole('option', {name}).click();
    await this.page.keyboard.press('Escape');
  }

  // Flow nodes are picked in the BPMN diagram by their id.
  async selectFlowNode(flowNodeId: string): Promise<void> {
    await this.page.locator(`.djs-element[data-element-id="${flowNodeId}"]`).click();
  }
}
