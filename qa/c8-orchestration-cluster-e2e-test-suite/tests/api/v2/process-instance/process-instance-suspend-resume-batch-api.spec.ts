/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, expect, test} from '@playwright/test';
import {
  cancelProcessInstance,
  createInstances,
} from '../../../../utils/zeebeClient';
import {
  assertInvalidArgument,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {
  activateSingleJob,
  expectProcessInstanceCount,
  completeJob,
  deployServiceTaskProcess,
  expectBatchState,
  expectProcessState,
  expectSuspendedDate,
} from '@requestHelpers';
import {
  extendedAssertionOptions,
  uniquePrefixedId,
} from '../../../../utils/constants';

/**
 * Each test deploys its own definition and filters its batch on that id.
 * businessId cannot group instances — it is unique per instance and a second
 * create with the same value is rejected with 409.
 *
 * A batch resolves its items from secondary storage when it is created, and
 * each operation selects on state: suspend takes ACTIVE, resume takes
 * SUSPENDED, cancel takes either. So every batch here waits for its inputs to
 * be visible *in the state it selects on* first — otherwise it silently
 * resolves to nothing and still reports COMPLETED.
 */

const INSTANCE_COUNT = 3;
const instancesToCancel: string[] = [];

async function startInstances(prefix: string, count = INSTANCE_COUNT) {
  const processDefinitionId = uniquePrefixedId(prefix);
  const jobType = uniquePrefixedId(`${prefix}-job`);
  await deployServiceTaskProcess(processDefinitionId, jobType);
  const instances = await createInstances(processDefinitionId, 1, count);
  const processInstanceKeys = instances.map((i) =>
    String(i.processInstanceKey),
  );
  instancesToCancel.push(...processInstanceKeys);
  return {processDefinitionId, jobType, processInstanceKeys};
}

async function runBatch(
  request: APIRequestContext,
  path:
    | '/process-instances/suspension'
    | '/process-instances/resumption'
    | '/process-instances/cancellation',
  filter: Record<string, unknown>,
): Promise<string> {
  const res = await request.post(buildUrl(path), {
    headers: jsonHeaders(),
    data: {filter},
  });
  await assertStatusCode(res, 200);
  return String((await res.json()).batchOperationKey);
}

/** Waits for the inputs in the exact state the batch's item provider selects. */
async function expectAllInState(
  request: APIRequestContext,
  processDefinitionId: string,
  state: string,
  count = INSTANCE_COUNT,
) {
  await expectProcessInstanceCount(
    request,
    {processDefinitionId, state: {$eq: state}},
    count,
    extendedAssertionOptions,
  );
}

/**
 * One batch per operation, asserted strictly: partial targeting is the
 * regression these tests exist to catch, so re-issuing a batch for whatever the
 * previous one missed would hide it. Callers wait for their inputs to be
 * visible in the state the operation's item provider selects on first.
 */
async function runBatchUntilAllInState(
  request: APIRequestContext,
  path:
    | '/process-instances/suspension'
    | '/process-instances/resumption'
    | '/process-instances/cancellation',
  processDefinitionId: string,
  targetState: string,
  count = INSTANCE_COUNT,
) {
  const batchOperationKey = await runBatch(request, path, {
    processDefinitionId,
  });
  await expectBatchState(request, batchOperationKey, 'COMPLETED');
  // The state flips to COMPLETED before the counters finish aggregating, so
  // reading them once can see total=3 alongside completed=0.
  await expect(async () => {
    const res = await request.get(
      buildUrl('/batch-operations/{batchOperationKey}', {batchOperationKey}),
      {headers: jsonHeaders()},
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    expect(body.operationsTotalCount).toBe(count);
    expect(body.operationsCompletedCount).toBe(count);
    expect(body.operationsFailedCount).toBe(0);
  }).toPass(extendedAssertionOptions);
  await expectAllInState(request, processDefinitionId, targetState, count);
}

async function completeAll(
  request: APIRequestContext,
  jobType: string,
  processInstanceKeys: string[],
) {
  for (const processInstanceKey of processInstanceKeys) {
    const jobKey = await activateSingleJob(
      request,
      jobType,
      processInstanceKey,
    );
    await completeJob(request, jobKey);
    await expectProcessState(
      request,
      processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  }
}

test.describe('Process Instance Batch Suspend and Resume API', () => {
  test.afterAll(async () => {
    for (const processInstanceKey of instancesToCancel) {
      try {
        await cancelProcessInstance(processInstanceKey);
      } catch {
        // Already terminal.
      }
    }
    instancesToCancel.length = 0;
  });

  test('A batch suspends every instance the filter matches and nothing else', async ({
    request,
  }) => {
    const subject = await startInstances('sr-batch');
    const control = await startInstances('sr-batch-control');

    await expectAllInState(request, subject.processDefinitionId, 'ACTIVE');

    await runBatchUntilAllInState(
      request,
      '/process-instances/suspension',
      subject.processDefinitionId,
      'SUSPENDED',
    );

    for (const processInstanceKey of subject.processInstanceKeys) {
      await expectProcessState(
        request,
        processInstanceKey,
        'SUSPENDED',
        extendedAssertionOptions,
      );
      await expectSuspendedDate(
        request,
        processInstanceKey,
        true,
        extendedAssertionOptions,
      );
    }
    for (const processInstanceKey of control.processInstanceKeys) {
      await expectProcessState(
        request,
        processInstanceKey,
        'ACTIVE',
        extendedAssertionOptions,
      );
      await expectSuspendedDate(
        request,
        processInstanceKey,
        false,
        extendedAssertionOptions,
      );
    }

    await expectAllInState(request, subject.processDefinitionId, 'SUSPENDED');
    await runBatchUntilAllInState(
      request,
      '/process-instances/resumption',
      subject.processDefinitionId,
      'ACTIVE',
    );
    await completeAll(request, subject.jobType, subject.processInstanceKeys);
    await completeAll(request, control.jobType, control.processInstanceKeys);
  });

  test('A batch resume returns every instance to ACTIVE and clears its suspension date', async ({
    request,
  }) => {
    const {processDefinitionId, jobType, processInstanceKeys} =
      await startInstances('sr-batch-resume');
    await expectAllInState(request, processDefinitionId, 'ACTIVE');

    await runBatchUntilAllInState(
      request,
      '/process-instances/suspension',
      processDefinitionId,
      'SUSPENDED',
    );

    await expectAllInState(request, processDefinitionId, 'SUSPENDED');
    await runBatchUntilAllInState(
      request,
      '/process-instances/resumption',
      processDefinitionId,
      'ACTIVE',
    );

    for (const processInstanceKey of processInstanceKeys) {
      await expectProcessState(
        request,
        processInstanceKey,
        'ACTIVE',
        extendedAssertionOptions,
      );
      await expectSuspendedDate(
        request,
        processInstanceKey,
        false,
        extendedAssertionOptions,
      );
    }

    await completeAll(request, jobType, processInstanceKeys);
  });

  test('A batch whose filter matches nothing completes cleanly', async ({
    request,
  }) => {
    const batchOperationKey = await runBatch(
      request,
      '/process-instances/suspension',
      {processDefinitionId: uniquePrefixedId('sr-batch-nomatch')},
    );
    await expectBatchState(request, batchOperationKey, 'COMPLETED');
    const res = await request.get(
      buildUrl('/batch-operations/{batchOperationKey}', {batchOperationKey}),
      {headers: jsonHeaders()},
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    expect(body.operationsTotalCount).toBe(0);
    expect(body.operationsFailedCount).toBe(0);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('A batch without usable filter criteria is rejected', async ({
    request,
  }) => {
    for (const path of [
      '/process-instances/suspension',
      '/process-instances/resumption',
    ] as const) {
      await assertInvalidArgument(
        await request.post(buildUrl(path), {
          headers: jsonHeaders(),
          data: {},
        }),
        400,
        'No filter provided.',
      );
      await assertInvalidArgument(
        await request.post(buildUrl(path), {
          headers: jsonHeaders(),
          data: {filter: {}},
        }),
        400,
        'At least one of filter criteria is required.',
      );
    }
  });

  test('A batch can cancel instances that are suspended', async ({request}) => {
    const {processDefinitionId, processInstanceKeys} =
      await startInstances('sr-batch-cancel');
    await expectAllInState(request, processDefinitionId, 'ACTIVE');

    await runBatchUntilAllInState(
      request,
      '/process-instances/suspension',
      processDefinitionId,
      'SUSPENDED',
    );

    // Suspension is not a shield: TERMINATED is the expected end state here, so
    // this is the one test in this file that does not resume.
    await expectAllInState(request, processDefinitionId, 'SUSPENDED');
    await runBatchUntilAllInState(
      request,
      '/process-instances/cancellation',
      processDefinitionId,
      'TERMINATED',
    );

    for (const processInstanceKey of processInstanceKeys) {
      await expectProcessState(
        request,
        processInstanceKey,
        'TERMINATED',
        extendedAssertionOptions,
      );
      await expectSuspendedDate(
        request,
        processInstanceKey,
        false,
        extendedAssertionOptions,
      );
    }
  });
});
