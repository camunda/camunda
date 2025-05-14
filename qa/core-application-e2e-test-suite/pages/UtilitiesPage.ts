/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, expect} from '@playwright/test';

export async function navigateToApp(
  page: Page,
  appName: string,
): Promise<void> {
  if (appName == 'operate') {
    await page.goto(
      process.env.CORE_APPLICATION_OPERATE_URL +
        '/' +
        appName.toLowerCase() +
        '/login',
    );
  } else if (appName == 'tasklist') {
    await page.goto(
      process.env.CORE_APPLICATION_TASKLIST_URL +
        '/' +
        appName.toLowerCase() +
        '/login',
    );
  }
}

export async function validateURL(page: Page, URL: RegExp): Promise<void> {
  // eslint-disable-next-line @typescript-eslint/no-floating-promises, playwright/missing-playwright-await
  expect(page).toHaveURL(URL);
}
