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
test.describe.parallel('Unassign User Task Tests', () => {
  const {state, beforeAll, beforeEach, afterEach} =
    setupProcessInstanceForTests('user_task_api_test_process');

  test.beforeAll(beforeAll);

  test.beforeEach(beforeEach);

  test.afterEach(afterEach);

  test('Unassign user task - success', async ({request}) => {
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

    await test.step('Then unassignment', async () => {
      const res2 = await request.delete(
        buildUrl(`/user-tasks/${userTaskKey}/assignee`),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res2, 204);
    });
  });

  test('Unassign user task - unauthorized', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.delete(
      buildUrl(`/user-tasks/${userTaskKey}/assignee`),
      {
        // No auth headers
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign user task - not found', async ({request}) => {
    // Valid partition-1 key (routable) with a counter no real task will reach,
    // so the command reaches the engine and is rejected with NOT_FOUND (404).
    const unknownUserTaskKey = '4503599627370495';
    const res = await request.delete(
      buildUrl(`/user-tasks/${unknownUserTaskKey}/assignee`),
      {
        headers: jsonHeaders(),
      },
    );
    // The rejection reads 'ASSIGN' (not 'UNASSIGN') by design: the engine has no
    // separate UNASSIGN intent — unassignment is an ASSIGN command with an empty
    // assignee. See issue #38880.
    await assertNotFoundRequest(
      res,
      `Command 'ASSIGN' rejected with code 'NOT_FOUND': Expected to assign user task with key '${unknownUserTaskKey}', but no such user task was found`,
    );
  });

  // Skipped due to bug #56635: https://github.com/camunda/camunda/issues/56635
  test.skip('Unassign user task - out-of-range partition key', async ({
    request,
  }) => {
    // Key decodes to a partition that does not exist in the cluster, so the
    // command cannot be routed and currently returns a retryable 503 instead
    // of a permanent 404.
    const outOfRangeUserTaskKey = '9999999999999999';
    const res = await request.delete(
      buildUrl(`/user-tasks/${outOfRangeUserTaskKey}/assignee`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(res, 404);
  });

  test('Unassign user task - bad request - invalid user task key', async ({
    request,
  }) => {
    const invalidUserTaskKey = 'invalidKey';
    const res = await request.delete(
      buildUrl(`/user-tasks/${invalidUserTaskKey}/assignee`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertBadRequest(
      res,
      `Failed to convert 'userTaskKey' with value: '${invalidUserTaskKey}'`,
    );
  });

  test('Double Unassign user task - success', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );

    await test.step('First unassignment', async () => {
      const res1 = await request.delete(
        buildUrl(`/user-tasks/${userTaskKey}/assignee`),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res1, 204);
    });

    await test.step('Second unassignment', async () => {
      const res2 = await request.delete(
        buildUrl(`/user-tasks/${userTaskKey}/assignee`),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res2, 204);
    });
  });
});
