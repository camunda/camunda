/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, expect} from '@playwright/test';
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
  }).toPass(defaultAssertionOptions);

  return key;
}
