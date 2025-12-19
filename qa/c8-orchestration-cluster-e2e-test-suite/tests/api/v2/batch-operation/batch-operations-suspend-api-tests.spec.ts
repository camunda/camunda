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
  assertConflictRequest,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {
  createCancellationBatch,
  createCompletedBatchOperation,
  expectBatchState,
  notFoundDetail,
  resumeBatchOperation,
  suspendBatchOperation,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe('Suspend Batch Operation Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/batch_suspension_process.bpmn']);
  });

  test('Suspend active batch operation returns 204 and status becomes SUSPENDED', async ({
    request,
  }) => {
    const key =
      await test.step('Create cancelable batch operation', async () => {
        return createCancellationBatch(request, 3, 'batch_suspension_process');
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

  test('Suspend batch operation twice fails on second request', async ({
    request,
  }) => {
    const key =
      await test.step('Create cancelable batch operation', async () => {
        return createCancellationBatch(request, 3, 'batch_suspension_process');
      });

    await test.step('Suspend batch operation once', async () => {
      const res = await suspendBatchOperation(request, key);
      await assertStatusCode(res, 204);
    });

    await test.step('Wait for suspended state', async () => {
      await expectBatchState(request, key, 'SUSPENDED');
    });

    await test.step('Suspend already suspended batch operation', async () => {
      const doubleRes = await suspendBatchOperation(request, key, 409);
      await assertConflictRequest(doubleRes, 'INVALID_STATE');
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
      buildUrl('/batch-operations/not-a-valid-key/suspension'),
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
      buildUrl(`/batch-operations/${key}/suspension`),
      {
        data: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });
});
