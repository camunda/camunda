/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';
import {Paths} from 'modules/Routes';

export class ProcessInstance {
  private page: Page;
  readonly instanceHeader: Locator;
  readonly instanceHistory: Locator;
  readonly diagram: Locator;

  constructor(page: Page) {
    this.page = page;
    this.instanceHeader = page.getByTestId('instance-header');
    this.instanceHistory = page.getByTestId('instance-history');
    this.diagram = page.getByTestId('diagram');
  }

  async navigateToProcessInstance(id: string) {
    await this.page.goto(Paths.processInstance(id));
  }
}
