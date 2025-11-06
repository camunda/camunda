/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

export class OperateOperationPanelPage {
  private page: Page;
  readonly operationIdField: Locator;
  readonly expandButton: Locator;
  readonly collapseButton: Locator;
  
  constructor(page: Page) {
    this.page = page;
    this.operationIdField = page.getByTestId('operation-id');
    this.expandButton = page.getByRole('button', { name: 'Expand Operations' })
    this.collapseButton = page.getByRole('button', { name: 'Collapse Operations' })
  }

  async selectFirstOperationItem(): Promise<void> {
    await this.operationIdField.first().click( {timeout: 10000} );
  }

  async expandOperationIdField(): Promise<void> {
    await this.expandButton.click();
  }

  async collapseOperationIdField(): Promise<void> {
    await this.collapseButton.click();
  }

  

}