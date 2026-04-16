/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {deploy, createInstances} from '../../../../utils/zeebeClient';
import {
  buildUrl,
  jsonHeaders,
  assertStatusCode,
  assertUnauthorizedRequest,
  assertBadRequest,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';

/* eslint-disable playwright/expect-expect */

const state: Record<string, unknown> = {};

test.describe.parallel('Signal Broadcast API', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/signal_broadcast_test_process.bpmn']);

    const instances = await createInstances(
      'signal_broadcast_test_process',
      1,
      1,
      {
        orderId: '123',
      },
    );

    state.processInstanceKey = instances[0].processInstanceKey;
  });

  test.afterAll(async () => {
    // Currently no dedicated cleanup is required. Deployments
    // are typically reused across tests in this suite.
  });

  test('Broadcast Signal Success WithRequiredFieldsOnly', async ({request}) => {
    const body = {
      signalName: 'Signal_220k2ur',
    };

    const res = await request.post(buildUrl('/signals/broadcast'), {
      headers: jsonHeaders(),
      data: body,
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/signals/broadcast',
        method: 'POST',
        status: '200',
      },
      res,
    );

    const json = await res.json();
    expect(json).toBeDefined();
    expect(json.tenantId).toBeDefined();
    expect(json.signalKey).toBeDefined();
  });

  test('Broadcast Signal Success WithAllFields', async ({request}) => {
    const body = {
      signalName: 'Signal_220k2ur',
      variables: {
        orderId: '123',
      },
    };

    const res = await request.post(buildUrl('/signals/broadcast'), {
      headers: jsonHeaders(),
      data: body,
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/signals/broadcast',
        method: 'POST',
        status: '200',
      },
      res,
    );

    const json = await res.json();
    expect(json).toBeDefined();
    expect(json.tenantId).toBeDefined();
    expect(json.signalKey).toBeDefined();
  });

  test('Broadcast Signal Unauthorized', async ({request}) => {
    const body = {
      signalName: 'Signal_220k2ur',
      variables: {
        orderId: '123',
      },
    };

    const res = await request.post(buildUrl('/signals/broadcast'), {
      headers: {},
      data: body,
    });

    await assertUnauthorizedRequest(res);
  });

  test('Broadcast Signal BadRequest ForMissingName', async ({request}) => {
    const invalidBody = {
      variables: {
        orderId: '123',
      },
    };

    const res = await request.post(buildUrl('/signals/broadcast'), {
      headers: jsonHeaders(),
      data: invalidBody,
    });

    await assertBadRequest(res, /signalName/i, 'INVALID_ARGUMENT');
  });

  test('Broadcast Signal BadRequest ForInvalidNameType', async ({request}) => {
    const invalidBody = {
      signalName: 42,
      variables: {
        orderId: '123',
      },
    };

    const res = await request.post(buildUrl('/signals/broadcast'), {
      headers: jsonHeaders(),
      data: invalidBody,
    });

    await assertBadRequest(res, /signalName/i);
  });
});
