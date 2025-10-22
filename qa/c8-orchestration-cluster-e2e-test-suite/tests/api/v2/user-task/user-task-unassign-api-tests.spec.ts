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

  // Skipped due to bug #38880: https://github.com/camunda/camunda/issues/38880
  test.skip('Unassign user task - not found', async ({request}) => {
    const unknownUserTaskKey = '2251799813694876';
    const res = await request.delete(
      buildUrl(`/user-tasks/${unknownUserTaskKey}/assignee`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'UNASSIGN' rejected with code 'NOT_FOUND': Expected to unassign user task with key '${unknownUserTaskKey}', but no such user task was found`,
    );
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
