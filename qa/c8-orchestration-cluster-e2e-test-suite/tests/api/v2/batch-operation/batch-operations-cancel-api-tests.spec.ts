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
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {
  createCancellationBatch,
  cancelBatchOperation,
  createCanceledBatchOperation,
  createCompletedBatchOperation,
  expectBatchState,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */

// Run serially (not .parallel) to match the reliably-passing suspend suite.
// Cancelling an ACTIVE batch is a race: the cancel command must arrive while
// the batch is still non-terminal (CREATED/INITIALIZED/SUSPENDED), before the
// engine finishes cancelling every instance and the batch turns COMPLETED
// (after which cancel is permanently rejected with NOT_FOUND). On a fast
// single-partition all-in-one RDBMS the ACTIVE window is only a few seconds.
// Under test.describe.parallel, sibling tests each fire 30 concurrent
// instance-creation requests at once, delaying this test's own cancel call
// until after its batch has already completed — losing the race. Serializing
// removes that self-inflicted contention so each cancel is issued promptly.
test.describe('Cancel Batch Operation Tests', () => {
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
    const key =
      await test.step('Create batch operation and cancel it while active', async () => {
        // Same scheduler-phase race as the twice-fails test: a batch that is
        // fully executed within one ~1s scheduler cycle reaches COMPLETED before
        // the cancel is processed and is then permanently rejected with
        // NOT_FOUND. createCanceledBatchOperation retries the whole
        // create-then-cancel cycle until one batch is caught active and settles
        // on CANCELED (the first cancel returns 204), instead of racing a single
        // fast-completing batch.
        return createCanceledBatchOperation(request);
      });

    await test.step('Confirm batch operation is canceled', async () => {
      await expectBatchState(request, key, 'CANCELED');
    });
  });

  test('Cancel batch operation twice fails on second request', async ({
    request,
  }) => {
    const key =
      await test.step('Create batch operation and cancel it while active', async () => {
        // Catching the batch active for the first cancel is a scheduler-phase
        // race that cannot be won by retrying a single batch: a batch of <=100
        // items is initialized and fully executed within one ~1s scheduler
        // cycle, so a batch created just before a tick reaches COMPLETED before
        // the cancel is processed and is then permanently rejected with
        // NOT_FOUND. createCanceledBatchOperation retries the whole
        // create-then-cancel cycle until one batch is caught active and settles
        // on CANCELED (the first cancel returns 204).
        return createCanceledBatchOperation(request);
      });

    await test.step('Send second cancel request and assert failure', async () => {
      const secondRes = await cancelBatchOperation(request, key);

      await assertNotFoundRequest(
        secondRes,
        `Command 'CANCEL' rejected with code 'NOT_FOUND': Expected to cancel a batch operation with key '${key}', but no such batch operation was found`,
      );
    });
  });

  test('Cancel finished batch operation returns 404', async ({request}) => {
    const key = state.finishedBatchOperationKey as string;

    await test.step('Cancel finished batch operation', async () => {
      const res = await cancelBatchOperation(request, key);
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
      const res = await cancelBatchOperation(request, unknownKey);
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
        buildUrl('/batch-operations/not-a-valid-key/cancellation'),
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
        buildUrl(`/batch-operations/${key}/cancellation`),
        {
          data: {},
        },
      );
      await assertUnauthorizedRequest(res);
    });
  });
});
