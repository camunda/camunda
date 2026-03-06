/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, request, type APIRequestContext} from '@playwright/test';
import {sleep} from './sleep';

export async function waitForIncidents(
  _request: APIRequestContext,
  processInstanceKey: string,
  timeout = 120000,
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
    await expect
      .poll(
        async () => {
          const response = await apiContext.post('/v1/incidents/search', {
            data: {
              filter: {
                processInstanceKey: parseInt(processInstanceKey),
              },
            },
          });
          const incidents: {items: {state: string}[]; total: number} =
            await response.json();
          return (
            incidents.total > 0 &&
            incidents.items.filter(({state}) => state === 'PENDING').length ===
              0
          );
        },
        {timeout},
      )
      .toBeTruthy();
  } finally {
    await apiContext.dispose();
  }

  await sleep(2000);
}

export async function waitForProcessInstances(
  _request: APIRequestContext,
  instanceIds: string[],
  expectedCount: number,
  timeout = 120000,
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
    await expect
      .poll(
        async () => {
          const response = await apiContext.post('/api/process-instances', {
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
  } finally {
    await apiContext.dispose();
  }

  await sleep(2000);
}
