/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  defaultHeaders,
} from '../../../utils/http';
import {validateResponse} from '../../../json-body-assertions';

test.describe('Cluster API Tests', () => {
  test('Get Cluster Topology', async ({request}) => {
    const res = await request.get(buildUrl('/topology'), {
      headers: defaultHeaders(),
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/topology',
        method: 'GET',
        status: '200',
      },
      res,
    );
    const result = await res.json();
    expect(result.brokers).toHaveLength(1);
    // Partition count is environment-dependent (single-broker clusters can be
    // configured with 1 or more partitions). Assert health/role rather than
    // a hard-coded count to keep the test stable across environments.
    expect(result.brokers[0].partitions.length).toBeGreaterThanOrEqual(1);
    for (const partition of result.brokers[0].partitions) {
      expect(partition.health).toBe('healthy');
      expect(partition.role).toBe('leader');
    }
  });

  test('Get Cluster Topology - Unauthorized', async ({request}) => {
    const res = await request.get(buildUrl('/topology'));
    await assertUnauthorizedRequest(res);
  });

  //Skipped due to bug 43397: https://github.com/camunda/camunda/issues/43397
  test.skip('Get Cluster Status', async ({request}) => {
    const res = await request.get(buildUrl('/status'));
    await assertStatusCode(res, 204);
    const result = await res.body();
    expect(result.length).toBe(0);
  });
});
