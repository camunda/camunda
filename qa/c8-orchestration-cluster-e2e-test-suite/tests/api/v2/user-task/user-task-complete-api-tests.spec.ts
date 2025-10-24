/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setupProcessInstanceForTests} from '@requestHelpers';
import {expect, test} from '@playwright/test';
import {
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponseShape} from '../../../../json-body-assertions';
import {completeUserTask, findUserTask} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Complete User Task Tests', () => {
  const {state, beforeAll, beforeEach, afterEach} =
    setupProcessInstanceForTests('user_task_api_test_process');

  test.beforeAll(beforeAll);
  test.beforeEach(beforeEach);
  test.afterEach(afterEach);

  test('Search, complete and verify completion', async ({request}) => {
    const localState: Record<string, unknown> = {};

    await test.step('Find the user task in CREATED state', async () => {
      localState['userTaskKey'] = await findUserTask(
        request,
        state['processInstanceKey'] as string,
        'CREATED',
      );
    });

    await test.step('Complete the user task', async () => {
      const completeRes = await completeUserTask(
        request,
        localState['userTaskKey'] as string,
      );
      await assertStatusCode(completeRes, 204);
    });

    await test.step('Verify the user task is in COMPLETED state', async () => {
      await expect(async () => {
        const verifyRes = await request.post(buildUrl('/user-tasks/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: state['processInstanceKey'] as string,
            },
          },
        });
        await assertStatusCode(verifyRes, 200);
        const verifyJson = await verifyRes.json();

        validateResponseShape(
          {
            path: '/user-tasks/search',
            method: 'POST',
            status: '200',
          },
          verifyJson,
        );
        expect(verifyJson.page.totalItems).toBe(1);
        expect(verifyJson.items.length).toBe(1);
        expect(verifyJson.items[0].state).toBe('COMPLETED');
      }).toPass(defaultAssertionOptions);
    });

    state['processCompleted'] = true;
  });

  test('Complete user task - bad request - invalid payload', async ({
    request,
  }) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await completeUserTask(request, userTaskKey, {
      invalid: 'payload',
    });
    await assertStatusCode(res, 400);
  });

  test('Complete user task - not found', async ({request}) => {
    const unknownUserTaskKey = '2251799813694876';
    const res = await completeUserTask(request, unknownUserTaskKey, {});
    await assertNotFoundRequest(
      res,
      `Command 'COMPLETE' rejected with code 'NOT_FOUND': Expected to complete user task with key '${unknownUserTaskKey}', but no such user task was found`,
    );
  });

  test('Complete user task - unauthorized', async ({request}) => {
    const userTaskKey = await findUserTask(
      request,
      state['processInstanceKey'] as string,
      'CREATED',
    );
    const res = await request.post(
      buildUrl(`/user-tasks/${userTaskKey}/completion`),
      {
        // No auth headers
        headers: {
          'Content-Type': 'application/json',
        },
        data: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });
});
