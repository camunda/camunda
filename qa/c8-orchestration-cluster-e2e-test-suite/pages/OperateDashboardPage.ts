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
  readonly runningInstancesText: Locator;
  readonly totalInstancesLink: Locator;
  readonly activeInstancesLink: Locator;
  readonly incidentInstancesLink: Locator;

  constructor(page: Page) {
    this.page = page;
    this.metricPanel = page.getByTestId('metric-panel');
    this.runningInstancesText = page.getByText(
      /running process instances in total/i,
    );
    this.totalInstancesLink = page.getByTestId('total-instances-link');
    this.activeInstancesLink = page.getByTestId('active-instances-link');
    this.incidentInstancesLink = page.getByTestId('incident-instances-link');
  }

  async gotoDashboardPage(
    options?: Parameters<Page['goto']>[1],
  ): Promise<void> {
    await this.page.goto(
      `${process.env.CORE_APPLICATION_OPERATE_URL}/operate`,
      options,
    );
  }

  async clickActiveInstancesLink(): Promise<void> {
    await this.activeInstancesLink.click();
  }

  async clickIncidentInstancesLink(): Promise<void> {
    await this.incidentInstancesLink.click();
  }
}
