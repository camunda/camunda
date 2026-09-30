/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, test} from '@playwright/test';
import {deploy} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertInvalidState,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {sleep} from '../../../../utils/sleep';
import {
  cancelBatchOperation,
  createCancellationBatch,
  createCompletedBatchOperation,
  expectBatchState,
  getBatchOperationState,
  notFoundDetail,
  resumeBatchOperation,
  suspendBatchOperation,
} from '@requestHelpers';

// Bounded suspend attempt used only by the fresh-batch retry below: unlike
// suspendBatchOperation() (which retries a 404 for up to 240s on the
// assumption it is just not visible yet), a 404 here can also mean the
// batch already reached a terminal state before suspend could land -- in
// which case waiting out the full 240s budget only delays the fresh-batch
// retry that would actually recover. Disambiguate via the batch's own
// state instead of guessing from elapsed time.
async function attemptSuspendBeforeCompletion(
  request: APIRequestContext,
  batchOperationKey: string,
): Promise<'accepted' | 'lost'> {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const res = await request.post(
      buildUrl('/batch-operations/{batchOperationKey}/suspension', {
        batchOperationKey,
      }),
      {headers: jsonHeaders()},
    );
    if (res.status() === 204) {
      return 'accepted';
    }
    if (res.status() === 404) {
      const state = await getBatchOperationState(request, batchOperationKey);
      if (state !== 'ACTIVE') {
        return 'lost';
      }
      await sleep(2_000);
      continue;
    }
    await assertStatusCode(res, 204);
  }
  return 'lost';
}

