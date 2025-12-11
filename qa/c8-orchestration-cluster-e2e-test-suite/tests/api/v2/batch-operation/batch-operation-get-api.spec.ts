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
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {createCancellationBatch} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */

test.describe('Get Batch Operation Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/batch_cancellation_process.bpmn']);
  });

  test('Get Batch Operation - Success', async ({request}) => {
    const localState: Record<string, unknown> = {};

    await test.step('First, create a cancellation batch operation', async () => {
      localState['batchOperationKey'] = await createCancellationBatch(request);
    });

    await test.step('Get Batch Operation', async () => {
      await expect(async () => {
        const batchOperationKey = localState['batchOperationKey'] as string;
        const res = await request.get(
          buildUrl(`/batch-operations/${batchOperationKey}`),
          {
            headers: jsonHeaders(),
          },
        );

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/batch-operations/{batchOperationKey}',
            method: 'GET',
            status: '200',
          },
          res,
        );

        localState['responseJson'] = await res.json();
        const json = localState['responseJson'] as {[key: string]: unknown};
        expect(json['batchOperationKey']).toBe(localState.batchOperationKey);
        expect(json['state']).toBeDefined();
        expect(json['batchOperationType']).toBe('CANCEL_PROCESS_INSTANCE');
        expect(json['operationsTotalCount']).toBeGreaterThan(0);
        expect(json['operationsFailedCount']).toBeGreaterThanOrEqual(0);
        expect(json['operationsCompletedCount']).toBeGreaterThanOrEqual(0);
        expect(json['errors']).toBeDefined();
        //Skipped due to bug 42165: https://github.com/camunda/camunda/issues/42165
        // expect(json['startDate']).toBeDefined();
      }).toPass({
        intervals: [5_000, 10_000, 15_000, 25_000],
        timeout: 60_000,
      });
    });
  });

  test('Get Batch Operation - Not Found', async ({request}) => {
    const unknownBatchOperationKey = '2251799813999999';
    const res = await request.get(
      buildUrl(`/batch-operations/${unknownBatchOperationKey}`),
      {
        headers: jsonHeaders(),
      },
    );

    await assertNotFoundRequest(
      res,
      `Batch Operation with id '${unknownBatchOperationKey}' not found`,
    );
  });

  test('Get Batch Operation - Unauthorized', async ({request}) => {
    const localState: Record<string, unknown> = {};

    await test.step('Create cancellation batch operation', async () => {
      localState['batchOperationKey'] = await createCancellationBatch(request);
    });

    await test.step('Get Batch Operation without auth', async () => {
      const authRes = await request.get(
        buildUrl(`/batch-operations/${localState['batchOperationKey']}`),
        {
          // No Authorization header on purpose
          headers: {
            'Content-Type': 'application/json',
          },
        },
      );

      await assertUnauthorizedRequest(authRes);
    });
  });
});
