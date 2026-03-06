/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, request, APIRequestContext} from '@playwright/test';

export async function createDemoOperations(
  _request: APIRequestContext,
  processInstanceKey: string,
  count: number,
): Promise<void> {
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
    // Wait until the process instance is indexed in Operate before creating operations
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

    for (let index = 0; index < count; index++) {
      const response = await apiContext.post(
        `/api/process-instances/${processInstanceKey}/operation`,
        {
          data: {operationType: 'RESOLVE_INCIDENT'},
        },
      );
      if (!response.ok()) {
        const errorBody = await response.text();
        console.error(
          `Failed to create operation ${index + 1}/${count}: ${response.status()} ${response.statusText()}`,
        );
        console.error(`Process Instance Key: ${processInstanceKey}`);
        console.error(`Response body: ${errorBody}`);
      }
      expect(
        response.ok(),
        `Operation ${index + 1}/${count} failed: ${response.status()} ${response.statusText()}`,
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
