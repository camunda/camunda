/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, APIRequestContext} from '@playwright/test';
import {credentials, authHeaders, buildUrl} from './http';

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
        buildUrl(
          `/process-instances/${processInstanceKey}/incident-resolution`,
        ),
        {
          ...requestHeaders,
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
          buildUrl('/batch-operations/search'),
          {
            ...requestHeaders,
            data: {
              filter: {
                operationType: 'RESOLVE_INCIDENT',
              },
            },
          },
        );
        expect(response.status()).toBe(200);
        const body = await response.json();
        return body.items.length;
      },
      {timeout: 30000},
    )
    .toBeGreaterThanOrEqual(count);
}
