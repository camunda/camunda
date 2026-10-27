/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, Locator} from '@playwright/test';

/**
 * Asserts whether a Locator is in the viewport.
 *
 * @param locator - The Playwright Locator to check
 * @param shouldBeVisible - true if expected to be in the viewport, false if expected to be out
 */
export async function expectInViewport(
  locator: Locator,
  shouldBeVisible: boolean,
) {
  const isInViewport = await locator.evaluate((el) => {
    const rect = el.getBoundingClientRect();
    return (
      rect.top < window.innerHeight &&
      rect.bottom > 0 &&
      rect.left < window.innerWidth &&
      rect.right > 0
    );
  });
  expect(isInViewport).toBe(shouldBeVisible);
}
