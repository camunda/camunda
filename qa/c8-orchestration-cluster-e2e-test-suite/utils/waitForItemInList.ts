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
  const maxPage = 20;
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
    retries?: number;
    retryDelay?: number;
  },
) => {
  const {
    shouldBeVisible = true,
    timeout = 10000,
    emptyStateLocator,
    onAfterReload,
    clickNext = false,
    retries = 3,
    retryDelay = 1000,
  } = options || {};

  const poll = expect.poll(
    async () => {
      let lastError: Error | undefined;

      for (let retry = 0; retry <= retries; retry++) {
        try {
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

          return clickNext
            ? await findLocatorInPaginatedList(page, item)
            : await item.isVisible();
        } catch (error) {
          lastError = error instanceof Error ? error : new Error(String(error));

          if (retry === retries) {
            throw lastError;
          }

          console.log(
            `Wait attempt ${retry + 1} failed, retrying in ${retryDelay}ms...`,
          );
          await sleep(retryDelay);
        }
      }

<<<<<<< HEAD
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
          .waitFor({timeout: 20000});
      }

      if (clickNext) {
        return await findLocatorInPaginatedList(page, item);
      } else {
        return await item.isVisible();
      }
=======
      throw lastError || new Error('All retry attempts failed');
>>>>>>> cb9acd7b1df (test: identity e2e User inherits permissions through role assignment)
    },
    {timeout},
  );

  return shouldBeVisible ? await poll.toBeTruthy() : await poll.toBeFalsy();
};
