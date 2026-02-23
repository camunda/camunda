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
  readonly instancesByProcess: Locator;
  readonly incidentsByError: Locator;
  readonly instancesByProcessItem: (index: number) => Locator;
  readonly incidentsByErrorItem: (index: number) => Locator;
  readonly activeInstancesBadge: Locator;
  readonly incidentInstancesBadge: Locator;
  readonly processInstancesHeading: (
    count: string | number,
    isPlural?: boolean,
  ) => Locator;
  readonly incidentLinkByType: (type: string | RegExp) => Locator;
  readonly viewInstanceLink: () => Locator;
  readonly expandRowButton: () => Locator;
  readonly incidentBadgeFromItem: (item: Locator) => Locator;
  readonly activeBadgeFromItem: (item: Locator) => Locator;
  readonly expandRowButtonFromItem: (item: Locator) => Locator;
  readonly firstLinkFromItem: (item: Locator) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.metricPanel = page.getByTestId('metric-panel');
    this.runningInstancesText = page.getByText(
      /running process instances in total/i,
    );
    this.totalInstancesLink = page.getByTestId('total-instances-link');
    this.activeInstancesLink = page.getByTestId('active-instances-link');
    this.incidentInstancesLink = page.getByTestId('incident-instances-link');
    this.instancesByProcess = page.getByTestId(
      'instances-by-process-definition',
    );
    this.incidentsByError = page.getByTestId('incident-byError');

    this.instancesByProcessItem = (index: number) =>
      page.getByTestId(`instances-by-process-definition-${index}`);

    this.incidentsByErrorItem = (index: number) =>
      page.getByTestId(`incident-byError-${index}`);

    this.activeInstancesBadge = page
      .getByTestId('active-instances-badge')
      .nth(0);

    this.incidentInstancesBadge = page
      .getByTestId('incident-instances-badge')
      .nth(0);

    this.processInstancesHeading = (count, isPlural = true) =>
      page.getByRole('heading', {
        name: `Process Instances - ${count} result${isPlural ? 's' : ''}`,
      });

    this.incidentLinkByType = (type) =>
      page.getByRole('link', {
        name: type,
      });

    this.viewInstanceLink = () =>
      page.getByRole('link', {
        name: /view instance/i,
      });

    this.expandRowButton = () =>
      page.getByRole('button', {
        name: 'Expand current row',
      });

    this.incidentBadgeFromItem = (item) =>
      item.getByTestId('incident-instances-badge');

    this.activeBadgeFromItem = (item) =>
      item.getByTestId('active-instances-badge');

    this.expandRowButtonFromItem = (item) =>
      item.getByRole('button', {
        name: 'Expand current row',
      });

    this.firstLinkFromItem = (item) => item.getByRole('link').nth(0);
  }

  async gotoDashboardPage(options?: Parameters<Page['goto']>[1]) {
    await this.page.goto('/operate', options);
  }

  async clickActiveInstancesLink(): Promise<void> {
    await this.activeInstancesLink.click();
  }

  async clickIncidentInstancesLink(): Promise<void> {
    await this.incidentInstancesLink.click();
  }

  async clickIncidentByType(type: string | RegExp): Promise<void> {
    await this.incidentLinkByType(type).click();
  }

  async clickViewInstanceLink(): Promise<void> {
    await this.viewInstanceLink().click();
  }

  async clickItem(item: Locator): Promise<void> {
    await item.click();
  }

  async expandItem(item: Locator): Promise<void> {
    await this.expandRowButtonFromItem(item).click();
  }

  async clickFirstLinkInItem(item: Locator): Promise<void> {
    await this.firstLinkFromItem(item).click();
  }
}
