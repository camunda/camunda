/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type APIRequestContext} from '@playwright/test';
import {sleep} from './sleep';

export async function waitForIncidents(
  request: APIRequestContext,
  processInstanceKey: string,
  flowNodeId: string,
  expectedCount: number,
  timeout = 120000,
): Promise<void> {
  const username = process.env.TEST_USERNAME || 'demo';
  const password = process.env.TEST_PASSWORD || 'demo';
  const auth = Buffer.from(`${username}:${password}`).toString('base64');
  const requestHeaders = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Basic ${auth}`,
    },
  };

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
