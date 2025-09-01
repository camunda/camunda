/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, Page} from '@playwright/test';

export async function verifyAccess(
  page: Page,
  shouldHaveAccess: boolean = true,
  appName?: string,
) {
  if (appName) {
    await page.goto(`${process.env.CORE_APPLICATION_URL}/${appName}/`);
  }

  if (shouldHaveAccess) {
    await expect(page).not.toHaveURL(/forbidden/);
    if (appName) {
      await expect(page).toHaveURL(new RegExp(appName));
    }
  } else {
    await expect(page).toHaveURL(/forbidden/);
  }
}
