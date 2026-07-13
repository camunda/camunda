/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, request} from '@playwright/test';

// Populates the Operations panel with one batch operation per instance so the
// "Infinite scrolling" test has enough entries to page through. The operation
// type is CANCEL_PROCESS_INSTANCE, one per dedicated throwaway instance: each
// cancel completes and terminates its instance, so the operations settle
// instead of perpetually requeuing on Operate's shared operation-executor.
// RESOLVE_INCIDENT on a single unresolvable-incident instance was used before;
// it never completed and saturated the executor for the whole session, starving
// operation-success assertions in unrelated specs.
export async function createDemoOperations(
  processInstanceKeys: string[],
): Promise<void> {
  const count = processInstanceKeys.length;
  const baseURL =
    process.env.CORE_APPLICATION_OPERATE_URL ?? 'http://localhost:8081';
  const username = process.env.TEST_USERNAME || 'demo';
  const password = process.env.TEST_PASSWORD || 'demo';

  const apiContext = await request.newContext({baseURL});
  await apiContext.post('/api/login', {
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    form: {username, password},
  });

  try {
    // Wait until each instance is indexed in Operate before creating operations.
    for (const processInstanceKey of processInstanceKeys) {
      await expect
        .poll(
          async () => {
            const response = await apiContext.get(
              `/v1/process-instances/${processInstanceKey}`,
            );
            return response.status();
          },
          {timeout: 60000},
        )
        .toBe(200);
    }

    for (const processInstanceKey of processInstanceKeys) {
      const response = await apiContext.post(
        `/api/process-instances/${processInstanceKey}/operation`,
        {
          data: {operationType: 'CANCEL_PROCESS_INSTANCE'},
        },
      );
      if (!response.ok()) {
        const errorBody = await response.text();
        console.error(
          `Failed to create operation for ${processInstanceKey}: ${response.status()} ${response.statusText()}`,
        );
        console.error(`Response body: ${errorBody}`);
      }
      expect(
        response.ok(),
        `Operation for ${processInstanceKey} failed: ${response.status()} ${response.statusText()}`,
      ).toBeTruthy();
    }

    await expect
      .poll(
        async () => {
          const response = await apiContext.post('/api/batch-operations', {
            data: {pageSize: count},
          });
          const operations = await response.json();
          return operations.length;
        },
        {timeout: 30000},
      )
      .toBeGreaterThanOrEqual(count);
  } finally {
    await apiContext.dispose();
  }
}
