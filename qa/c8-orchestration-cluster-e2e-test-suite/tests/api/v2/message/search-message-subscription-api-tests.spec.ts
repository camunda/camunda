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
  expectedMessageSubscription1,
  expectedMessageSubscription2,
  messageSubscriptionRequiredFields,
} from '../../../../utils/beans/request-beans';
import {createInstances, deploy} from '../../../../utils/zeebeClient';

test.beforeAll(async () => {
  await deploy(['./resources/messageCatchEvent1.bpmn']);
  await deploy(['./resources/messageCatchEvent2.bpmn']);
  await createInstances('messageCatchEvent1', 1, 1);
  await createInstances('messageCatchEvent2', 1, 1);
});

test.describe('Search Message Subscription API Tests', () => {
  const state: Record<string, unknown> = {};

  test('Search Message Subscriptions By Invalid Name', async ({request}) => {
    const res = await request.post(buildUrl('/message-subscriptions/search'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          messageName: 'invalid-message-name',
        },
      },
    });
    expect(res.status()).toBe(200);
    const json = await res.json();
    assertRequiredFields(json, paginatedResponseFields);
    expect(json.page.totalItems).toBe(0);
    expect(json.items.length).toBe(0);
  });

  test('Search Subscriptions Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl(`/message-subscriptions/search`), {
      headers: {},
      data: {filter: {name: state.name}},
    });
    expect(res.status()).toBe(401);
  });

  test('Search Message Flow', async ({request}) => {
    await test.step('Search Message Subscriptions', async () => {
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
        assertRequiredFields(json, paginatedResponseFields);
        expect(json.page.totalItems).toBeGreaterThan(1);
        const matchingItem1 = json.items.find(
          (it: {processDefinitionId: string}) =>
            it.processDefinitionId === 'messageCatchEvent1',
        );
        const matchingItem2 = json.items.find(
          (it: {processDefinitionId: string}) =>
            it.processDefinitionId === 'messageCatchEvent2',
        );
        assertRequiredFields(matchingItem1, messageSubscriptionRequiredFields);
        assertRequiredFields(matchingItem2, messageSubscriptionRequiredFields);
        assertEqualsForKeys(matchingItem1, expectedMessageSubscription1, [
          'correlationKey',
          'tenantId',
          'elementId',
          'messageName',
        ]);
        assertEqualsForKeys(matchingItem2, expectedMessageSubscription2, [
          'correlationKey',
          'tenantId',
          'elementId',
          'messageName',
        ]);
        state['processDefinitionKey'] = matchingItem1['processDefinitionKey'];
        state['processInstanceKey'] = matchingItem1['processInstanceKey'];
        state['elementInstanceKey'] = matchingItem2['elementInstanceKey'];
      }).toPass({
        intervals: [5_000, 10_000, 15_000],
        timeout: 30_000,
      });
    });

    await test.step('Search Message Subscriptions By Process Definition Id', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processDefinitionId:
                expectedMessageSubscription1.processDefinitionId,
            },
          },
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
        assertRequiredFields(it, messageSubscriptionRequiredFields);
        assertEqualsForKeys(it, expectedMessageSubscription1, [
          'correlationKey',
          'tenantId',
          'elementId',
          'messageName',
        ]);
      });
    });

    await test.step('Search Message Subscriptions By Correlation Key', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              correlationKey: expectedMessageSubscription1.correlationKey,
            },
          },
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
        assertRequiredFields(it, messageSubscriptionRequiredFields);
        assertEqualsForKeys(it, expectedMessageSubscription1, [
          'correlationKey',
          'tenantId',
          'elementId',
          'messageName',
        ]);
      });
    });

    await test.step('Search Message Subscriptions By Tenant Id', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              tenantId: expectedMessageSubscription1.tenantId,
            },
          },
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      const matchingItem1 = json.items.find(
        (it: {processDefinitionId: string}) =>
          it.processDefinitionId === 'messageCatchEvent1',
      );
      const matchingItem2 = json.items.find(
        (it: {processDefinitionId: string}) =>
          it.processDefinitionId === 'messageCatchEvent2',
      );
      assertRequiredFields(matchingItem1, messageSubscriptionRequiredFields);
      assertRequiredFields(matchingItem2, messageSubscriptionRequiredFields);
      assertEqualsForKeys(matchingItem1, expectedMessageSubscription1, [
        'correlationKey',
        'tenantId',
        'elementId',
        'messageName',
      ]);
      assertEqualsForKeys(matchingItem2, expectedMessageSubscription2, [
        'correlationKey',
        'tenantId',
        'elementId',
        'messageName',
      ]);
    });

    await test.step('Search Message Subscriptions By Message Subscription Type', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              messageSubscriptionType: 'CREATED',
            },
          },
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThan(1);
      const matchingItem1 = json.items.find(
        (it: {processDefinitionId: string}) =>
          it.processDefinitionId === 'messageCatchEvent1',
      );
      const matchingItem2 = json.items.find(
        (it: {processDefinitionId: string}) =>
          it.processDefinitionId === 'messageCatchEvent2',
      );
      assertRequiredFields(matchingItem1, messageSubscriptionRequiredFields);
      assertRequiredFields(matchingItem2, messageSubscriptionRequiredFields);
      assertEqualsForKeys(matchingItem1, expectedMessageSubscription1, [
        'correlationKey',
        'tenantId',
        'elementId',
        'messageName',
      ]);
      assertEqualsForKeys(matchingItem2, expectedMessageSubscription2, [
        'correlationKey',
        'tenantId',
        'elementId',
        'messageName',
      ]);
    });

    await test.step('Search Message Subscriptions By Element Id', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              elementId: expectedMessageSubscription2.elementId,
            },
          },
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
        assertRequiredFields(it, messageSubscriptionRequiredFields);
        assertEqualsForKeys(it, expectedMessageSubscription2, [
          'correlationKey',
          'tenantId',
          'elementId',
          'messageName',
        ]);
      });
    });

    await test.step('Search Message Subscriptions By Message Name', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              messageName: expectedMessageSubscription2.messageName,
            },
          },
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
        assertRequiredFields(it, messageSubscriptionRequiredFields);
        assertEqualsForKeys(it, expectedMessageSubscription2, [
          'correlationKey',
          'tenantId',
          'elementId',
          'messageName',
        ]);
      });
    });

    await test.step('Search Message Subscriptions By Process Definition Key', async () => {
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
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
        assertRequiredFields(it, messageSubscriptionRequiredFields);
        assertEqualsForKeys(it, expectedMessageSubscription1, [
          'correlationKey',
          'tenantId',
          'elementId',
          'messageName',
        ]);
      });
    });

    await test.step('Search Message Subscriptions By Process Instance Key', async () => {
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
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
        assertRequiredFields(it, messageSubscriptionRequiredFields);
        assertEqualsForKeys(it, expectedMessageSubscription1, [
          'correlationKey',
          'tenantId',
          'elementId',
          'messageName',
        ]);
      });
    });

    await test.step('Search Message Subscriptions By Element Instance Key', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              elementInstanceKey: state.elementInstanceKey,
            },
          },
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
        assertRequiredFields(it, messageSubscriptionRequiredFields);
        assertEqualsForKeys(it, expectedMessageSubscription2, [
          'correlationKey',
          'tenantId',
          'elementId',
          'messageName',
        ]);
      });
    });

    await test.step('Search Message Subscriptions By Multiple Fields', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              elementInstanceKey: state.elementInstanceKey,
              tenantId: expectedMessageSubscription2.tenantId,
              messageName: expectedMessageSubscription2.messageName,
              elementId: expectedMessageSubscription2.elementId,
            },
          },
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
        assertRequiredFields(it, messageSubscriptionRequiredFields);
        assertEqualsForKeys(it, expectedMessageSubscription2, [
          'correlationKey',
          'tenantId',
          'elementId',
          'messageName',
        ]);
      });
    });
  });
});
