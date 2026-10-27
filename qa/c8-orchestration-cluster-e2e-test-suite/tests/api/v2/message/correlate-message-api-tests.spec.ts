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
  assertStatusCode,
  assertUnauthorizedRequest,
  assertInvalidArgument,
  assertNotFoundRequest,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {
  CORRELATE_MESSAGE4,
  correlateMessageRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {
  cancelProcessInstance,
  createInstances,
  deploy,
} from '../../../../utils/zeebeClient';
import {defaultAssertionOptions} from '../../../../utils/constants';

test.describe('Correlate Message API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async () => {
    await deploy(['./resources/messageCatchEvent4.bpmn']);
    const processes = await createInstances('messageCatchEvent4', 1, 1);
    expect(processes).toHaveLength(1);
    state['processInstanceKey'] = processes[0].processInstanceKey;
  });

  test.afterAll(async () => {
    await cancelProcessInstance(state['processInstanceKey'] as string);
  });

  test('Correlate Message Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/messages/correlation'), {
      headers: {},
      data: CORRELATE_MESSAGE4,
    });
    await assertUnauthorizedRequest(res);
  });

  test('Correlate Message Bad Request', async ({request}) => {
    const res = await request.post(buildUrl('/messages/correlation'), {
      headers: jsonHeaders(),
      data: {correlationKey: 'correlationKey'},
    });
    await assertInvalidArgument(res, 400, 'No messageName provided.');
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
    await assertNotFoundRequest(
      res,
      "Command 'CORRELATE' rejected with code 'NOT_FOUND': Expected to find subscription for message with name 'invalidMessageName' and correlation key 'invalidKey', but none was found.",
    );
  });

  test('Correlate Message Invalid Tenant', async ({request}) => {
    const updatedBody = {
      ...CORRELATE_MESSAGE4,
      tenantId: 'invalidTenant',
    };
    const res = await request.post(buildUrl('/messages/correlation'), {
      headers: jsonHeaders(),
      data: updatedBody,
    });
    await assertInvalidArgument(
      res,
      400,
      'Expected to handle request Correlate Message with tenant identifier',
    );
  });

  test('Correlate Message Flow', async ({request}) => {
    await test.step('Correlate Message', async () => {
      const res = await request.post(buildUrl('/messages/correlation'), {
        headers: jsonHeaders(),
        data: CORRELATE_MESSAGE4,
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/messages/correlation',
          method: 'POST',
          status: '200',
        },
        res,
      );
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
        assertRequiredFields(json, paginatedResponseFields);
        expect(json.page.totalItems).toBe(1);
        expect(json.items[0].messageSubscriptionState).toBe('CORRELATED');
      }).toPass(defaultAssertionOptions);
    });
  });
});
