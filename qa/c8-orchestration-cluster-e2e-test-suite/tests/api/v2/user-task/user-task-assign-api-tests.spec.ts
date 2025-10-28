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
    const unknownUserTaskKey = '2251799813694876';
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
