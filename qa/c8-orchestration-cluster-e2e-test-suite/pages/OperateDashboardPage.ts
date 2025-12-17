/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

export class OperateDashboardPage {
  private page: Page;
  readonly metricPanel: Locator;
  readonly incidentInstancesBadge: Locator;
  readonly activeInstancesBadge: Locator;
  readonly totalInstancesLink: Locator;
  readonly activeInstancesLink: Locator;
  readonly incidentInstancesLink: Locator;
  readonly instancesByProcess: Locator;
  readonly incidentsByError: Locator;

  constructor(page: Page) {
    this.page = page;
    this.metricPanel = page.getByTestId('metric-panel');
    this.incidentInstancesBadge = page
      .getByTestId('incident-instances-badge')
      .first();
    this.activeInstancesBadge = page
      .getByTestId('active-instances-badge')
      .first();
    this.totalInstancesLink = page.getByTestId('total-instances-link');
    this.activeInstancesLink = page.getByTestId('active-instances-link');
    this.incidentInstancesLink = page.getByTestId('incident-instances-link');
    this.instancesByProcess = page.getByTestId('instances-by-process');
    this.incidentsByError = page.getByTestId('incident-byError');
  }

  async gotoDashboardPage(options?: Parameters<Page['goto']>[1]) {
    await this.page.goto('/operate', options);
  }

  getInstancesByProcessItem(index: number): Locator {
    return this.page.getByTestId(`instances-by-process-${index}`);
  }

  getIncidentsByErrorItem(index: number): Locator {
    return this.page.getByTestId(`incident-byError-${index}`);
  }
}
