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
  assertConflictRequest,
  assertStatusCode,
  assertForbiddenRequest,
  encode,
} from '../../../../utils/http';
import {
  generateUniqueId,
  defaultAssertionOptions,
} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {cleanupGlobalTaskListeners} from '../../../../utils/globalTaskListenerCleanup';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {
  createUser,
  createComponentAuthorization,
  cleanupAuthorizations,
} from '@requestHelpers';

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
    await cleanupGlobalTaskListeners(request, createdListenerIds);
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

    await assertBadRequest(res, /id/i, 'Bad Request');
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

    await assertBadRequest(res, /type/i, 'Bad Request');
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

    await assertBadRequest(res, /eventTypes/i, 'Bad Request');
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

    await assertBadRequest(res, /eventTypes|INVALID_EVENT_TYPE/i);
  });

  test('Create Global Task Listener - duplicate id conflict', async ({
    request,
  }) => {
    const body = createUniqueGlobalTaskListenerBody();

    await test.step('First creation should succeed', async () => {
      await expect(async () => {
        const firstRes = await request.post(
          buildUrl('/global-task-listeners'),
          {
            headers: jsonHeaders(),
            data: body,
          },
        );
        await assertStatusCode(firstRes, 201);

        const json = await firstRes.json();
        if (!createdListenerIds.includes(json.id)) {
          createdListenerIds.push(json.id);
        }
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Second creation with the same id should return 409', async () => {
      const secondRes = await request.post(buildUrl('/global-task-listeners'), {
        headers: jsonHeaders(),
        data: body,
      });
      await assertConflictRequest(secondRes);
    });
  });
});

test.describe('Global Task Listener API - Create Permission Tests', () => {
  let userWithoutPermission: {
    username: string;
    name: string;
    email: string;
    password: string;
  };
  let userWithReadOnlyPermission: {
    username: string;
    name: string;
    email: string;
    password: string;
  };

  const createdListenerIds: string[] = [];
  const authorizationKeys: string[] = [];

  test.beforeAll(async ({request}) => {
    userWithoutPermission = await createUser(request);

    userWithReadOnlyPermission = await createUser(request);

    const readOnlyAuthKey = await createComponentAuthorization(request, {
      ownerId: userWithReadOnlyPermission.username,
      ownerType: 'USER',
      resourceType: 'GLOBAL_LISTENER',
      resourceId: '*',
      permissionTypes: ['READ_TASK_LISTENER'],
    });
    authorizationKeys.push(readOnlyAuthKey);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, [
      userWithoutPermission.username,
      userWithReadOnlyPermission.username,
    ]);
    await cleanupGlobalTaskListeners(request, createdListenerIds);
  });

  test('Create Global Task Listener - 403 Forbidden - user with no GLOBAL_LISTENER permissions', async ({
    request,
  }) => {
    const token = encode(
      `${userWithoutPermission.username}:${userWithoutPermission.password}`,
    );

    await expect(async () => {
      const res = await request.post(buildUrl('/global-task-listeners'), {
        headers: jsonHeaders(token),
        data: createUniqueGlobalTaskListenerBody(),
      });

      await assertForbiddenRequest(
        res,
        "Command 'CREATE' rejected with code 'FORBIDDEN': Insufficient permissions to perform operation 'CREATE_TASK_LISTENER' on resource 'GLOBAL_LISTENER'",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Create Global Task Listener - 403 Forbidden - user with READ-only GLOBAL_LISTENER permission', async ({
    request,
  }) => {
    const token = encode(
      `${userWithReadOnlyPermission.username}:${userWithReadOnlyPermission.password}`,
    );

    await expect(async () => {
      const res = await request.post(buildUrl('/global-task-listeners'), {
        headers: jsonHeaders(token),
        data: createUniqueGlobalTaskListenerBody(),
      });

      await assertForbiddenRequest(
        res,
        "Command 'CREATE' rejected with code 'FORBIDDEN': Insufficient permissions to perform operation 'CREATE_TASK_LISTENER' on resource 'GLOBAL_LISTENER'",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Create Global Task Listener - 201 Success - admin user with full permissions', async ({
    request,
  }) => {
    await expect(async () => {
      const body = createUniqueGlobalTaskListenerBody();
      const res = await request.post(buildUrl('/global-task-listeners'), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertStatusCode(res, 201);
      const json = await res.json();
      createdListenerIds.push(json.id);
    }).toPass(defaultAssertionOptions);
  });
});
