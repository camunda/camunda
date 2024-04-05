/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page} from '@playwright/test';

class TestSetupPage {
  private page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async goToLoginPage() {
    await this.page.goto('/login');
  }
}
export {TestSetupPage};
