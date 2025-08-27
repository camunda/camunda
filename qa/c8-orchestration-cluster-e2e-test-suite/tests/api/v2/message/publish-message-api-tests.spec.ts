/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  buildUrl,
  jsonHeaders,
  assertRequiredFields,
  assertEqualsForKeys,
} from '../../../../utils/http';
import {PUBLISH_NEW_MESSAGE} from '../../../../utils/beans/request-beans';

test.describe('Publish Message API Tests', () => {
  test('Publish Message', async ({request}) => {
    const requestBody = PUBLISH_NEW_MESSAGE();
    const res = await request.post(buildUrl('/messages/publication'), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    expect(res.status()).toBe(200);
    const json = await res.json();
    assertRequiredFields(json, ['tenantId', 'messageKey']);
    assertEqualsForKeys(json, {tenantId: '<default>'}, ['tenantId']);
  });

  test('Publish Message Unauthorized', async ({request}) => {
    const requestBody = PUBLISH_NEW_MESSAGE();

    const res = await request.post(buildUrl('/messages/publication'), {
      headers: {},
      data: requestBody,
    });
    expect(res.status()).toBe(401);
  });

  test('Publish Message Bad Request', async ({request}) => {
    const res = await request.post(buildUrl('/messages/publication'), {
      headers: jsonHeaders(),
      data: {correlationKey: 'correlationKey'},
    });
    expect(res.status()).toBe(400);
    const json = await res.json();
    assertRequiredFields(json, ['detail', 'title']);
    expect(json.title).toBe('INVALID_ARGUMENT');
    expect(json.detail).toBe('No name provided.');
  });

  test('Publish Message Invalid Tenant', async ({request}) => {
    const updatedBody = {
      ...PUBLISH_NEW_MESSAGE(),
      tenantId: 'invaliTenant',
    };
    const res = await request.post(buildUrl('/messages/publication'), {
      headers: jsonHeaders(),
      data: updatedBody,
    });
    expect(res.status()).toBe(400);
    const json = await res.json();
    assertRequiredFields(json, ['detail', 'title']);
    expect(json.title).toBe('INVALID_ARGUMENT');
    expect(json.detail).toContain(
      'Expected to handle request Publish Message with tenant identifier',
    );
  });
});