/* eslint-disable playwright/expect-expect */
test.describe('Suspend & Resume Batch Operation Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/batch_suspension_process.bpmn']);
  });

  test('Suspend active batch operation returns 204 and status becomes SUSPENDED, finally resumes', async ({
    request,
  }) => {
    // Use a large instance count so the batch stays ACTIVE long enough for the
    // suspend command to catch it in flight. 30 instances can finish first,
    // making /suspension return a permanent 404 that the retry budget cannot
    // recover from. Same remedy as the other 500-instance tests in this file.
    const key =
      await test.step('Create cancelable batch operation', async () => {
        return createCancellationBatch(
          request,
          500,
          'batch_suspension_process',
        );
      });

    await test.step('Send suspend request', async () => {
      const res = await suspendBatchOperation(request, key);
      await assertStatusCode(res, 204);
    });

    await test.step('Poll until batch operation is suspended', async () => {
      await expectBatchState(request, key, 'SUSPENDED');
    });

    await test.step('resume batch operation', async () => {
      const res = await resumeBatchOperation(request, key);
      await assertStatusCode(res, 204);
    });
  });

  test('Suspend batch operation twice fails on second request, finally resumes', async ({
    request,
  }) => {
    // Use a large instance count so the batch stays ACTIVE long enough for the
    // suspend command to catch it in flight and be observed as SUSPENDED before
    // it reaches a terminal state. 30 instances can finish first, making
    // /suspension return a permanent 404 that the retry budget cannot recover
    // from. Same remedy as the 500-instance tests below.
    const key =
      await test.step('Create cancelable batch operation', async () => {
        return createCancellationBatch(
          request,
          500,
          'batch_suspension_process',
        );
      });

    await test.step('Suspend batch operation once', async () => {
      const res = await suspendBatchOperation(request, key, 204);
      await assertStatusCode(res, 204);
    });

    await test.step('Wait for suspended state', async () => {
      await expectBatchState(request, key, 'SUSPENDED');
    });

    await test.step('Suspend already suspended batch operation', async () => {
      const doubleRes = await suspendBatchOperation(request, key, 409);
      await assertInvalidState(doubleRes);
    });

    await test.step('resume batch operation', async () => {
      const res = await resumeBatchOperation(request, key);
      await assertStatusCode(res, 204);
    });
  });

  test('Suspend finished batch operation returns 404', async ({request}) => {
    const key =
      await test.step('Create completed batch operation', async () => {
        return createCompletedBatchOperation(request);
      });

    const res = await suspendBatchOperation(request, key, 404);
    await assertNotFoundRequest(res, notFoundDetail(key));
  });

  test('Suspend batch operation with unknown key returns 404', async ({
    request,
  }) => {
    const unknownKey = '2251799813999999';
    const res = await suspendBatchOperation(request, unknownKey, 404);
    await assertNotFoundRequest(res, notFoundDetail(unknownKey));
  });

  test('Suspend batch operation with invalid key returns 400', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/batch-operations/{batchOperationKey}/suspension', {
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

  test('Suspend batch operation without auth returns 401', async ({
    request,
  }) => {
    const key =
      await test.step('Create batch operation for auth test', async () => {
        return createCancellationBatch(request, 3, 'batch_suspension_process');
      });

    const res = await request.post(
      buildUrl('/batch-operations/{batchOperationKey}/suspension', {
        batchOperationKey: key,
      }),
      {
        data: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Suspend active batch operation returns 204 and status becomes SUSPENDED without resume', async ({
    request,
  }) => {
    // Use a large instance count so the batch stays ACTIVE long enough for the
    // suspend command to catch it in flight and be observed as SUSPENDED before
    // it reaches a terminal state. 30 instances can finish first, making
    // /suspension return a permanent 404 that the retry budget cannot recover
    // from. Same remedy as the 500-instance tests below.
    const key = await test.step('Create cancel batch operation', async () => {
      return createCancellationBatch(request, 500, 'batch_suspension_process');
    });

    await test.step('Send suspend request', async () => {
      const res = await suspendBatchOperation(request, key);
      await assertStatusCode(res, 204);
    });

    await test.step('Poll until batch operation is suspended', async () => {
      await expectBatchState(request, key, 'SUSPENDED');
    });
  });

  test('Resume suspended batch operation runs to completion', async ({
    request,
  }) => {
    // Increasing the instance count only widens the window the suspend
    // command has to land before the batch finishes cancelling everything --
    // it can't guarantee it. 500 instances raced to COMPLETED on nightly
    // RDBMS (MSSQL); 1000 -- already the ceiling before the readiness-check
    // $in filter in createCancellationBatch() would need chunking to stay
    // under Oracle's 1000-item IN-list limit (ORA-01795) -- raced to
    // COMPLETED again on nightly RDBMS (Oracle 21c, camunda/camunda#63745).
    // Retry the whole create-and-suspend sequence against a fresh batch when
    // that race is lost, instead of gambling on an even larger instance
    // count that isn't available.
    const key =
      await test.step('Create and suspend a batch operation before it completes', async () => {
        const maxAttempts = 3;
        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
          const candidateKey = await createCancellationBatch(
            request,
            1000,
            'batch_suspension_process',
          );

          const suspendResult = await attemptSuspendBeforeCompletion(
            request,
            candidateKey,
          );
          if (suspendResult === 'lost') {
            console.log(
              `Attempt ${attempt}: batch ${candidateKey} was already terminal before suspend could land; retrying with a fresh batch.`,
            );
            continue;
          }

          let state = await getBatchOperationState(request, candidateKey);
          const pollDeadline = Date.now() + 15_000;
          while (state === 'ACTIVE' && Date.now() < pollDeadline) {
            await sleep(1_000);
            state = await getBatchOperationState(request, candidateKey);
          }

          if (state === 'SUSPENDED') {
            return candidateKey;
          }
          console.log(
            `Attempt ${attempt}: batch ${candidateKey} reached "${state}" before the suspend command landed; retrying with a fresh batch.`,
          );
        }
        throw new Error(
          `Suspend never caught the batch before it completed, after ${maxAttempts} attempts.`,
        );
      });

    await test.step('Resume batch operation', async () => {
      const res = await resumeBatchOperation(request, key);
      await assertStatusCode(res, 204);
    });

    await test.step('Poll until batch operation is completed', async () => {
      await expectBatchState(request, key, 'COMPLETED');
    });
  });

  test('Cancel suspended batch operation transitions to CANCELED', async ({
    request,
  }) => {
    // Use a large instance count so the cancellation batch stays ACTIVE long
    // enough for the suspend command to catch it in flight. A batch of only 30
    // instances can finish cancelling (reaching a terminal state) before the
    // suspend request is processed under nightly contention, which makes the
    // suspend endpoint return a permanent 404 NOT_FOUND. This mirrors the proven
    // suspension pattern in tests/operate/batchOperations.spec.ts.
    const key = await test.step('Create cancel batch operation', async () => {
      return createCancellationBatch(request, 500, 'batch_suspension_process');
    });

    await test.step('Suspend batch operation', async () => {
      const res = await suspendBatchOperation(request, key);
      await assertStatusCode(res, 204);
    });

    await test.step('Poll until batch operation is suspended', async () => {
      await expectBatchState(request, key, 'SUSPENDED');
    });

    await test.step('Cancel suspended batch operation', async () => {
      const res = await cancelBatchOperation(request, key);
      await assertStatusCode(res, 204);
    });

    await test.step('Poll until batch operation is canceled', async () => {
      await expectBatchState(request, key, 'CANCELED');
    });
  });
});
