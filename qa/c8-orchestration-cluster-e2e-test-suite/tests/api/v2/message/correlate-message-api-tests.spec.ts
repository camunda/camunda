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
  paginatedResponseFields,
} from '../../../../utils/http';
import {
  CORRELATE_MESSAGE,
  correlateMessageRequiredFields,
} from '../../../../utils/beans/request-beans';
import {createInstances, deploy} from '../../../../utils/zeebeClient';

test.describe('Correlate Message API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await deploy(['./resources/messageCatchEvent3.bpmn']);
    await createInstances('messageCatchEvent3', 1, 1);
    await expect(async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThan(1);
      const matchingItem = json.items.find(
        (it: {processDefinitionId: string}) =>
          it.processDefinitionId === 'messageCatchEvent3',
      );
      expect(matchingItem).toBeDefined();
      state['processInstanceKey'] = matchingItem['processInstanceKey'];
    }).toPass({
      intervals: [5_000, 10_000, 15_000],
      timeout: 30_000,
    });
  });

  test('Correlate Message Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/messages/correlation'), {
      headers: {},
      data: CORRELATE_MESSAGE,
    });
    expect(res.status()).toBe(401);
  });

  test('Correlate Message Bad Request', async ({request}) => {
    const res = await request.post(buildUrl('/messages/correlation'), {
      headers: jsonHeaders(),
      data: {correlationKey: 'correlationKey'},
    });
    expect(res.status()).toBe(400);
    const json = await res.json();
    assertRequiredFields(json, ['detail', 'title']);
    expect(json.title).toBe('INVALID_ARGUMENT');
    expect(json.detail).toBe('No messageName provided.');
  });

  test('Correlate Message Not found', async ({request}) => {
    const res = await request.post(buildUrl('/messages/correlation'), {
      headers: jsonHeaders(),
      data: {
        name: 'invalidMessageName',
        correlationKey: 'invalidKey',
        variables: {foo: 'bar'},
      },
    });
    expect(res.status()).toBe(404);
    const json = await res.json();
    assertRequiredFields(json, ['detail', 'title']);
    expect(json.title).toBe('NOT_FOUND');
  });

  test('Correlate Message Invalid Tenant', async ({request}) => {
    const updatedBody = {
      ...CORRELATE_MESSAGE,
      tenantId: 'invaliTenant',
    };
    const res = await request.post(buildUrl('/messages/correlation'), {
      headers: jsonHeaders(),
      data: updatedBody,
    });
    expect(res.status()).toBe(400);
    const json = await res.json();
    assertRequiredFields(json, ['detail', 'title']);
    expect(json.title).toBe('INVALID_ARGUMENT');
    expect(json.detail).toContain(
      'Expected to handle request Correlate Message with tenant identifier',
    );
  });

  test('Correlate Message Flow', async ({request}) => {
    await test.step('Correlate Message', async () => {
      const res = await request.post(buildUrl('/messages/correlation'), {
        headers: jsonHeaders(),
        data: CORRELATE_MESSAGE,
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, correlateMessageRequiredFields);
      assertEqualsForKeys(
        json,
        {
          tenantId: '<default>',
          processInstanceKey: state.processInstanceKey,
        },
        ['tenantId', 'processInstanceKey'],
      );
    });

    await test.step('Search Message Subscriptions After Correlating Message', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/message-subscriptions/search'),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: state.processInstanceKey,
              },
            },
          },
        );
        expect(res.status()).toBe(200);
        const json = await res.json();
        assertRequiredFields(json, paginatedResponseFields);
        expect(json.page.totalItems).toBe(0);
      }).toPass({
        intervals: [5_000, 10_000, 15_000],
        timeout: 30_000,
      });
    });
  });
});
