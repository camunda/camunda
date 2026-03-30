/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIResponse} from '@playwright/test';
import {sleep} from './sleep';

/**
 * Polls an HTTP request until it returns a target status code or a
 * non-retryable status is received.
 *
 * Useful for eventually-consistent endpoints where the resource may not
 * be immediately visible after creation.
 *
 * @param fn          – A function that fires the HTTP request and returns its APIResponse.
 * @param target      – The status code that signals success (e.g. 200, 204).
 * @param retryOn     – Status codes that should trigger a retry (e.g. [404]).
 *                      Any status not in `retryOn` and not equal to `target` aborts immediately.
 * @param maxAttempts – Maximum number of polling attempts (default: 10).
 * @param intervalMs  – Milliseconds to wait between attempts (default: 3000).
 * @returns The APIResponse that matched `target`.
 * @throws If the target status is never received within the allowed attempts,
 *         or if an unexpected (non-retryable) status is encountered.
 */
export async function pollForStatus(
  fn: () => Promise<APIResponse>,
  target: number,
  retryOn: number[] = [404],
  {maxAttempts = 10, intervalMs = 3000} = {},
): Promise<APIResponse> {
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const res = await fn();
    const status = res.status();

    if (status === target) {
      return res;
    }

    if (!retryOn.includes(status)) {
      const body = await res.text().catch(() => '<no-body>');
      throw new Error(
        `pollForStatus: unexpected status ${status} (expected ${target}, retryable: [${retryOn}]). Body: ${body}`,
      );
    }

    if (attempt < maxAttempts) {
      await sleep(intervalMs);
    }
  }

  throw new Error(
    `pollForStatus: did not receive status ${target} after ${maxAttempts} attempts`,
  );
}
