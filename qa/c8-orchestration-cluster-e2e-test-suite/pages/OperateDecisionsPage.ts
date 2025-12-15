/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

class OperateDecisionsPage {
  private page: Page;
  readonly viewDecisionInstanceLink: (decisionInstanceId: string) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.viewDecisionInstanceLink = (decisionInstanceId: string) =>
      page.getByRole('link', {
        name: `View decision instance ${decisionInstanceId}`,
      });
  }

  async clickViewDecisionInstanceLink(
    decisionInstanceId: string,
  ): Promise<void> {
    await this.viewDecisionInstanceLink(decisionInstanceId).click();
  }

  async gotoDecisionsPage(): Promise<void> {
    await this.page.goto('/decisions');
  }
}

export {OperateDecisionsPage};
