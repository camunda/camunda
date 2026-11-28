/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page} from '@playwright/test';

/**
 * Makes the unified Admin behave as if login were delegated to an IdP (OIDC).
 * Admin reads this from `authentication.isLoginDelegated` in the system
 * configuration API. Install it before the page loads that configuration.
 */
export async function mockOIDCModeUI(page: Page): Promise<void> {
  await page.route('**/v2/system/configuration', async (route) => {
    const response = await route.fetch();
    const configuration = await response.json();
    await route.fulfill({
      response,
      json: {
        ...configuration,
        authentication: {
          ...configuration.authentication,
          isLoginDelegated: true,
        },
      },
    });
  });
}
