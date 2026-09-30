/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, APIResponse, expect} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from 'utils/http';
import {createCancellationBatch} from '@requestHelpers';
import {defaultAssertionOptions} from 'utils/constants';
import {validateResponse} from 'json-body-assertions';

export async function cancelBatchOperation(
  request: APIRequestContext,
  batchOperationKey: string,
  expectedStatusCode = 204,
) {
  const result: Record<string, APIResponse> = {};
  await expect(async () => {
    const res = await request.post(
      buildUrl('/batch-operations/{batchOperationKey}/cancellation', {
        batchOperationKey,
      }),
      {
        headers: jsonHeaders(),
      },
    );
    result.response = res;
    await assertStatusCode(res, expectedStatusCode);
  }).toPass(batchOperationLifecycleOptions);
  return result.response as APIResponse;
}

// A freshly created batch operation can take longer than the default 30s
// to be visible to the suspend/resume commands on a loaded shared cluster
// (404 → 204). Use a more generous budget here for batch operation lifecycle
// actions while the engine catches up. The 180s budget proved tight when
// multiple cancellation batches (30 instances each) accumulate within a
// single spec file, so allow up to 240s with a longer tail interval.
const batchOperationLifecycleOptions = {
  intervals: [5_000, 10_000, 10_000, 15_000, 20_000, 30_000, 45_000],
  timeout: 240_000,
};

export async function suspendBatchOperation(
  request: APIRequestContext,
  batchOperationKey: string,
  expectedStatusCode = 204,
) {
  const result: Record<string, unknown> = {};
  await expect(async () => {
    const res = await request.post(
      buildUrl('/batch-operations/{batchOperationKey}/suspension', {
        batchOperationKey,
      }),
      {
        headers: jsonHeaders(),
      },
    );
    result.response = res;
    await assertStatusCode(res, expectedStatusCode);
  }).toPass(batchOperationLifecycleOptions);
  return result.response as APIResponse;
}

export async function resumeBatchOperation(
  request: APIRequestContext,
  batchOperationKey: string,
  expectedStatusCode = 204,
) {
  const result: Record<string, unknown> = {};
  await expect(async () => {
    const res = await request.post(
      buildUrl('/batch-operations/{batchOperationKey}/resumption', {
        batchOperationKey,
      }),
      {
        headers: jsonHeaders(),
      },
    );
    result.response = res;
    await assertStatusCode(res, expectedStatusCode);
  }).toPass(batchOperationLifecycleOptions);
  return result.response as APIResponse;
}

export async function createCompletedBatchOperation(
  request: APIRequestContext,
) {
  const key = await createCancellationBatch(request);

  await expect(async () => {
    const res = await request.get(
      buildUrl('/batch-operations/{batchOperationKey}', {
        batchOperationKey: key,
      }),
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
    const json = await res.json();
    expect(json.state).toBe('COMPLETED');
  }).toPass({
    intervals: [5_000, 10_000, 10_000, 15_000, 20_000],
    timeout: 90_000,
  });

  return key;
}

export async function getBatchOperationState(
  request: APIRequestContext,
  batchOperationKey: string,
): Promise<string> {
  const result: {state?: string} = {};
  await expect(async () => {
    const res = await request.get(
      buildUrl('/batch-operations/{batchOperationKey}', {batchOperationKey}),
      {headers: jsonHeaders()},
    );
    // A freshly created batch operation can briefly 404 before the read
    // model catches up with the write it was created from; retry through
    // that instead of failing the caller on a transient gap.
    await assertStatusCode(res, 200);
    result.state = (await res.json()).state;
  }).toPass({
    // A 1000-instance cancellation batch loads the write path enough that the
    // batch-operation read model can take well over 15s to materialize on a
    // nightly RDBMS shard, producing a permanent 404 within the old budget.
    // Keep the fast early retries but extend the ceiling to the 60s the other
    // batch-operation polling in this file already tolerates.
    intervals: [1_000, 2_000, 3_000, 5_000, 10_000, 15_000],
    timeout: 60_000,
  });
  return result.state as string;
}

export async function expectBatchState(
  request: APIRequestContext,
  batchOperationKey: string,
  expectedState: string,
) {
  await expect(async () => {
    const statusRes = await request.get(
      buildUrl('/batch-operations/{batchOperationKey}', {batchOperationKey}),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(statusRes, 200);
    await validateResponse(
      {
        path: '/batch-operations/{batchOperationKey}',
        method: 'GET',
        status: '200',
      },
      statusRes,
    );
    const body = await statusRes.json();
    expect(body.state).toBe(expectedState);
  }).toPass({
    intervals: [5_000, 10_000, 15_000, 25_000, 35_000],
    timeout: 120_000,
  });
}

// Post-migration user-task search waits for the secondary-storage indexer to
// reflect the migrated elementId. Used as the second phase after
// searchElementInstanceByElementIdAndState confirms the engine already moved
// the token, so the pipeline is partially warm and 120s is sufficient.
export const postMigrationAssertionOptions = {
  intervals: [5_000, 10_000, 15_000, 20_000, 25_000, 25_000],
  timeout: 120_000,
};

export const notFoundDetail = (key: string) =>
  `Command 'SUSPEND' rejected with code 'NOT_FOUND': Expected to suspend a batch operation with key '${key}', but no such batch operation was found`;

export async function findCompletedBatchKey(
  request: APIRequestContext,
): Promise<string> {
  const result: {batchKey?: string} = {};
  await expect(async () => {
    const res = await request.post(buildUrl('/batch-operations/search'), {
      headers: jsonHeaders(),
      data: {
        filter: {
          state: 'COMPLETED',
          operationType: 'CANCEL_PROCESS_INSTANCE',
        },
        sort: [{field: 'startDate', order: 'DESC'}],
        page: {from: 0, limit: 1},
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
    expect(body.items?.length).toBeGreaterThanOrEqual(1);
    expect(body.items[0].batchOperationKey).toBeDefined();
    result.batchKey = String(body.items[0].batchOperationKey);
  }).toPass(defaultAssertionOptions);
  if (result.batchKey === undefined) {
    throw new Error(
      'Expected to find a completed batch operation key, but none was returned.',
    );
  }
  return result.batchKey;
}
