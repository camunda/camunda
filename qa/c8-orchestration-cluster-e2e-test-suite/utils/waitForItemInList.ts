/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, Locator, Page} from '@playwright/test';

export const waitForItemInList = async (
  page: Page,
  item: Locator,
  options?: {
    shouldBeVisible?: boolean;
    timeout?: number;
    emptyStateLocator?: Locator;
    clickAuthorizationsPageTab?: () => Promise<void>;
  },
) => {
  const {
    shouldBeVisible = true,
    timeout = 10000,
    emptyStateLocator,
    clickAuthorizationsPageTab,
  } = options || {};

  const poll = expect.poll(
    async () => {
      await page.reload();

      if (clickAuthorizationsPageTab) {
        await clickAuthorizationsPageTab();
      }

      if (emptyStateLocator) {
        await Promise.race([
          page.getByRole('cell').filter({hasText: /.+/}).first().waitFor(),
          emptyStateLocator?.waitFor(),
        ]);
      } else {
        await page.getByRole('cell').filter({hasText: /.+/}).first().waitFor();
      }

      return await item.isVisible();
    },
    {timeout},
  );

  return shouldBeVisible ? await poll.toBeTruthy() : await poll.toBeFalsy();
};
