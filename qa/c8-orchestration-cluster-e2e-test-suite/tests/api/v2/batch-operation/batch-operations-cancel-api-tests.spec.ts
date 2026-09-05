/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';
import {deploy} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {
  createCancellationBatch,
  cancelBatchOperation,
  createCompletedBatchOperation,
  expectBatchState,
} from '@requestHelpers';
/* eslint-disable playwright/expect-expect */

test.describe.parallel('Cancel Batch Operation Tests', () => {
  const state: {
    cancelableBatchOperationKey?: string;
    finishedBatchOperationKey?: string;
  } = {};

  test.beforeAll(async ({request}) => {
    await deploy(['./resources/batch_cancellation_process.bpmn']);

    state.finishedBatchOperationKey =
      await createCompletedBatchOperation(request);
  });

  test('Cancel active batch operation returns 204 and status becomes CANCELED', async ({
    request,
  }) => {
    // Use a large instance count so the batch stays ACTIVE long enough for the
    // cancel command to catch it in flight. Cancellation time scales with the
    // instance count: a 3-instance batch completes in ~2.5s, so a 30-instance
    // batch can reach a terminal state between the accepted create and the
    // first cancel retry, and cancelling a finished batch correctly returns a
    // permanent 404 that the 240s retry budget cannot recover from. Mirrors the
    // instance count the sibling suspend suite already relies on for the same
    // reason.
    const key =
      await test.step('Create cancelable batch operation', async () => {
        return createCancellationBatch(request, 500);
      });

    await test.step('Cancel batch operation', async () => {
      const res = await cancelBatchOperation(request, key);
      await assertStatusCode(res, 204);
    });

    await test.step('Poll batch status', async () => {
      await expectBatchState(request, key, 'CANCELED');
    });
  });

  test('Cancel batch operation twice fails on second request', async ({
    request,
  }) => {
    // Same reason as the test above: the first cancel has to land while the
    // batch is still ACTIVE, and 30 instances can finish before it does.
    const key =
      await test.step('Create cancelable batch operation', async () => {
        return createCancellationBatch(request, 500);
      });

    await test.step('Send first cancel request', async () => {
      const firstRes = await cancelBatchOperation(request, key);
      await assertStatusCode(firstRes, 204);
    });

    await test.step('Send second cancel request and assert failure', async () => {
      const secondRes = await cancelBatchOperation(request, key, 404);

      await assertNotFoundRequest(
        secondRes,
        `Command 'CANCEL' rejected with code 'NOT_FOUND': Expected to cancel a batch operation with key '${key}', but no such batch operation was found`,
      );
    });
  });

  test('Cancel finished batch operation returns 404', async ({request}) => {
    const key = state.finishedBatchOperationKey as string;

    await test.step('Cancel finished batch operation', async () => {
      const res = await cancelBatchOperation(request, key, 404);
      await assertNotFoundRequest(
        res,
        `Command 'CANCEL' rejected with code 'NOT_FOUND': Expected to cancel a batch operation with key '${key}', but no such batch operation was found`,
      );
    });
  });

  test('Cancel batch operation with unknown key returns 404', async ({
    request,
  }) => {
    const unknownKey = '2251799813999999';

    await test.step('Cancel unknown batch operation', async () => {
      const res = await cancelBatchOperation(request, unknownKey, 404);
      await assertNotFoundRequest(
        res,
        `Command 'CANCEL' rejected with code 'NOT_FOUND': Expected to cancel a batch operation with key '${unknownKey}', but no such batch operation was found`,
      );
    });
  });

  test('Cancel batch operation with invalid key format returns 400', async ({
    request,
  }) => {
    await test.step('Send cancel request with invalid key', async () => {
      const res = await request.post(
        buildUrl('/batch-operations/{batchOperationKey}/cancellation', {
          batchOperationKey: 'not-a-valid-key',
        }),
        {
          headers: jsonHeaders(),
        },
      );
      await assertBadRequest(
        res,
        "Batch operation id 'not-a-valid-key' is not a valid number. Legacy Batch Operation IDs are not supported!",
      );
    });
  });

  test('Cancel batch operation without auth returns 401', async ({request}) => {
    const key =
      await test.step('Create batch operation for auth test', async () => {
        return createCancellationBatch(request);
      });

    await test.step('Send cancel request without auth', async () => {
      const res = await request.post(
        buildUrl('/batch-operations/{batchOperationKey}/cancellation', {
          batchOperationKey: key,
        }),
        {
          data: {},
        },
      );
      await assertUnauthorizedRequest(res);
    });
  });
});
