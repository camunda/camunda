/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test, type APIRequestContext} from '@playwright/test';
import {
  cancelProcessInstance,
  createInstances,
  createSingleInstance,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {
  completeUserTask,
  deleteProcessDefinition,
  deployUserTaskProcess,
  drainProcessDefinition,
  expectProcessDefinitionDeleted,
  expectProcessDefinitionPurged,
  expectProcessDefinitionState,
  expectProcessInstanceCount,
  searchProcessDefinitionItems,
  findUserTask,
  RESOURCE_DELETION_ENDPOINT,
  searchProcessInstances,
} from '@requestHelpers';
import {
  DEFAULT_PAGE_LIMIT,
  defaultAssertionOptions,
  uniquePrefixedId,
  extendedAssertionOptions,
} from '../../../../utils/constants';

const CALL_ACTIVITY_CHILD_MODEL_ID = 'childProcess';

async function instanceCountFor(
  request: APIRequestContext,
  processDefinitionId: string,
): Promise<number> {
  return (await searchProcessInstances(request, {processDefinitionId})).length;
}

async function startInstance(
  request: APIRequestContext,
  data: Record<string, unknown>,
) {
  return request.post(buildUrl('/process-instances'), {
    headers: jsonHeaders(),
    data,
  });
}

async function startedInstanceOf(
  request: APIRequestContext,
  data: Record<string, unknown>,
) {
  const res = await startInstance(request, data);
  await assertStatusCode(res, 200);
  await validateResponse(
    {path: '/process-instances', method: 'POST', status: '200'},
    res,
  );
  return res.json();
}

type HistoryBatchOperation = {batchOperationKey: string; state: string};

/**
 * Every history-deletion batch operation currently known. The item names no
 * process definition, so the one a purge creates is only identifiable by
 * diffing against a snapshot taken before the delete.
 */
async function historyBatchOperations(
  request: APIRequestContext,
): Promise<HistoryBatchOperation[]> {
  const res = await request.post(buildUrl('/batch-operations/search'), {
    headers: jsonHeaders(),
    data: {
      filter: {operationType: 'DELETE_PROCESS_INSTANCE'},
      page: {limit: DEFAULT_PAGE_LIMIT},
    },
  });
  await assertStatusCode(res, 200);
  const items: HistoryBatchOperation[] = (await res.json()).items ?? [];
  return items.map((item) => ({
    batchOperationKey: String(item.batchOperationKey),
    state: item.state,
  }));
}

async function partitionsCount(request: APIRequestContext): Promise<number> {
  const res = await request.get(buildUrl('/topology'), {
    headers: jsonHeaders(),
  });
  await assertStatusCode(res, 200);
  return (await res.json()).partitionsCount as number;
}

/**
 * Error messages of the incidents raised on instances of one definition, once
 * `expectedCount` of them exist.
 */
async function incidentMessagesFor(
  request: APIRequestContext,
  processDefinitionKey: string,
  expectedCount: number,
): Promise<string[]> {
  let messages: string[] = [];
  await expect(async () => {
    const res = await request.post(buildUrl('/incidents/search'), {
      headers: jsonHeaders(),
      data: {filter: {processDefinitionKey}, page: {limit: DEFAULT_PAGE_LIMIT}},
    });
    await assertStatusCode(res, 200);
    const items: Array<{errorMessage: string}> = (await res.json()).items ?? [];
    expect(items).toHaveLength(expectedCount);
    messages = items.map((item) => item.errorMessage);
  }).toPass(defaultAssertionOptions);
  return messages;
}

/* eslint-disable playwright/expect-expect */
test.describe('Process Definition Draining Deletion API', () => {
  let instancesToCancel: string[] = [];

  test.beforeEach(() => {
    instancesToCancel = [];
  });

  // A definition left draining blocks instance creation for any later test that
  // reuses it, so every instance started here has to be terminated.
  test.afterEach(async () => {
    for (const processInstanceKey of instancesToCancel.filter(Boolean)) {
      await cancelProcessInstance(processInstanceKey);
    }
  });

  test('Deleting a definition with a running instance drains it until the instance completes', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-complete');
    const {processDefinitionKey} =
      await deployUserTaskProcess(processDefinitionId);
    const instance = await createSingleInstance(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);

    const userTaskKey = await findUserTask(
      request,
      instance.processInstanceKey,
      'CREATED',
    );

    const deleteRes = await deleteProcessDefinition(
      request,
      processDefinitionKey,
    );
    await assertStatusCode(deleteRes, 200);
    await validateResponse(
      {path: RESOURCE_DELETION_ENDPOINT, method: 'POST', status: '200'},
      deleteRes,
    );

    await expectProcessDefinitionState(
      request,
      processDefinitionKey,
      'DRAINING',
    );

    await assertStatusCode(await completeUserTask(request, userTaskKey), 204);

    await expectProcessDefinitionDeleted(request, processDefinitionKey);
  });

  test('Cancelling the last running instance finalizes the drain', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-cancel');
    const {processDefinitionKey} =
      await deployUserTaskProcess(processDefinitionId);
    const instance = await createSingleInstance(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);

    await findUserTask(request, instance.processInstanceKey, 'CREATED');
    await drainProcessDefinition(request, processDefinitionKey);

    await cancelProcessInstance(instance.processInstanceKey);

    await expectProcessDefinitionDeleted(request, processDefinitionKey);
  });

  test('A definition stays DRAINING until the last of several instances ends', async ({
    request,
  }) => {
    // Only the deployment partition flips secondary storage to DELETED, and only
    // once every partition has reported. Ending the instances one at a time is
    // what separates that from a definition deleted as soon as any partition
    // finishes.
    const processDefinitionId = uniquePrefixedId('draining-partial');
    const {processDefinitionKey} =
      await deployUserTaskProcess(processDefinitionId);

    const instances = await createInstances(processDefinitionId, 1, 6);
    instancesToCancel.push(
      ...instances.map((instance) => instance.processInstanceKey),
    );
    await expectProcessInstanceCount(request, {processDefinitionId}, 6);

    await drainProcessDefinition(request, processDefinitionKey);

    for (let ended = 1; ended < instances.length; ended++) {
      await cancelProcessInstance(instances[ended - 1]!.processInstanceKey);
      await expectProcessInstanceCount(
        request,
        {processDefinitionId, state: 'ACTIVE'},
        instances.length - ended,
      );
      await expectProcessDefinitionState(
        request,
        processDefinitionKey,
        'DRAINING',
      );
    }

    await cancelProcessInstance(
      instances[instances.length - 1]!.processInstanceKey,
    );
    await expectProcessDefinitionDeleted(request, processDefinitionKey);
  });

  test('A call activity child definition drains until its child instance is terminated', async ({
    request,
  }) => {
    const parentId = uniquePrefixedId('draining-parent');
    const childId = uniquePrefixedId('draining-child');

    const childDeployment = await deployWithSubstitutions(
      './resources/childProcess_v_1.bpmn',
      {
        [CALL_ACTIVITY_CHILD_MODEL_ID]: childId,
        // The model's job type is the generic `Task`; scoping it to this test
        // keeps an unrelated worker from completing the child and ending the
        // drain before it is asserted.
        'type="Task"': `type="${childId}-job"`,
      },
    );
    const childKey = childDeployment.processes[0].processDefinitionKey;

    const parentDeployment = await deployWithSubstitutions(
      './resources/callActivityParentProcess.bpmn',
      {
        callActivityParentProcess: parentId,
        [CALL_ACTIVITY_CHILD_MODEL_ID]: childId,
      },
    );
    const parentKey = parentDeployment.processes[0].processDefinitionKey;

    const parentInstance = await createSingleInstance(parentId, 1);
    instancesToCancel.push(parentInstance.processInstanceKey);

    await expectProcessInstanceCount(
      request,
      {processDefinitionKey: childKey, state: 'ACTIVE'},
      1,
    );

    await drainProcessDefinition(request, childKey);
    await expectProcessDefinitionState(request, parentKey, 'ACTIVE');

    await cancelProcessInstance(parentInstance.processInstanceKey);

    await expectProcessDefinitionDeleted(request, childKey);
  });

  test('Deleting a definition with no running instances is deleted without draining', async ({
    request,
  }) => {
    // Nothing to drain, so the definition skips DRAINING entirely. Worth its
    // own test because the engine finalizing correctly is not enough — a
    // secondary storage left stuck at DRAINING is what camunda#60472 was.
    const processDefinitionId = uniquePrefixedId('draining-none');
    const {processDefinitionKey} =
      await deployUserTaskProcess(processDefinitionId);

    await assertStatusCode(
      await deleteProcessDefinition(request, processDefinitionKey),
      200,
    );

    await expectProcessDefinitionDeleted(request, processDefinitionKey);

    expect(
      await searchProcessDefinitionItems(request, {
        filter: {processDefinitionId, state: 'DRAINING'},
      }),
    ).toHaveLength(0);
  });

  test('A call activity raises an incident when the child definition is draining', async ({
    request,
  }) => {
    const parentId = uniquePrefixedId('draining-callincident-parent');
    const childId = uniquePrefixedId('draining-callincident-child');

    const childDeployment = await deployWithSubstitutions(
      './resources/childProcess_v_1.bpmn',
      {
        [CALL_ACTIVITY_CHILD_MODEL_ID]: childId,
        'type="Task"': `type="${childId}-job"`,
      },
    );
    const child = childDeployment.processes[0];

    const parentDeployment = await deployWithSubstitutions(
      './resources/callActivityParentProcess.bpmn',
      {
        callActivityParentProcess: parentId,
        [CALL_ACTIVITY_CHILD_MODEL_ID]: childId,
      },
    );
    const parent = parentDeployment.processes[0];

    // The first parent instance keeps a child instance alive, so the child
    // definition stays DRAINING instead of finalizing immediately.
    const firstParent = await createSingleInstance(parentId, 1);
    instancesToCancel.push(firstParent.processInstanceKey);
    await expectProcessInstanceCount(
      request,
      {processDefinitionKey: child.processDefinitionKey, state: 'ACTIVE'},
      1,
    );

    await drainProcessDefinition(request, child.processDefinitionKey);

    // The parent is untouched, so starting it still succeeds — it is the call
    // activity that cannot resolve the draining child, and that failure has to
    // surface as an incident rather than silently skipping the child.
    //
    // Which message the incident carries depends on the serving partition:
    // only the partition holding the child instance is still draining, the
    // others finalized the delete at once and no longer hold the definition.
    // Three instances per partition exercise both.
    const parentCount = 3 * (await partitionsCount(request));
    const parents = await createInstances(parentId, 1, parentCount);
    instancesToCancel.push(
      ...parents.map((instance) => instance.processInstanceKey),
    );

    const messages = await incidentMessagesFor(
      request,
      parent.processDefinitionKey,
      parentCount,
    );
    const drainingMessage =
      `Expected to call process with BPMN process id '${childId}' and version ` +
      `${child.processDefinitionVersion} (key ${child.processDefinitionKey}), ` +
      'but it is being deleted.';
    const alreadyDeletedMessage =
      `Expected process with BPMN process id '${childId}' to be deployed, ` +
      'but not found.';

    expect(messages).toContain(drainingMessage);
    expect(
      messages.filter(
        (message) =>
          message !== drainingMessage && message !== alreadyDeletedMessage,
      ),
    ).toEqual([]);
  });

  test('A new version can be deployed while an older one is draining', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-newversion');
    const v1 = await deployUserTaskProcess(processDefinitionId);
    const v1Instance = await createSingleInstance(processDefinitionId, 1);
    instancesToCancel.push(v1Instance.processInstanceKey);
    await findUserTask(request, v1Instance.processInstanceKey, 'CREATED');

    await drainProcessDefinition(request, v1.processDefinitionKey);

    // Deploying during the drain, not before it: the draining version must not
    // block the deployment, and must not be resurrected as ACTIVE by it.
    const v2 = await deployUserTaskProcess(processDefinitionId, '-v2');
    expect(v2.processDefinitionVersion).toBeGreaterThan(
      v1.processDefinitionVersion,
    );

    await expectProcessDefinitionState(
      request,
      v2.processDefinitionKey,
      'ACTIVE',
    );
    await expectProcessDefinitionState(
      request,
      v1.processDefinitionKey,
      'DRAINING',
    );

    const started = await startedInstanceOf(request, {processDefinitionId});
    instancesToCancel.push(started.processInstanceKey);
    expect(started.processDefinitionKey).toBe(v2.processDefinitionKey);
  });

  test('Creating an instance by processDefinitionKey is refused while the definition is draining', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-blocked');
    const {processDefinitionKey} =
      await deployUserTaskProcess(processDefinitionId);
    const instance = await createSingleInstance(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);

    await drainProcessDefinition(request, processDefinitionKey);

    const res = await startInstance(request, {processDefinitionKey});

    // Deletion finalizes per partition, so on a multi-partition cluster the
    // refusal depends on which partition serves the create: one still draining
    // answers 409, one that already finished deleting answers 404. Neither
    // starts an instance, which is the guarantee under test.
    expect([404, 409]).toContain(res.status());
    await expectProcessInstanceCount(request, {processDefinitionId}, 1);
  });

  test('Creating by processDefinitionId alone starts on a newer ACTIVE version instead of the draining one', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-newer');
    const v1 = await deployUserTaskProcess(processDefinitionId);
    const v2 = await deployUserTaskProcess(processDefinitionId, '-v2');
    expect(v2.processDefinitionVersion).toBeGreaterThan(
      v1.processDefinitionVersion,
    );

    const v1Instance = await createSingleInstance(
      processDefinitionId,
      v1.processDefinitionVersion,
    );
    instancesToCancel.push(v1Instance.processInstanceKey);

    await drainProcessDefinition(request, v1.processDefinitionKey);

    const started = await startedInstanceOf(request, {processDefinitionId});
    instancesToCancel.push(started.processInstanceKey);

    expect(started.processDefinitionKey).toBe(v2.processDefinitionKey);
  });

  test('Creating by processDefinitionId alone resolves to the latest ACTIVE version below the draining one', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-older');
    const v1 = await deployUserTaskProcess(processDefinitionId);
    const v2 = await deployUserTaskProcess(processDefinitionId, '-v2');

    const v2Instance = await createSingleInstance(
      processDefinitionId,
      v2.processDefinitionVersion,
    );
    instancesToCancel.push(v2Instance.processInstanceKey);

    await drainProcessDefinition(request, v2.processDefinitionKey);

    const started = await startedInstanceOf(request, {processDefinitionId});
    instancesToCancel.push(started.processInstanceKey);

    // Resolution skips the draining version and lands on the latest ACTIVE one
    // below it, regardless of which partition serves the request (camunda#61719).
    expect(started.processDefinitionKey).toBe(v1.processDefinitionKey);
    expect(started.processDefinitionKey).not.toBe(v2.processDefinitionKey);
  });

  test('Creating by processDefinitionId skips consecutive draining versions', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-chain');
    const v1 = await deployUserTaskProcess(processDefinitionId);
    const v2 = await deployUserTaskProcess(processDefinitionId, '-v2');
    const v3 = await deployUserTaskProcess(processDefinitionId, '-v3');

    for (const version of [v3, v2]) {
      const instance = await createSingleInstance(
        processDefinitionId,
        version.processDefinitionVersion,
      );
      instancesToCancel.push(instance.processInstanceKey);
      await findUserTask(request, instance.processInstanceKey, 'CREATED');
      await drainProcessDefinition(request, version.processDefinitionKey);
    }

    const started = await startedInstanceOf(request, {processDefinitionId});
    instancesToCancel.push(started.processInstanceKey);

    expect(started.processDefinitionKey).toBe(v1.processDefinitionKey);
  });

  test('Creating by processDefinitionId is refused when every version is draining', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-all');
    const v1 = await deployUserTaskProcess(processDefinitionId);
    const v2 = await deployUserTaskProcess(processDefinitionId, '-v2');

    for (const version of [v1, v2]) {
      const instance = await createSingleInstance(
        processDefinitionId,
        version.processDefinitionVersion,
      );
      instancesToCancel.push(instance.processInstanceKey);
      await findUserTask(request, instance.processInstanceKey, 'CREATED');
      await drainProcessDefinition(request, version.processDefinitionKey);
    }

    const before = await instanceCountFor(request, processDefinitionId);
    const res = await startInstance(request, {processDefinitionId});

    expect([404, 409]).toContain(res.status());
    expect(await instanceCountFor(request, processDefinitionId)).toBe(before);
  });

  test('A repeated delete of an already draining definition is rejected', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-repeat');
    const {processDefinitionKey} =
      await deployUserTaskProcess(processDefinitionId);
    const instance = await createSingleInstance(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);
    await findUserTask(request, instance.processInstanceKey, 'CREATED');

    await drainProcessDefinition(request, processDefinitionKey);

    const second = await deleteProcessDefinition(request, processDefinitionKey);
    expect(second.status()).toBe(409);
    await expectProcessDefinitionState(
      request,
      processDefinitionKey,
      'DRAINING',
    );
  });

  test('Without deleteHistory the instance history survives the deletion', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-keephistory');
    const {processDefinitionKey} =
      await deployUserTaskProcess(processDefinitionId);
    const instance = await createSingleInstance(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);

    const deletion = await deleteProcessDefinition(
      request,
      processDefinitionKey,
    );
    await assertStatusCode(deletion, 200);
    // A process definition still in runtime state reports no batch operation,
    // whether or not history deletion was requested.
    expect((await deletion.json()).batchOperation).toBeNull();

    // Cancelling keeps the test off the user-task index, the slowest propagation
    // path.
    await cancelProcessInstance(instance.processInstanceKey);
    await expectProcessDefinitionDeleted(request, processDefinitionKey);

    expect(await instanceCountFor(request, processDefinitionId)).toBe(1);
  });

  test('With deleteHistory the instance history is purged by a batch operation once the drain finishes', async ({
    request,
  }) => {
    const processDefinitionId = uniquePrefixedId('draining-purgehistory');
    const {processDefinitionKey} =
      await deployUserTaskProcess(processDefinitionId);
    const instance = await createSingleInstance(processDefinitionId, 1);
    instancesToCancel.push(instance.processInstanceKey);

    const batchOperationKeysBefore = new Set(
      (await historyBatchOperations(request)).map(
        (item) => item.batchOperationKey,
      ),
    );

    const deletion = await deleteProcessDefinition(
      request,
      processDefinitionKey,
      true,
    );
    await assertStatusCode(deletion, 200);
    expect((await deletion.json()).batchOperation).toBeNull();

    await expectProcessDefinitionState(
      request,
      processDefinitionKey,
      'DRAINING',
    );
    // History is retained for as long as the definition is draining.
    expect(await instanceCountFor(request, processDefinitionId)).toBe(1);

    await cancelProcessInstance(instance.processInstanceKey);

    // The purge takes the definition record with it, so there is nothing left to
    // read back.
    await expectProcessDefinitionPurged(request, processDefinitionKey);

    await expectProcessInstanceCount(
      request,
      {processDefinitionId},
      0,
      extendedAssertionOptions,
    );

    // The purge runs as a batch operation, and it has to reach COMPLETED —
    // otherwise history lingers with nothing left to retry. Only the snapshot
    // diff identifies it, so "created exactly once" stays a manual check.
    await expect(async () => {
      const created = (await historyBatchOperations(request)).filter(
        (item) => !batchOperationKeysBefore.has(item.batchOperationKey),
      );
      expect(created.length).toBeGreaterThan(0);
      expect(created.map((item) => item.state)).toContain('COMPLETED');
    }).toPass(extendedAssertionOptions);
  });

  test('Deleting a definition with running instances leaves the deployment queue usable', async ({
    request,
  }) => {
    // The bug this feature fixes: the deletion used to stall on a partition that
    // still held instances, and because deletions and deployments share one
    // distribution queue, every later deployment was blocked cluster-wide.
    const processDefinitionId = uniquePrefixedId('draining-queue');
    const {processDefinitionKey} =
      await deployUserTaskProcess(processDefinitionId);

    const spread = await createInstances(processDefinitionId, 1, 6);
    instancesToCancel.push(
      ...spread.map((instance) => instance.processInstanceKey),
    );
    await expectProcessInstanceCount(request, {processDefinitionId}, 6);

    await drainProcessDefinition(request, processDefinitionKey);

    const probeProcessDefinitionId = uniquePrefixedId('draining-queue-probe');
    const probe = await deployUserTaskProcess(probeProcessDefinitionId);
    expect(probe.processDefinitionKey).toBeDefined();

    // Instances land round-robin, so three per partition make it certain every
    // partition served one and can resolve the new definition.
    const probeInstanceCount = 3 * (await partitionsCount(request));
    const probeInstances = await createInstances(
      probeProcessDefinitionId,
      1,
      probeInstanceCount,
    );
    instancesToCancel.push(
      ...probeInstances.map((instance) => instance.processInstanceKey),
    );
    await expectProcessInstanceCount(
      request,
      {processDefinitionId: probeProcessDefinitionId},
      probeInstanceCount,
    );

    // And the original definition is still draining, not silently dropped.
    await expectProcessDefinitionState(
      request,
      processDefinitionKey,
      'DRAINING',
    );
  });
});
