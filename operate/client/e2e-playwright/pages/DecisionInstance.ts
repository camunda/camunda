/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page} from '@playwright/test';

export class DecisionInstance {
  private page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async closeDrdPanel() {
    await this.page.getByRole('button', {name: /close drd panel/i}).click();
  }
}
