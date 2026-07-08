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
import {validateResponse} from 'json-body-assertions';

// A freshly created batch operation can take longer than the default 30s
// to be visible to the cancel/suspend/resume commands on a loaded shared
// cluster (404 → 204). Use a more generous budget here for batch operation
// lifecycle actions while the engine catches up. The 90s budget proved tight
// when multiple cancellation batches (10 instances each) accumulate within a
// single spec file, so allow up to 240s with a longer tail interval.
export const batchOperationLifecycleOptions = {
  intervals: [5_000, 10_000, 10_000, 15_000, 20_000, 30_000, 45_000],
  timeout: 240_000,
};

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
      buildUrl(`/batch-operations/${batchOperationKey}/resumption`),
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
    const res = await request.get(buildUrl(`/batch-operations/${key}`), {
      headers: jsonHeaders(),
    });
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

// Post-migration user-task search has to wait for the secondary-storage
// indexer to reflect the migrated elementId. The default 30s budget is
// too tight on a loaded shared cluster; give it more room.
export const postMigrationAssertionOptions = {
  intervals: [5_000, 10_000, 15_000, 25_000, 35_000],
  timeout: 90_000,
};

export const notFoundDetail = (key: string) =>
  `Command 'SUSPEND' rejected with code 'NOT_FOUND': Expected to suspend a batch operation with key '${key}', but no such batch operation was found`;

// Batch states that are not yet settled: the batch is still starting up (CREATED)
// or running (ACTIVE), so its lifecycle outcome is not decided yet.
const PENDING_BATCH_STATES = ['CREATED', 'ACTIVE'];

// Polls the batch operation through the query API until it reaches a settled state
// (anything other than CREATED/ACTIVE), then returns that state. Query-side reads
// are eventually consistent on Elasticsearch/OpenSearch, so a freshly created (or
// just suspended/cancelled) batch can return 404 or a stale CREATED/ACTIVE
// snapshot for a short while — this is indexing lag, not a terminal outcome, so it
// is polled through rather than treated as a failure. Keeping the lag handling
// here, separate from the create-then-command retry below, is what lets the whole
// cycle work on ES/OS backings: a lagging read must never discard a batch that was
// already caught active.
async function waitForSettledBatchState(
  request: APIRequestContext,
  batchOperationKey: string,
): Promise<string> {
  const result: Record<string, string> = {};
  await expect(async () => {
    const res = await request.get(
      buildUrl(`/batch-operations/${batchOperationKey}`),
      {headers: jsonHeaders()},
    );
    // A 404 here is query-side indexing lag, not a terminal state: keep polling.
    expect(res.status()).toBe(200);
    const body = await res.json();
    const state = body.state as string;
    // Wait until the batch has settled; the caller decides whether the settled
    // state is the expected one.
    expect(PENDING_BATCH_STATES).not.toContain(state);
    result.state = state;
  }).toPass({
    intervals: [1_000, 2_000, 2_000, 5_000, 10_000, 15_000],
    timeout: 120_000,
  });
  return result.state;
}

// Catching a cancellation batch in a non-terminal state so it can be suspended or
// cancelled is an inherent engine race that cannot be won by retrying a single
// batch. The scheduler (default 1s interval) picks a freshly created batch up at a
// random phase, and a small batch is initialized and fully executed within roughly
// one scheduler cycle, so a batch created just before a tick reaches COMPLETED
// before the suspend/cancel command is processed on the stream and is then
// permanently rejected with NOT_FOUND. The only robust approach is to retry the
// whole cycle: create a fresh batch and re-issue the command until one lands while
// the batch is still active. The command race (retry the whole cycle) and the
// query-side indexing lag (tolerated in waitForSettledBatchState) are handled
// separately on purpose — an indexing-lag 404 must not trigger a recreate, or the
// cycle churns forever on ES/OS backings, which is what an earlier version did.
async function createBatchAndReachLifecycleState(
  request: APIRequestContext,
  action: 'suspension' | 'cancellation',
  expectedState: 'SUSPENDED' | 'CANCELED',
  numberOfInstances: number,
  processDefinitionId: string,
): Promise<string> {
  const result: Record<string, string> = {};
  await expect(async () => {
    const key = await createCancellationBatch(
      request,
      numberOfInstances,
      processDefinitionId,
    );

    // Single-shot lifecycle command (no inner retry): a non-204 response means the
    // batch already reached a terminal state before the command was processed, so
    // this attempt lost the race and must start over with a fresh batch.
    const res = await request.post(
      buildUrl(`/batch-operations/${key}/${action}`),
      {headers: jsonHeaders()},
    );
    await assertStatusCode(res, 204);

    // The command was accepted while the batch was active. Confirm it settles on
    // the expected state; a settled COMPLETED means the command was overtaken by
    // the batch finishing, so restart the whole cycle. The read tolerates
    // query-side indexing lag, so a lagging ES/OS read never discards this batch.
    const settledState = await waitForSettledBatchState(request, key);
    expect(settledState).toBe(expectedState);
    result.key = key;
  }).toPass({
    intervals: [1_000, 2_000, 5_000, 5_000, 10_000, 15_000],
    timeout: 240_000,
  });
  return result.key;
}

// Creates a cancellation batch and reliably suspends it while active, returning a
// batch key whose state is confirmed SUSPENDED. See
// createBatchAndReachLifecycleState for why the whole cycle is retried.
export async function createSuspendedBatchOperation(
  request: APIRequestContext,
  numberOfInstances = 100,
  processDefinitionId = 'batch_suspension_process',
): Promise<string> {
  return createBatchAndReachLifecycleState(
    request,
    'suspension',
    'SUSPENDED',
    numberOfInstances,
    processDefinitionId,
  );
}

// Creates a cancellation batch and reliably cancels it while active (first cancel
// returns 204), returning a batch key whose state is confirmed CANCELED. See
// createBatchAndReachLifecycleState for why the whole cycle is retried.
export async function createCanceledBatchOperation(
  request: APIRequestContext,
  numberOfInstances = 100,
  processDefinitionId = 'batch_cancellation_process',
): Promise<string> {
  return createBatchAndReachLifecycleState(
    request,
    'cancellation',
    'CANCELED',
    numberOfInstances,
    processDefinitionId,
  );
}
