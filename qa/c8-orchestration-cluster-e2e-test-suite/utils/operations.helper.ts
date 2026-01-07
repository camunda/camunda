/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, APIRequestContext} from '@playwright/test';
import {credentials, authHeaders} from './http';

export async function createDemoOperations(
  request: APIRequestContext,
  processInstanceKey: string,
  count: number,
): Promise<void> {
  const requestHeaders = {
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(credentials.accessToken),
    },
  };

  await Promise.all(
    [...new Array(count)].map(async (_, index) => {
      const response = await request.post(
        `${credentials.baseUrl}/api/process-instances/${processInstanceKey}/operation`,
        {
          ...requestHeaders,
          data: {
            operationType: 'RESOLVE_INCIDENT',
          },
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
      return response;
    }),
  );

  await expect
    .poll(
      async () => {
        const response = await request.post(
          `${credentials.baseUrl}/api/batch-operations`,
          {
            ...requestHeaders,
            data: {
              pageSize: count,
            },
          },
        );
        const operations = await response.json();
        return operations.length;
      },
      {timeout: 30000},
    )
    .toBeGreaterThanOrEqual(count);
}
