/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';

export class IdentityMcpProcessesPage {
  private page: Page;
  readonly mcpProcessesTable: Locator;
  readonly mcpProcessesHeading: Locator;
  readonly searchInput: Locator;
  readonly allMcpProcessRows: Locator;

  constructor(page: Page) {
    this.page = page;
    this.mcpProcessesTable = page.getByRole('table');
    this.mcpProcessesHeading = page.getByRole('heading', {
      name: 'MCP Processes',
    });
    this.searchInput = page.getByPlaceholder('Search by tool name');
    this.allMcpProcessRows = this.mcpProcessesTable.getByRole('row');
  }

  async navigateToMcpProcesses(): Promise<void> {
    await this.page.goto(relativizePath(Paths.mcpProcesses()));
  }

  getRowByToolName(toolName: string): Locator {
    return this.mcpProcessesTable.getByRole('row').filter({hasText: toolName});
  }
}
