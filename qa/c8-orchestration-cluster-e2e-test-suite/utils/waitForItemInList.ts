/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, Locator, Page} from '@playwright/test';
import {sleep} from './sleep';

export async function findLocatorInPaginatedList(
  page: Page,
  locator: Locator,
): Promise<boolean> {
  const maxPage = 10;
  const nextButton = page.getByRole('button', {name: 'Next page'});

  for (let i = 0; i < maxPage; i++) {
    await sleep(500);
    if (
      (await locator.count()) > 0 ||
      !(await nextButton.isVisible()) ||
      (await nextButton.isDisabled())
    ) {
      return await locator.isVisible();
    }
    await nextButton.click();
  }
  return false;
}

export const waitForItemInList = async (
  page: Page,
  item: Locator,
  options?: {
    shouldBeVisible?: boolean;
    timeout?: number;
    emptyStateLocator?: Locator;
    onAfterReload?: () => Promise<void>;
    clickNext?: boolean;
  },
) => {
  const {
    shouldBeVisible = true,
    timeout = 10000,
    emptyStateLocator,
    onAfterReload,
    clickNext = false,
  } = options || {};

  const poll = expect.poll(
    async () => {
      await page.reload();

      if (onAfterReload) {
        await onAfterReload();
      }

      if (emptyStateLocator) {
        await Promise.race([
          page
            .getByRole('cell')
            .filter({hasText: /.+/, hasNot: page.locator('div')})
            .first()
            .waitFor(),
          emptyStateLocator?.waitFor(),
        ]);
      } else {
        await page
          .getByRole('cell')
          .filter({hasText: /.+/, hasNot: page.locator('div')})
          .first()
          .waitFor();
      }

      if (clickNext) {
        return await findLocatorInPaginatedList(page, item);
      } else {
        return await item.isVisible();
      }
    },
    {timeout},
  );

  return shouldBeVisible ? await poll.toBeTruthy() : await poll.toBeFalsy();
};
