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
  assertEqualsForKeys,
  assertStatusCode,
  assertUnauthorizedRequest,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {
  expectedMessageSubscription1,
  expectedMessageSubscription2,
} from '../../../../utils/beans/requestBeans';
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
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/message-subscriptions/search',
        method: 'POST',
        status: '200',
      },
      res,
    );
    const json = await res.json();
    expect(json.page.totalItems).toBe(0);
    expect(json.items).toHaveLength(0);
  });

  test('Search Subscriptions Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl(`/message-subscriptions/search`), {
      headers: {},
      data: {filter: {name: state.name}},
    });
    await assertUnauthorizedRequest(res);
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

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/message-subscriptions/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
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

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
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
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
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
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      const matchingItem1 = json.items.find(
        (it: {processDefinitionId: string}) =>
          it.processDefinitionId === 'messageCatchEvent1',
      );
      const matchingItem2 = json.items.find(
        (it: {processDefinitionId: string}) =>
          it.processDefinitionId === 'messageCatchEvent2',
      );
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

    await test.step('Search Message Subscriptions By Message Subscription State', async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              messageSubscriptionState: 'CREATED',
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
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
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
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
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
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
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
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
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
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
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
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
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/message-subscriptions/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      expect(json.page.totalItems).toBeGreaterThanOrEqual(1);
      json.items.forEach((it: object) => {
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
