/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { expect, Locator, Page } from "@playwright/test";

export const waitForItemInList = async (
  page: Page,
  item: Locator,
  shouldBeVisible: boolean = true,
  timeout: number = 10000,
) => {
  const poll = expect.poll(
    async () => {
      await page.reload();
      await page.getByRole("table").waitFor();
      await page.getByRole("cell").first().waitFor();
      const isVisible = await item.isVisible();

      return isVisible;
    },
    {
      timeout,
    },
  );

  if (shouldBeVisible) {
    return await poll.toBeTruthy();
  }

  return await poll.toBeFalsy();
};
