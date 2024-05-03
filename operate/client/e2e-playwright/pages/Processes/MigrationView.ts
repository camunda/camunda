/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

export class MigrationView {
  private page: Page;

  readonly targetProcessDropdown: Locator;
  readonly targetVersionDropdown: Locator;
  readonly nextButton: Locator;
  readonly confirmButton: Locator;
  readonly summaryNotification: Locator;

  constructor(page: Page) {
    this.page = page;

    this.targetProcessDropdown = page.getByRole('combobox', {
      name: 'Target Process',
      exact: true,
    });

    this.targetVersionDropdown = page.getByRole('combobox', {
      name: 'Target Version',
    });

    this.nextButton = page.getByRole('button', {
      name: /^next$/i,
    });

    this.confirmButton = page.getByRole('button', {
      name: /^confirm$/i,
    });

    this.summaryNotification = page.getByRole('main').getByRole('status');
  }

  async selectTargetProcess(option: string) {
    await this.targetProcessDropdown.click();
    await this.page.getByRole('option', {name: option}).click();
  }

  async selectTargetVersion(option: string) {
    await this.targetVersionDropdown.click();
    await this.page.getByRole('option', {name: option}).click();
  }

  mapFlowNode({
    sourceFlowNodeName,
    targetFlowNodeName,
  }: {
    sourceFlowNodeName: string;
    targetFlowNodeName: string;
  }) {
    return this.page
      .getByLabel(`Target flow node for ${sourceFlowNodeName}`)
      .selectOption(targetFlowNodeName);
  }

  selectTargetSourceFlowNode(flowNodeName: string) {
    return this.page
      .getByRole('cell', {name: flowNodeName, exact: true})
      .click();
  }
}
