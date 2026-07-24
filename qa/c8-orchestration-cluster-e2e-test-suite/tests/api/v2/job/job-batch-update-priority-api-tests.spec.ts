/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {randomUUID} from 'crypto';
import {
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {
  cancelProcessInstance,
  createInstances,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {defaultAssertionOptions} from 'utils/constants';
import {validateResponse} from 'json-body-assertions';
import {expectBatchState} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe('Job Batch Update Priority API Tests', () => {
  const runSuffix = randomUUID().slice(0, 8);
  const processId = `processWithJobPriorityForBatchUpdate-${runSuffix}`;
  const batchPriorityJobType = `batchPriorityJobType-${runSuffix}`;
  const state: Record<string, unknown> = {};

  // Dedicated fixture (3 service tasks of one job type with distinct static
  // priorities) so this suite's batch-update deploys/instances can't
  // interfere with the sibling job-priority search/activation coverage that
  // uses its own processWithJobPriority.bpmn.
  test.beforeAll(async () => {
    await deployWithSubstitutions(
      './resources/processWithJobPriorityForBatchUpdate.bpmn',
      {
        processWithJobPriorityForBatchUpdate: processId,
        batchUpdatePriorityJobType: batchPriorityJobType,
      },
    );
    const [processInstance] = await createInstances(processId, 1, 1);
    state['processInstanceKey'] = processInstance.processInstanceKey;
  });

  test.afterAll(async () => {
    await cancelProcessInstance(state['processInstanceKey'] as string);
  });

  test('Batch update by type sets priority on all matching jobs and completes successfully', async ({
    request,
  }) => {
    let jobKeys: string[] = [];
    let batchOperationKey: string;

    await test.step('Confirm 3 pending jobs of batchPriorityJobType exist', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/jobs/search'), {
          headers: jsonHeaders(),
          data: {filter: {type: batchPriorityJobType}},
        });
        await assertStatusCode(res, 200);
        await validateResponse(
          {path: '/jobs/search', method: 'POST', status: '200'},
          res,
        );
        const json = await res.json();
        expect(json.items).toHaveLength(3);
        jobKeys = json.items.map((item: {jobKey: string}) =>
          String(item.jobKey),
        );
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Create batch operation to update priority of jobs matching the type filter', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/jobs/batch-update'), {
          headers: jsonHeaders(),
          data: {
            filter: {type: batchPriorityJobType},
            changeset: {priority: 99},
          },
        });
        await assertStatusCode(res, 200);
        await validateResponse(
          {path: '/jobs/batch-update', method: 'POST', status: '200'},
          res,
        );
        const json = await res.json();
        expect(json.batchOperationType).toBe('UPDATE_JOB');
        expect(json.batchOperationKey).toBeDefined();
        batchOperationKey = json.batchOperationKey;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Poll until the batch operation completes', async () => {
      await expectBatchState(request, batchOperationKey, 'COMPLETED');
    });

    await test.step('Every job of batchPriorityJobType now has priority 99', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/jobs/search'), {
          headers: jsonHeaders(),
          data: {filter: {type: batchPriorityJobType}},
        });
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.items).toHaveLength(3);
        for (const item of json.items) {
          expect(item.priority).toBe(99);
        }
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Batch operation items are all COMPLETED, one per updated job', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/batch-operation-items/search'),
          {
            headers: jsonHeaders(),
            data: {filter: {batchOperationKey}},
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/batch-operation-items/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.items).toHaveLength(3);
        const itemKeys = json.items.map((item: {itemKey: string}) =>
          String(item.itemKey),
        );
        expect(itemKeys.sort()).toEqual([...jobKeys].sort());
        for (const item of json.items) {
          expect(item.operationType).toBe('UPDATE_JOB');
          expect(item.state).toBe('COMPLETED');
        }
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Batch update jobs - unauthorized request returns 401', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/jobs/batch-update'), {
      data: {
        filter: {type: batchPriorityJobType},
        changeset: {priority: 1},
      },
    });
    await assertUnauthorizedRequest(res);
  });
});
