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
  assertUnauthorizedRequest,
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {cleanupGlobalTaskListeners} from '../../../../utils/globalTaskListenerCleanup';
import {createGlobalTaskListener} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Global Task Listener API Tests - Update', () => {
  const createdListenerIds: string[] = [];

  test.afterAll(async ({request}) => {
    await cleanupGlobalTaskListeners(request, createdListenerIds);
  });

  test('Update Global Task Listener - success updating all fields', async ({
    request,
  }) => {
    const created = await createGlobalTaskListener(request);
    createdListenerIds.push(created.id);

    const updateBody = {
      type: `io.camunda.test.listener.updated.${created.id}`,
      eventTypes: ['assigning', 'canceling'],
      retries: 5,
      afterNonGlobal: true,
      priority: 75,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/global-task-listeners/{id}', {id: created.id}),
        {
          headers: jsonHeaders(),
          data: updateBody,
        },
      );

      await assertStatusCode(res, 200);
      await validateResponse(
        {path: '/global-task-listeners/{id}', method: 'PUT', status: '200'},
        res,
      );
      const json = await res.json();
      expect(json.id).toBe(created.id);
      expect(json.type).toBe(updateBody.type);
      expect(json.eventTypes).toEqual(
        expect.arrayContaining(updateBody.eventTypes),
      );
      expect(json.retries).toBe(5);
      expect(json.afterNonGlobal).toBe(true);
      expect(json.priority).toBe(75);
      expect(json.source).toBe('API');
    }).toPass(defaultAssertionOptions);
  });

  test('Update Global Task Listener - success updating required fields only', async ({
    request,
  }) => {
    const created = await createGlobalTaskListener(request, {
      retries: 3,
      afterNonGlobal: true,
      priority: 50,
    });
    createdListenerIds.push(created.id);

    const updateBody = {
      type: `io.camunda.test.listener.updated.${created.id}`,
      eventTypes: ['all'],
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/global-task-listeners/{id}', {id: created.id}),
        {
          headers: jsonHeaders(),
          data: updateBody,
        },
      );

      await assertStatusCode(res, 200);
      await validateResponse(
        {path: '/global-task-listeners/{id}', method: 'PUT', status: '200'},
        res,
      );
      const json = await res.json();
      expect(json.id).toBe(created.id);
      expect(json.type).toBe(updateBody.type);
      expect(json.eventTypes).toEqual(
        expect.arrayContaining(updateBody.eventTypes),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Update Global Task Listener - unauthorized', async ({request}) => {
    const created = await createGlobalTaskListener(request);
    createdListenerIds.push(created.id);

    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: created.id}),
      {
        headers: {},
        data: {
          type: created.type,
          eventTypes: created.eventTypes,
        },
      },
    );

    await assertUnauthorizedRequest(res);
  });

  test('Update Global Task Listener - not found', async ({request}) => {
    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: 'non-existent-id'}),
      {
        headers: jsonHeaders(),
        data: {
          type: 'io.camunda.test.listener.dummy',
          eventTypes: ['creating'],
        },
      },
    );

    await assertNotFoundRequest(res, 'non-existent-id');
  });

  test('Update Global Task Listener - missing required type field', async ({
    request,
  }) => {
    const created = await createGlobalTaskListener(request);
    createdListenerIds.push(created.id);

    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: created.id}),
      {
        headers: jsonHeaders(),
        data: {eventTypes: ['creating']},
      },
    );

    await assertBadRequest(res, /type/i, 'INVALID_ARGUMENT');
  });

  test('Update Global Task Listener - missing required eventTypes field', async ({
    request,
  }) => {
    const created = await createGlobalTaskListener(request);
    createdListenerIds.push(created.id);

    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: created.id}),
      {
        headers: jsonHeaders(),
        data: {type: created.type},
      },
    );

    await assertBadRequest(res, /eventTypes/i, 'INVALID_ARGUMENT');
  });

  test('Update Global Task Listener - invalid eventType value', async ({
    request,
  }) => {
    const created = await createGlobalTaskListener(request);
    createdListenerIds.push(created.id);

    const res = await request.put(
      buildUrl('/global-task-listeners/{id}', {id: created.id}),
      {
        headers: jsonHeaders(),
        data: {
          type: created.type,
          eventTypes: ['INVALID_EVENT_TYPE'],
        },
      },
    );

    await assertBadRequest(res, /eventTypes|INVALID_EVENT_TYPE/i);
  });
});
