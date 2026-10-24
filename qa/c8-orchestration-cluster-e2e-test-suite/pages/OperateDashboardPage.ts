/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

const ACTIVE_INSTANCES_BADGE = /^\d+ active instances?$/;
const INCIDENT_INSTANCES_BADGE = /^\d+ instances? with incidents$/;

export class OperateDashboardPage {
  private page: Page;
  readonly runningInstancesText: Locator;
  readonly totalInstancesLink: Locator;
  readonly activeInstancesLink: Locator;
  readonly incidentInstancesLink: Locator;
  readonly instancesByProcess: Locator;
  readonly incidentsByError: Locator;
  readonly instancesByProcessItemByName: (name: string) => Locator;
  readonly incidentsByErrorItemByMessage: (message: string | RegExp) => Locator;
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
  readonly drainingIndicatorFromItem: (item: Locator) => Locator;
  readonly expandRowButtonFromItem: (item: Locator) => Locator;
  readonly firstLinkFromItem: (item: Locator) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.runningInstancesText = page.getByText(
      /running process instances in total/i,
    );
    this.totalInstancesLink = page.getByRole('link', {
      name: /running process instances in total$/i,
    });
    this.activeInstancesLink = page.getByRole('link', {
      name: /^active process instances$/i,
    });
    this.incidentInstancesLink = page.getByRole('link', {
      name: /^process instances with incident$/i,
    });
    this.instancesByProcess = page.getByRole('table').filter({
      has: page.getByRole('columnheader', {
        name: 'instance',
        exact: true,
        includeHidden: true,
      }),
    });
    this.incidentsByError = page.getByRole('table').filter({
      has: page.getByRole('columnheader', {
        name: 'incident',
        exact: true,
        includeHidden: true,
      }),
    });

    this.instancesByProcessItemByName = (name: string) =>
      this.instancesByProcess.getByRole('row').filter({hasText: name});

    this.incidentsByErrorItemByMessage = (message: string | RegExp) =>
      this.incidentsByError.getByRole('row').filter({hasText: message});

    this.activeInstancesBadge = page
      .getByRole('img', {name: ACTIVE_INSTANCES_BADGE})
      .nth(0);

    this.incidentInstancesBadge = page
      .getByRole('img', {name: INCIDENT_INSTANCES_BADGE})
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
      item.getByRole('img', {name: INCIDENT_INSTANCES_BADGE});

    this.activeBadgeFromItem = (item) =>
      item.getByRole('img', {name: ACTIVE_INSTANCES_BADGE});

    this.drainingIndicatorFromItem = (item) =>
      item.getByLabel(/scheduled for deletion/);

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
