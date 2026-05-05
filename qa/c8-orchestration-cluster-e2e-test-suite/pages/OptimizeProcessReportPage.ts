/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';

export class OptimizeProcessReportPage {
  private page: Page;
  readonly reportName: Locator;
  readonly reportRenderer: Locator;
  readonly controlPanel: Locator;
  readonly reportTable: Locator;
  readonly reportChart: Locator;
  readonly reportNumber: Locator;
  readonly saveButton: Locator;
  readonly warningMessage: Locator;

  constructor(page: Page) {
    this.page = page;
    this.reportName = page.locator('.ReportView .name');
    this.reportRenderer = page.locator('.ReportRenderer').nth(0);
    this.controlPanel = page.locator('.ReportControlPanel');
    this.reportTable = this.reportRenderer.locator('.Table');
    this.reportChart = this.reportRenderer.locator('canvas');
    this.reportNumber = this.reportRenderer.locator('.Number .data');
    this.saveButton = page.locator('button').filter({hasText: 'Save'});
    this.warningMessage = page.locator('.Message--warning');
  }

  dropdownOption(text: string): Locator {
    return this.page
      .locator('.Dropdown.is-open .DropdownOption')
      .filter({hasText: text});
  }

  configurationOption(text: string): Locator {
    return this.page
      .locator('.Configuration .DropdownOption')
      .filter({hasText: text});
  }

  flowNode(id: string): Locator {
    return this.page.locator(`.BPMNDiagram [data-element-id="${id}"]`);
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }
}
