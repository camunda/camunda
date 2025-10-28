/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';
import {setupProcessInstanceForTests} from '@requestHelpers';
import {
  assertNotFoundRequest,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {validateResponseShape} from '../../../../json-body-assertions';
import {findUserTask} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Get User Task Tests', () => {
  const {state, beforeAll, beforeEach, afterEach} =
    setupProcessInstanceForTests('user_task_api_test_process');
  test.beforeAll(beforeAll);
  test.beforeEach(beforeEach);
  test.afterEach(afterEach);

  test('Get user task - success', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.get(buildUrl(`/user-tasks/${userTaskKey}`), {
      headers: jsonHeaders(),
    });
    const responseBody = await res.json();
    validateResponseShape(
      {
        path: '/user-tasks/{userTaskKey}',
        method: 'GET',
        status: '200',
      },
      responseBody,
    );
  });

  test('Get user task - not found', async ({request}) => {
    const unknownUserTaskKey = '2251799813694876';
    const res = await request.get(
      buildUrl(`/user-tasks/${unknownUserTaskKey}`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `User Task with key '${unknownUserTaskKey}' not found`,
    );
  });

  test('Get user task - unauthorized', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.get(buildUrl(`/user-tasks/${userTaskKey}`), {
      // No auth headers
      headers: {
        'Content-Type': 'application/json',
      },
    });
    await assertUnauthorizedRequest(res);
  });
});
