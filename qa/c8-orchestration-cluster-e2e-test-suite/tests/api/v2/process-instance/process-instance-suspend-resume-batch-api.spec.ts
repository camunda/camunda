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
  completeJob,
  deployServiceTaskProcess,
  expectBatchState,
  expectProcessInstanceCount,
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

async function expectBatchCounts(
  request: APIRequestContext,
  batchOperationKey: string,
  expected: {total: number; completed: number; failed: number},
) {
  await expectBatchState(request, batchOperationKey, 'COMPLETED');
  const res = await request.get(
    buildUrl('/batch-operations/{batchOperationKey}', {batchOperationKey}),
    {headers: jsonHeaders()},
  );
  await assertStatusCode(res, 200);
  const body = await res.json();
  expect(body.operationsTotalCount).toBe(expected.total);
  expect(body.operationsCompletedCount).toBe(expected.completed);
  expect(body.operationsFailedCount).toBe(expected.failed);
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

    // A batch resolves its items from secondary storage when it is created, so
    // one created mid-propagation covers only the instances already indexed.
    await expectProcessInstanceCount(
      request,
      {processDefinitionId: subject.processDefinitionId},
      INSTANCE_COUNT,
      extendedAssertionOptions,
    );

    const batchKey = await runBatch(request, '/process-instances/suspension', {
      processDefinitionId: subject.processDefinitionId,
    });
    await expectBatchCounts(request, batchKey, {
      total: INSTANCE_COUNT,
      completed: INSTANCE_COUNT,
      failed: 0,
    });

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

    const resumeKey = await runBatch(request, '/process-instances/resumption', {
      processDefinitionId: subject.processDefinitionId,
    });
    await expectBatchCounts(request, resumeKey, {
      total: INSTANCE_COUNT,
      completed: INSTANCE_COUNT,
      failed: 0,
    });
    await completeAll(request, subject.jobType, subject.processInstanceKeys);
    await completeAll(request, control.jobType, control.processInstanceKeys);
  });

  test('A batch resume returns every instance to ACTIVE and clears its suspension date', async ({
    request,
  }) => {
    const {processDefinitionId, jobType, processInstanceKeys} =
      await startInstances('sr-batch-resume');
    await expectProcessInstanceCount(
      request,
      {processDefinitionId},
      INSTANCE_COUNT,
      extendedAssertionOptions,
    );

    const suspendKey = await runBatch(
      request,
      '/process-instances/suspension',
      {processDefinitionId},
    );
    await expectBatchCounts(request, suspendKey, {
      total: INSTANCE_COUNT,
      completed: INSTANCE_COUNT,
      failed: 0,
    });

    const resumeKey = await runBatch(request, '/process-instances/resumption', {
      processDefinitionId,
    });
    await expectBatchCounts(request, resumeKey, {
      total: INSTANCE_COUNT,
      completed: INSTANCE_COUNT,
      failed: 0,
    });

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

  // eslint-disable-next-line playwright/expect-expect
  test('A batch whose filter matches nothing completes cleanly', async ({
    request,
  }) => {
    const batchKey = await runBatch(request, '/process-instances/suspension', {
      processDefinitionId: uniquePrefixedId('sr-batch-nomatch'),
    });
    await expectBatchCounts(request, batchKey, {
      total: 0,
      completed: 0,
      failed: 0,
    });
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
    await expectProcessInstanceCount(
      request,
      {processDefinitionId},
      INSTANCE_COUNT,
      extendedAssertionOptions,
    );

    const suspendKey = await runBatch(
      request,
      '/process-instances/suspension',
      {processDefinitionId},
    );
    await expectBatchCounts(request, suspendKey, {
      total: INSTANCE_COUNT,
      completed: INSTANCE_COUNT,
      failed: 0,
    });

    // Suspension is not a shield: TERMINATED is the expected end state here, so
    // this is the one test in this file that does not resume.
    const cancelKey = await runBatch(
      request,
      '/process-instances/cancellation',
      {processDefinitionId},
    );
    await expectBatchCounts(request, cancelKey, {
      total: INSTANCE_COUNT,
      completed: INSTANCE_COUNT,
      failed: 0,
    });

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
