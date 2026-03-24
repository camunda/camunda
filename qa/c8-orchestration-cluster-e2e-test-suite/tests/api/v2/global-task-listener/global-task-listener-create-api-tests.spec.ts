/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  jsonHeaders,
  buildUrl,
  assertRequiredFields,
  assertUnauthorizedRequest,
  assertBadRequest,
  assertConflictRequest,
  assertStatusCode,
} from '../../../../utils/http';
import {
  generateUniqueId,
  defaultAssertionOptions,
} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';

const GLOBAL_TASK_LISTENER_REQUIRED_FIELDS = [
  'id',
  'type',
  'eventTypes',
  'afterNonGlobal',
  'priority',
  'retries',
  'source',
];

function createUniqueGlobalTaskListenerBody(customId?: string) {
  const id = customId ?? `test-gl-${generateUniqueId()}`;
  return {
    id,
    type: `io.camunda.test.listener.${id}`,
    eventTypes: ['creating', 'completing'],
  };
}

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Global Task Listener API Tests - Create', () => {
  const createdListenerIds: string[] = [];

  test.afterAll(async ({request}) => {
    for (const id of createdListenerIds) {
      try {
        await request.delete(buildUrl('/global-task-listeners/{id}', {id}), {
          headers: jsonHeaders(),
        });
      } catch {
        // Ignore cleanup errors
      }
    }
  });

  test('Create Global Task Listener - success with required fields', async ({
    request,
  }) => {
    await expect(async () => {
      const body = createUniqueGlobalTaskListenerBody();

      const res = await request.post(buildUrl('/global-task-listeners'), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertStatusCode(res, 201);
      await validateResponse(
        {path: '/global-task-listeners', method: 'POST', status: '201'},
        res,
      );
      const json = await res.json();
      assertRequiredFields(json, GLOBAL_TASK_LISTENER_REQUIRED_FIELDS);
      expect(json.id).toBe(body.id);
      expect(json.type).toBe(body.type);
      expect(json.eventTypes).toEqual(expect.arrayContaining(body.eventTypes));
      expect(json.source).toBe('API');

      createdListenerIds.push(json.id);
    }).toPass(defaultAssertionOptions);
  });

  test('Create Global Task Listener - success with all optional fields', async ({
    request,
  }) => {
    await expect(async () => {
      const body = {
        ...createUniqueGlobalTaskListenerBody(),
        eventTypes: ['all'],
        retries: 3,
        afterNonGlobal: true,
        priority: 50,
      };

      const res = await request.post(buildUrl('/global-task-listeners'), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertStatusCode(res, 201);
      await validateResponse(
        {path: '/global-task-listeners', method: 'POST', status: '201'},
        res,
      );
      const json = await res.json();
      assertRequiredFields(json, GLOBAL_TASK_LISTENER_REQUIRED_FIELDS);
      expect(json.id).toBe(body.id);
      expect(json.type).toBe(body.type);
      expect(json.retries).toBe(3);
      expect(json.afterNonGlobal).toBe(true);
      expect(json.priority).toBe(50);
      expect(json.source).toBe('API');

      createdListenerIds.push(json.id);
    }).toPass(defaultAssertionOptions);
  });

  test('Create Global Task Listener - unauthorized', async ({request}) => {
    const body = createUniqueGlobalTaskListenerBody();

    const res = await request.post(buildUrl('/global-task-listeners'), {
      headers: {},
      data: body,
    });

    await assertUnauthorizedRequest(res);
  });

  test('Create Global Task Listener - missing required id field', async ({
    request,
  }) => {
    const unique = generateUniqueId();
    const bodyWithoutId = {
      type: `io.camunda.test.listener.${unique}`,
      eventTypes: ['creating', 'completing'],
    };

    const res = await request.post(buildUrl('/global-task-listeners'), {
      headers: jsonHeaders(),
      data: bodyWithoutId,
    });

    await assertBadRequest(res, /id/i, 'INVALID_ARGUMENT');
  });

  test('Create Global Task Listener - missing required type field', async ({
    request,
  }) => {
    const unique = generateUniqueId();
    const bodyWithoutType = {
      id: `test-gl-${unique}`,
      eventTypes: ['creating', 'completing'],
    };

    const res = await request.post(buildUrl('/global-task-listeners'), {
      headers: jsonHeaders(),
      data: bodyWithoutType,
    });

    await assertBadRequest(res, /type/i, 'INVALID_ARGUMENT');
  });

  test('Create Global Task Listener - missing required eventTypes field', async ({
    request,
  }) => {
    const unique = generateUniqueId();
    const bodyWithoutEventTypes = {
      id: `test-gl-${unique}`,
      type: `io.camunda.test.listener.${unique}`,
    };

    const res = await request.post(buildUrl('/global-task-listeners'), {
      headers: jsonHeaders(),
      data: bodyWithoutEventTypes,
    });

    await assertBadRequest(res, /eventTypes/i, 'INVALID_ARGUMENT');
  });

  test('Create Global Task Listener - invalid eventType value', async ({
    request,
  }) => {
    const body = {
      ...createUniqueGlobalTaskListenerBody(),
      eventTypes: ['INVALID_EVENT_TYPE'],
    };

    const res = await request.post(buildUrl('/global-task-listeners'), {
      headers: jsonHeaders(),
      data: body,
    });

    await assertBadRequest(
      res,
      /eventTypes|INVALID_EVENT_TYPE/i,
      'INVALID_ARGUMENT',
    );
  });

  test('Create Global Task Listener - duplicate id conflict', async ({
    request,
  }) => {
    const body = createUniqueGlobalTaskListenerBody();

    // First creation should succeed
    await expect(async () => {
      const firstRes = await request.post(buildUrl('/global-task-listeners'), {
        headers: jsonHeaders(),
        data: body,
      });
      await assertStatusCode(firstRes, 201);

      const json = await firstRes.json();
      if (!createdListenerIds.includes(json.id)) {
        createdListenerIds.push(json.id);
      }
    }).toPass(defaultAssertionOptions);

    // Second creation with the same id should return 409
    const secondRes = await request.post(buildUrl('/global-task-listeners'), {
      headers: jsonHeaders(),
      data: body,
    });

    await assertConflictRequest(secondRes);
  });
});
