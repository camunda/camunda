/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Route} from '@playwright/test';

export async function mockOIDCModeUI(page: Page): Promise<void> {
  await page.route('**/config.js', async (route: Route) => {
    const response = await route.fetch();
    const originalBody = await response.text();

    let config: Record<string, string> = {};

    const match = originalBody.match(
      /window\.clientConfig\s*=\s*(\{[\s\S]*\});?/,
    );

    if (match) {
      config = eval('(' + match[1] + ')');
    }

    Object.keys(config).forEach((key) => {
      if (key.includes('IS_OIDC')) {
        config[key] = 'true';
      }
    });

    await route.fulfill({
      status: 200,
      contentType: 'application/javascript',
      body: `window.clientConfig = ${JSON.stringify(config)};`,
    });
  });
}
