/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';
import {Paths} from 'modules/Routes';

export class Dashboard {
  private page: Page;
  readonly metricPanel: Locator;

  constructor(page: Page) {
    this.page = page;
    this.metricPanel = page.getByTestId('metric-panel');
  }

  async navigateToDashboard(options?: Parameters<Page['goto']>[1]) {
    await this.page.goto(Paths.dashboard(), options);
  }
}
