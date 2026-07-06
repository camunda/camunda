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
        expect(verifyJson.items).toHaveLength(1);
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
    // Valid partition-1 key (routable) with a counter no real task will reach,
    // so the command reaches the engine and is rejected with NOT_FOUND (404).
    const unknownUserTaskKey = '4503599627370495';
    const res = await completeUserTask(request, unknownUserTaskKey, {});
    await assertNotFoundRequest(
      res,
      `Command 'COMPLETE' rejected with code 'NOT_FOUND': Expected to complete user task with key '${unknownUserTaskKey}', but no such user task was found`,
    );
  });

  // Skipped due to bug #56635: https://github.com/camunda/camunda/issues/56635
  test.skip('Complete user task - out-of-range partition key', async ({
    request,
  }) => {
    // Key decodes to a partition that does not exist in the cluster, so the
    // command cannot be routed and currently returns a retryable 503 instead
    // of a permanent 404.
    const outOfRangeUserTaskKey = '9999999999999999';
    const res = await completeUserTask(request, outOfRangeUserTaskKey, {});
    await assertStatusCode(res, 404);
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
