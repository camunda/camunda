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
  buildUrl,
  defaultHeaders,
} from '../../../utils/http';
import {
  brokerResponseFields,
  clusterTopologyResponseFields,
  partionsResponseFields,
} from '../../../utils/beans/requestBeans';

test.describe('Cluster API Tests', () => {
  test('Get Cluster Topology', async ({request}) => {
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
      partionsResponseFields,
    );
  });

  test('Get Cluster Topology - Unauthorized', async ({request}) => {
    const res = await request.get(buildUrl('/topology'));
    expect(res.status()).toBe(401);
    const result = await res.json();
    expect(result.title).toBe('Unauthorized');
    expect(result.detail).toBe(
      'An Authentication object was not found in the SecurityContext',
    );
    expect(result.instance).toBe('/v2/topology');
  });

  test('Get Cluster Status', async ({request}) => {
    const res = await request.get(buildUrl('/status'));
    expect(res.status()).toBe(204);
    const result = await res.body();
    expect(result.length).toBe(0);
  });
});
