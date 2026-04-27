/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type APIRequestContext} from '@playwright/test';
import {sleep} from './sleep';

function getAuthHeaders() {
  const username = process.env.TEST_USERNAME || 'demo';
  const password = process.env.TEST_PASSWORD || 'demo';
  const auth = Buffer.from(`${username}:${password}`).toString('base64');
  return {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Basic ${auth}`,
    },
  };
}

export async function waitForIncidents(
  request: APIRequestContext,
  processInstanceKey: string,
  elementId: string,
  expectedCount: number,
  timeout = 120000,
): Promise<void> {
  const requestHeaders = getAuthHeaders();

  await expect
    .poll(
      async () => {
        const response = await request.post('/v2/element-instances/search', {
          ...requestHeaders,
          data: {
            filter: {
              processInstanceKey,
              elementId,
              type: 'SERVICE_TASK',
              hasIncident: true,
            },
          },
        });

        const result = await response.json();
        return result.page?.totalItems;
      },
      {timeout},
    )
    .toBe(expectedCount);

  await sleep(2000);
}

export async function waitForProcessInstances(
  request: APIRequestContext,
  instanceIds: string[],
  expectedCount: number,
  timeout = 120000,
): Promise<void> {
  const requestHeaders = getAuthHeaders();

  await expect
    .poll(
      async () => {
        let total = 0;

        for (const id of instanceIds) {
          const response = await request.post('/v2/process-instances/search', {
            ...requestHeaders,
            data: {
              filter: {
                processInstanceKey: id, // single ID per request
              },
            },
          });

          const result = await response.json();
          total += result.page?.totalItems ?? 0;
        }

        return total;
      },
      {timeout},
    )
    .toBe(expectedCount);

  await sleep(2000);
}
