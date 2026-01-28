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

export async function cancelBatchOperation(
  request: APIRequestContext,
  batchOperationKey: string,
) {
  return request.post(
    buildUrl(`/batch-operations/${batchOperationKey}/cancellation`),
    {
      headers: jsonHeaders(),
    },
  );
}

export async function suspendBatchOperation(
  request: APIRequestContext,
  batchOperationKey: string,
  expectedStatusCode = 204,
) {
  const result: Record<string, unknown> = {};
  await expect(async () => {
    const res = await request.post(
      buildUrl(`/batch-operations/${batchOperationKey}/suspension`),
      {
        headers: jsonHeaders(),
      },
    );
    result.response = res;
    await assertStatusCode(res, expectedStatusCode);
  }).toPass(defaultAssertionOptions);
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
      buildUrl(`/batch-operations/${batchOperationKey}/resumption`),
      {
        headers: jsonHeaders(),
      },
    );
    result.response = res;
    await assertStatusCode(res, expectedStatusCode);
  }).toPass(defaultAssertionOptions);
  return result.response as APIResponse;
}

export async function createCompletedBatchOperation(
  request: APIRequestContext,
) {
  const key = await createCancellationBatch(request);

  await expect(async () => {
    const res = await request.get(buildUrl(`/batch-operations/${key}`), {
      headers: jsonHeaders(),
    });
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.state).toBe('COMPLETED');
  }).toPass({
    intervals: [5_000, 10_000, 10_000, 15_000, 20_000],
    timeout: 90_000,
  });

  return key;
}

export async function expectBatchState(
  request: APIRequestContext,
  batchOperationKey: string,
  expectedState: string,
) {
  await expect(async () => {
    const statusRes = await request.get(
      buildUrl(`/batch-operations/${batchOperationKey}`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(statusRes, 200);
    const body = await statusRes.json();
    expect(body.state).toBe(expectedState);
  }).toPass({
    intervals: [5_000, 10_000, 15_000, 25_000, 35_000],
    timeout: 120_000,
  });
}

export const notFoundDetail = (key: string) =>
  `Command 'SUSPEND' rejected with code 'NOT_FOUND': Expected to suspend a batch operation with key '${key}', but no such batch operation was found`;
