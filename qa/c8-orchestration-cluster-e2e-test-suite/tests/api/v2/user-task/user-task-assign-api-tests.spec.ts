/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setupProcessInstanceForTests} from '@requestHelpers';
import {test} from '@playwright/test';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {findUserTask} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Assign User Task Tests', () => {
  const {state, beforeAll, beforeEach, afterEach} =
    setupProcessInstanceForTests('user_task_api_test_process');

  test.beforeAll(beforeAll);

  test.beforeEach(beforeEach);

  test.afterEach(afterEach);

  test('Assign user task - success', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const assignee = 'demo';
    const res = await request.post(
      buildUrl(`/user-tasks/${userTaskKey}/assignment`),
      {
        headers: jsonHeaders(),
        data: {
          assignee,
        },
      },
    );
    await assertStatusCode(res, 204);
  });

  test('Assign user task - bad request - invalid payload', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.post(
      buildUrl(`/user-tasks/${userTaskKey}/assignment`),
      {
        headers: jsonHeaders(),
        data: {}, // Missing assignee
      },
    );
    await assertBadRequest(res, 'No assignee provided', 'INVALID_ARGUMENT');
  });

  test('Assign user task - unauthorized', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.post(
      buildUrl(`/user-tasks/${userTaskKey}/assignment`),
      {
        // No auth headers
        headers: {
          'Content-Type': 'application/json',
        },
        data: {
          assignee: 'demo',
        },
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Assign user task - not found', async ({request}) => {
    // Valid partition-1 key (routable) with a counter no real task will reach,
    // so the command reaches the engine and is rejected with NOT_FOUND (404).
    const unknownUserTaskKey = '4503599627370495';
    const res = await request.post(
      buildUrl(`/user-tasks/${unknownUserTaskKey}/assignment`),
      {
        headers: jsonHeaders(),
        data: {
          assignee: 'demo',
        },
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'ASSIGN' rejected with code 'NOT_FOUND': Expected to assign user task with key '${unknownUserTaskKey}', but no such user task was found`,
    );
  });

  // Skipped due to bug #56635: https://github.com/camunda/camunda/issues/56635
  test.skip('Assign user task - out-of-range partition key', async ({
    request,
  }) => {
    // Key decodes to a partition that does not exist in the cluster, so the
    // command cannot be routed and currently returns a retryable 503 instead
    // of a permanent 404.
    const outOfRangeUserTaskKey = '9999999999999999';
    const res = await request.post(
      buildUrl(`/user-tasks/${outOfRangeUserTaskKey}/assignment`),
      {
        headers: jsonHeaders(),
        data: {
          assignee: 'demo',
        },
      },
    );
    await assertStatusCode(res, 404);
  });

  test('Double Assign user task - success', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const assignee = 'demo';

    await test.step('First assignment', async () => {
      const res1 = await request.post(
        buildUrl(`/user-tasks/${userTaskKey}/assignment`),
        {
          headers: jsonHeaders(),
          data: {
            assignee,
          },
        },
      );
      await assertStatusCode(res1, 204);
    });

    await test.step('Second assignment with the same assignee', async () => {
      const res2 = await request.post(
        buildUrl(`/user-tasks/${userTaskKey}/assignment`),
        {
          headers: jsonHeaders(),
          data: {
            assignee,
          },
        },
      );
      await assertStatusCode(res2, 204);
    });
  });
});
