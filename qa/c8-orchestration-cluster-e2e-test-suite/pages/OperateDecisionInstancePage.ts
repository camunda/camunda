/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

class OperateDecisionInstancePage {
  private page: Page;
  readonly decisionPanel: Locator;
  readonly popover: Locator;
  readonly closeDrdPanelButton: Locator;
  readonly instanceHeader: Locator;
  readonly viewProcessInstanceLink: (processInstanceKey: string) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.decisionPanel = page.getByTestId('decision-panel');
    this.popover = page.getByTestId('popover');
    this.closeDrdPanelButton = page.getByRole('button', {
      name: /close drd panel/i,
    });
    this.instanceHeader = page.getByTestId('instance-header');
    this.viewProcessInstanceLink = (processInstanceKey: string) =>
      page.getByRole('link', {
        name: `View process instance ${processInstanceKey}`,
      });
  }

  async closeDrdPanel(): Promise<void> {
    await this.closeDrdPanelButton.click();
  }

  async getDecisionInstanceId(): Promise<string> {
    const url = this.page.url();

    const match = url.match(/\/decisions\/([^/?]+)/);
    if (match && match[1]) {
      const decisionInstanceId = match[1];
      return decisionInstanceId;
    }

    throw new Error(`Could not extract decision instance ID from URL: ${url}`);
  }

  async gotoDecisionInstancePage(options: {id: string}): Promise<void> {
    await this.page.goto(`/decisions/${options.id}`);
  }

  async clickViewProcessInstanceLink(
    processInstanceKey: string,
  ): Promise<void> {
    await this.viewProcessInstanceLink(processInstanceKey).click();
  }
}

export {OperateDecisionInstancePage};
