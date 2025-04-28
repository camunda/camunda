/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page} from '@playwright/test';

export async function ensurePageLoaded(page: Page): Promise<void> {
  for (let attempt = 0; attempt < 2; attempt++) {
    // Allow 1 retry
    const state = await page.evaluate(() => document.readyState);
    if (state === 'complete') {
      return;
    }
    console.warn(
      `Page not fully loaded (state: ${state}), reloading... (Attempt ${attempt + 1})`,
    );
    await page.reload();
    await page.waitForLoadState('load');
  }

  const finalState = await page.evaluate(() => document.readyState);
  if (finalState !== 'complete') {
    throw new Error('Page did not fully load after retries.');
  }
}
