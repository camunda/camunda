/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertRequiredFields,
  assertUnauthorizedRequest,
  buildUrl,
  defaultHeaders,
} from '../../../utils/http';
import {
  brokerResponseFields,
  clusterTopologyResponseFields,
  partitionsResponseFields,
} from '../../../utils/beans/requestBeans';

test.describe('Cluster API Tests', () => {
  test('Get Cluster Topology', async ({request}) => {
    // eslint-disable-next-line playwright/no-conditional-in-test
    if (process.env.FORWARD_COMPAT_MODE === 'true') {
      test.skip(
        true,
        'Skipped in forward-compat mode - partition count differs on target server',
      );
    }
    const res = await request.get(buildUrl('/topology'), {
      headers: defaultHeaders(),
    });
    expect(res.status()).toBe(200);
    const result = await res.json();
    assertRequiredFields(result, clusterTopologyResponseFields);
    expect(result.brokers).toHaveLength(1);
    assertRequiredFields(result.brokers[0], brokerResponseFields);
    expect(result.brokers[0].partitions).toHaveLength(1);
    assertRequiredFields(
      result.brokers[0].partitions[0],
      partitionsResponseFields,
    );
  });

  test('Get Cluster Topology - Unauthorized', async ({request}) => {
    const res = await request.get(buildUrl('/topology'));
    await assertUnauthorizedRequest(res);
  });

  test('Get Cluster Status', async ({request}) => {
    const res = await request.get(buildUrl('/status'));
    expect(res.status()).toBe(204);
    const result = await res.body();
    expect(result.length).toBe(0);
  });
});
