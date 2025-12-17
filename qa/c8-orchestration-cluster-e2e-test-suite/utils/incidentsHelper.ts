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
  flowNodeId: string,
  expectedCount: number,
  timeout = 120000,
): Promise<void> {
  const requestHeaders = getAuthHeaders();

  await expect
    .poll(
      async () => {
        const response = await request.post('v1/flownode-instances/search', {
          ...requestHeaders,
          data: {
            filter: {
              processInstanceKey,
              flowNodeId,
              type: 'SERVICE_TASK',
              incident: true,
            },
          },
        });

        const flowNodeInstances = await response.json();
        return flowNodeInstances.total;
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
        const response = await request.post('/api/process-instances', {
          ...requestHeaders,
          data: {
            query: {
              active: true,
              running: true,
              incidents: true,
              completed: true,
              finished: true,
              canceled: true,
              ids: instanceIds,
            },
            pageSize: 50,
          },
        });
        const instances = await response.json();
        return instances.totalCount;
      },
      {timeout},
    )
    .toBe(expectedCount);

  await sleep(2000);
}
