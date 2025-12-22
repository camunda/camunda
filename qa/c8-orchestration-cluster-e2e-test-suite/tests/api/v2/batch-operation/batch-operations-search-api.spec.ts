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
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {createCancellationBatch} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */

test.describe.parallel('Search Batch Operation Tests', () => {
  const state: {batchOperations: string[]} = {
    batchOperations: [],
  };

  test.beforeAll(async ({request}) => {
    await deploy(['./resources/batch_cancellation_process.bpmn']);
    for (let i = 0; i < 3; i++) {
      const key = await createCancellationBatch(request);
      state.batchOperations.push(key);
    }
  });

  test('Search Batch Operations Success', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/batch-operations/search'), {
        headers: jsonHeaders(),
        data: {
          page: {from: 0, limit: 5},
          sort: [{field: 'startDate', order: 'DESC'}],
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/batch-operations/search',
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(
        state.batchOperations.length,
      );
      expect(body.items.length).toBeGreaterThanOrEqual(1);

      const startDates = body.items.map(
        (item: Record<string, string>) => item.startDate,
      );
      const sortedDates = [...startDates].sort().reverse();
      expect(startDates).toEqual(sortedDates);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Batch Operations Filter By State And Type', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/batch-operations/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            state: 'COMPLETED',
            operationType: 'CANCEL_PROCESS_INSTANCE',
          },
        },
      });

      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThan(0);
      expect(body.items.length).toBe(body.page.totalItems);
      for (const item of body.items) {
        expect(item.state).toBe('COMPLETED');
        expect(item.batchOperationType).toBe('CANCEL_PROCESS_INSTANCE');
      }
    }).toPass(defaultAssertionOptions);
  });

  test('Search Batch Operations Cursor Pagination', async ({request}) => {
    await expect(async () => {
      const firstRes = await request.post(
        buildUrl('/batch-operations/search'),
        {
          headers: jsonHeaders(),
          data: {page: {limit: 2}},
        },
      );
      await assertStatusCode(firstRes, 200);
      const firstJson = await firstRes.json();
      expect(firstJson.items.length).toBeGreaterThan(0);
      expect(firstJson.page.totalItems).toBeGreaterThan(2);
      expect(firstJson.page.endCursor).toBeTruthy();

      const secondRes = await request.post(
        buildUrl('/batch-operations/search'),
        {
          headers: jsonHeaders(),
          data: {page: {after: firstJson.page.endCursor, limit: 2}},
        },
      );
      await assertStatusCode(secondRes, 200);
      const secondJson = await secondRes.json();
      expect(secondJson.items.length).toBeGreaterThan(0);
      expect(secondJson.items.length).toBeLessThanOrEqual(2);
      expect(secondJson.page.startCursor).toBeTruthy();

      const firstPageKeys = new Set(
        firstJson.items.map(
          (item: Record<string, string>) => item.batchOperationKey,
        ),
      );
      for (const item of secondJson.items) {
        expect(firstPageKeys.has(item.batchOperationKey)).toBe(false);
      }
    }).toPass(defaultAssertionOptions);
  });

  test('Search Batch Operations Empty Result', async ({request}) => {
    const res = await request.post(buildUrl('/batch-operations/search'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          state: 'FAILED',
          operationType: 'DELETE_PROCESS_DEFINITION',
        },
        page: {from: 0, limit: 5},
      },
    });

    await assertStatusCode(res, 200);
    const body = await res.json();
    expect(body.page.totalItems).toBe(0);
    expect(body.items.length).toBe(0);
  });

  test('Search Batch Operations Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/batch-operations/search'), {
      data: {},
    });

    await assertUnauthorizedRequest(res);
  });

  //Skipped due to bug 39372: https://github.com/camunda/camunda/issues/39372
  test.skip('Search Batch Operations Invalid Pagination', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/batch-operations/search'), {
        headers: jsonHeaders(),
        data: {
          page: {from: -1, limit: 0},
        },
      });

      await assertBadRequest(res, /page\.(from|limit)/i);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Batch Operations Invalid Sort Field', async ({request}) => {
    const res = await request.post(buildUrl('/batch-operations/search'), {
      headers: jsonHeaders(),
      data: {
        sort: [{field: 'unknownField', order: 'ASC'}],
      },
    });

    await assertBadRequest(
      res,
      "Unexpected value 'unknownField' for enum field 'field'. Use any of the following values: [batchOperationKey, operationType, state, startDate, endDate, actorType, actorId]",
    );
  });
});
