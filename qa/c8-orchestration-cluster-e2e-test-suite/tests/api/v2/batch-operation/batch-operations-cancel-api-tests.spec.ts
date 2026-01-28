/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {deploy} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  createCancellationBatch,
  cancelBatchOperation,
  createCompletedBatchOperation,
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
    const key =
      await test.step('Create cancelable batch operation', async () => {
        return createCancellationBatch(request, 10);
      });

    await test.step('Cancel batch operation', async () => {
      const res = await cancelBatchOperation(request, key);
      await assertStatusCode(res, 204);
    });

    await test.step('Poll batch status', async () => {
      await expect(async () => {
        const statusRes = await request.get(
          buildUrl(`/batch-operations/${key}`),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(statusRes, 200);
        const body = await statusRes.json();
        expect(body.state).toBe('CANCELED');
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Cancel batch operation twice fails on second request', async ({
    request,
  }) => {
    const key =
      await test.step('Create cancelable batch operation', async () => {
        return createCancellationBatch(request, 10);
      });

    await test.step('Send first cancel request', async () => {
      const firstRes = await cancelBatchOperation(request, key);
      await assertStatusCode(firstRes, 204);
    });

    await test.step('Send second cancel request and assert failure', async () => {
      const secondRes = await cancelBatchOperation(request, key);
      const status = secondRes.status();
      expect(status).toBe(404);

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
