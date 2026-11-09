/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, expect, test} from '@playwright/test';
import type {JSONDoc} from '@camunda8/sdk/dist/zeebe/types.js';
import {
  activateJobsByType,
  activateSingleJob,
  completeJob,
  completeUserTask,
  createInstanceOnceDeployed,
  deployUserTaskProcess,
  expectBatchState,
  expectProcessInstanceCount,
  expectProcessState,
  expectUserTaskAssigned,
  findUserTask,
  getProcessInstance,
  resumeProcessInstance,
  suspendAndExpectSuspended,
  suspendProcessInstance,
} from '@requestHelpers';
import {
  extendedAssertionOptions,
  uniquePrefixedId,
} from '../../../../utils/constants';
import {
  assertBadRequest,
  assertInvalidState,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {
  cancelProcessInstance,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';

interface UserTask {
  userTaskKey: string;
  processInstanceKey: string;
  elementId: string;
  state: string;
  assignee: string | null;
  isSuspended: boolean;
}

const instancesToCancel: string[] = [];
const cancelingListeners = new Map<string, string>();

async function createTrackedInstance(
  processDefinitionId: string,
  variables?: JSONDoc,
) {
  const instance = await createInstanceOnceDeployed(
    processDefinitionId,
    1,
    variables,
  );
  instancesToCancel.push(instance.processInstanceKey);
  return instance.processInstanceKey;
}

async function startUserTasks(
  request: APIRequestContext,
  prefix: string,
  count = 1,
) {
  const processDefinitionId = uniquePrefixedId(prefix);
  await deployUserTaskProcess(processDefinitionId);
  const processInstanceKeys: string[] = [];
  for (let i = 0; i < count; i++) {
    const processInstanceKey = await createTrackedInstance(processDefinitionId);
    processInstanceKeys.push(processInstanceKey);
  }
  const userTaskKeys = await Promise.all(
    processInstanceKeys.map((processInstanceKey) =>
      findUserTask(
        request,
        processInstanceKey,
        'CREATED',
        undefined,
        extendedAssertionOptions,
      ),
    ),
  );
  await expectTasks(request, {processDefinitionId}, userTaskKeys, false);
  return {processDefinitionId, processInstanceKeys, userTaskKeys};
}

async function searchUserTasks(
  request: APIRequestContext,
  filter: Record<string, unknown>,
) {
  const response = await request.post(buildUrl('/user-tasks/search'), {
    headers: jsonHeaders(),
    data: {filter, page: {limit: 1000}},
  });
  await assertStatusCode(response, 200);
  const body: {items: UserTask[]; page: {totalItems: number}} =
    await response.json();
  return {items: body.items, totalItems: body.page.totalItems};
}

async function expectTasks(
  request: APIRequestContext,
  filter: Record<string, unknown>,
  expected: number | string[],
  isSuspended?: boolean,
): Promise<UserTask[]> {
  let tasks: UserTask[] = [];
  await expect(async () => {
    const {items, totalItems} = await searchUserTasks(request, filter);
    const count = typeof expected === 'number' ? expected : expected.length;
    expect(totalItems).toBe(count);
    expect(items).toHaveLength(count);
    if (Array.isArray(expected)) {
      expect(items.map((task) => task.userTaskKey).sort()).toEqual(
        [...expected].sort(),
      );
    }
    if (isSuspended !== undefined) {
      for (const task of items) {
        expect(task.isSuspended).toBe(isSuspended);
      }
    }
    tasks = items;
  }).toPass(extendedAssertionOptions);
  return tasks;
}

async function expectTaskSuspended(
  request: APIRequestContext,
  userTaskKey: string,
  expected: boolean,
) {
  const [task] = await expectTasks(
    request,
    {userTaskKey},
    [userTaskKey],
    expected,
  );
  return task;
}

function assignTask(request: APIRequestContext, userTaskKey: string) {
  return request.post(
    buildUrl('/user-tasks/{userTaskKey}/assignment', {userTaskKey}),
    {headers: jsonHeaders(), data: {assignee: 'demo'}},
  );
}

async function deployModel(
  modelId: string,
  substitutions: Record<string, string> = {},
) {
  const processDefinitionId = uniquePrefixedId(modelId);
  await deployWithSubstitutions(`./resources/${modelId}.bpmn`, {
    [modelId]: processDefinitionId,
    ...substitutions,
  });
  return processDefinitionId;
}

async function runBatch(
  request: APIRequestContext,
  path: '/process-instances/suspension' | '/process-instances/resumption',
  processDefinitionId: string,
  sourceState: string,
  targetState: string,
  count: number,
) {
  await expectProcessInstanceCount(
    request,
    {processDefinitionId, state: sourceState},
    count,
    extendedAssertionOptions,
  );
  const response = await request.post(buildUrl(path), {
    headers: jsonHeaders(),
    data: {filter: {processDefinitionId}},
  });
  await assertStatusCode(response, 200);
  const {batchOperationKey} = await response.json();
  await expectBatchState(request, batchOperationKey, 'COMPLETED');
  await expect(async () => {
    const result = await request.get(
      buildUrl('/batch-operations/{batchOperationKey}', {batchOperationKey}),
      {headers: jsonHeaders()},
    );
    await assertStatusCode(result, 200);
    const batch = await result.json();
    expect(batch.operationsTotalCount).toBe(count);
    expect(batch.operationsCompletedCount).toBe(count);
    expect(batch.operationsFailedCount).toBe(0);
  }).toPass(extendedAssertionOptions);
  await expectProcessInstanceCount(
    request,
    {processDefinitionId, state: targetState},
    count,
    extendedAssertionOptions,
  );
}

test.describe('User Task Suspension API', () => {
  test.afterAll(async ({request}) => {
    await Promise.all(
      instancesToCancel.map((key) => cancelProcessInstance(key)),
    );
    // A failed listener test must not leave cancellation waiting on its job.
    for (const [processInstanceKey, jobType] of cancelingListeners) {
      await expect(async () => {
        const instance = await getProcessInstance(request, processInstanceKey);
        if (instance.state === 'TERMINATED') {
          return;
        }
        const jobs = await activateJobsByType(
          request,
          jobType,
          processInstanceKey,
          [],
          1,
          1000,
        );
        for (const job of jobs) {
          await completeJob(request, job.jobKey);
        }
        expect(
          (await getProcessInstance(request, processInstanceKey)).state,
        ).toBe('TERMINATED');
      }).toPass(extendedAssertionOptions);
    }
    instancesToCancel.length = 0;
    cancelingListeners.clear();
  });

  test('The isSuspended filter splits suspended and active tasks', async ({
    request,
  }) => {
    const {processDefinitionId, processInstanceKeys, userTaskKeys} =
      await startUserTasks(request, 'ut-susp-filter', 2);
    await suspendAndExpectSuspended(request, processInstanceKeys[0]);
    await expectTaskSuspended(request, userTaskKeys[0], true);

    await expectTasks(
      request,
      {processDefinitionId, isSuspended: true},
      [userTaskKeys[0]],
      true,
    );
    await expectTasks(
      request,
      {processDefinitionId, isSuspended: false},
      [userTaskKeys[1]],
      false,
    );
    const allTasks = await expectTasks(
      request,
      {processDefinitionId},
      userTaskKeys,
    );
    expect(allTasks.map((task) => task.isSuspended).sort()).toEqual([
      false,
      true,
    ]);
  });

  test('The suspension filter combines with state, assignee and process filters', async ({
    request,
  }) => {
    const {processDefinitionId, processInstanceKeys, userTaskKeys} =
      await startUserTasks(request, 'ut-susp-intersection', 3);
    for (const userTaskKey of userTaskKeys.slice(0, 2)) {
      await assertStatusCode(await assignTask(request, userTaskKey), 204);
      await expectUserTaskAssigned(request, userTaskKey, 'demo');
    }
    for (const index of [0, 2]) {
      await suspendAndExpectSuspended(request, processInstanceKeys[index]);
      await expectTaskSuspended(request, userTaskKeys[index], true);
    }

    const [assignedTask] = await expectTasks(
      request,
      {processDefinitionId, isSuspended: true, assignee: 'demo'},
      [userTaskKeys[0]],
      true,
    );
    expect(assignedTask.assignee).toBe('demo');
    await expectTasks(
      request,
      {processDefinitionId, isSuspended: false, state: 'CREATED'},
      [userTaskKeys[1]],
      false,
    );
    await expectTasks(
      request,
      {processDefinitionId, isSuspended: false, state: 'COMPLETED'},
      0,
    );
    await expectTasks(
      request,
      {processDefinitionId, isSuspended: true},
      [userTaskKeys[0], userTaskKeys[2]],
      true,
    );
  });

  test('Batch suspend and resume mark and clear every task', async ({
    request,
  }) => {
    const count = 10;
    const {processDefinitionId, userTaskKeys} = await startUserTasks(
      request,
      'ut-susp-batch',
      count,
    );
    await runBatch(
      request,
      '/process-instances/suspension',
      processDefinitionId,
      'ACTIVE',
      'SUSPENDED',
      count,
    );
    await expectTasks(
      request,
      {processDefinitionId, isSuspended: true},
      userTaskKeys,
      true,
    );

    await runBatch(
      request,
      '/process-instances/resumption',
      processDefinitionId,
      'SUSPENDED',
      'ACTIVE',
      count,
    );
    const resumed = await expectTasks(
      request,
      {processDefinitionId},
      userTaskKeys,
      false,
    );
    expect(resumed.map((task) => task.state)).toEqual(
      Array(count).fill('CREATED'),
    );
    await expectTasks(request, {processDefinitionId, isSuspended: true}, 0);
  });

  test('A second suspension marks the task again', async ({request}) => {
    const {processInstanceKeys, userTaskKeys} = await startUserTasks(
      request,
      'ut-susp-cycle',
    );
    for (let cycle = 0; cycle < 2; cycle++) {
      await suspendAndExpectSuspended(request, processInstanceKeys[0]);
      await expectTaskSuspended(request, userTaskKeys[0], true);
      await assertStatusCode(
        await resumeProcessInstance(request, processInstanceKeys[0]),
        204,
      );
      const resumed = await expectTaskSuspended(
        request,
        userTaskKeys[0],
        false,
      );
      expect(resumed.state).toBe('CREATED');
    }
  });

  test('Non-boolean suspension filters are rejected and null leaves tasks unfiltered', async ({
    request,
  }) => {
    const {processDefinitionId, processInstanceKeys, userTaskKeys} =
      await startUserTasks(request, 'ut-susp-invalid', 2);
    await suspendAndExpectSuspended(request, processInstanceKeys[0]);
    await expectTaskSuspended(request, userTaskKeys[0], true);
    for (const isSuspended of ['true', 'yes', 1, {}]) {
      await test.step(`Reject isSuspended=${JSON.stringify(isSuspended)}`, async () => {
        const response = await request.post(buildUrl('/user-tasks/search'), {
          headers: jsonHeaders(),
          data: {filter: {processDefinitionId, isSuspended}},
        });
        await assertBadRequest(response, /isSuspended/);
      });
    }
    const unfiltered = await expectTasks(
      request,
      {processDefinitionId, isSuspended: null},
      userTaskKeys,
    );
    expect(unfiltered.map((task) => task.isSuspended).sort()).toEqual([
      false,
      true,
    ]);
  });

  test('Sorting user tasks by isSuspended is rejected', async ({request}) => {
    const response = await request.post(buildUrl('/user-tasks/search'), {
      headers: jsonHeaders(),
      data: {sort: [{field: 'isSuspended', order: 'ASC'}]},
    });
    await assertStatusCode(response, 400);
    expect(await response.json()).toMatchObject({
      title: 'Bad Request',
      detail: expect.stringContaining('isSuspended'),
    });
  });

  test('Finished tasks never report suspended', async ({request}) => {
    const {processDefinitionId, processInstanceKeys, userTaskKeys} =
      await startUserTasks(request, 'ut-susp-finished', 2);
    await assertStatusCode(
      await completeUserTask(request, userTaskKeys[0]),
      204,
    );
    await suspendAndExpectSuspended(request, processInstanceKeys[1]);
    await expectTaskSuspended(request, userTaskKeys[1], true);
    await cancelProcessInstance(processInstanceKeys[1], {
      ignoreNotFound: false,
    });

    for (const [state, userTaskKey] of [
      ['COMPLETED', userTaskKeys[0]],
      ['CANCELED', userTaskKeys[1]],
    ]) {
      const [terminalTask] = await expectTasks(
        request,
        {processDefinitionId, state},
        [userTaskKey],
        false,
      );
      expect(terminalTask.state).toBe(state);
      await expectTasks(
        request,
        {processDefinitionId, state, isSuspended: true},
        0,
      );
    }
  });

  test('Task commands are rejected immediately after suspension is requested', async ({
    request,
  }) => {
    const count = 200;
    const processDefinitionId = await deployModel(
      'user_task_suspension_multi_instance',
    );
    const processInstanceKey = await createTrackedInstance(
      processDefinitionId,
      {items: Array.from({length: count}, (_, index) => index + 1)},
    );
    const before = await expectTasks(
      request,
      {processInstanceKey, state: 'CREATED'},
      count,
      false,
    );

    const suspension = await suspendProcessInstance(
      request,
      processInstanceKey,
    );
    // No exporter read may delay these commands until all tasks are marked.
    const [completion, assignment] = await Promise.all([
      completeUserTask(request, before[0].userTaskKey),
      assignTask(request, before[1].userTaskKey),
    ]);
    await assertStatusCode(suspension, 204);
    await assertInvalidState(completion);
    await assertInvalidState(assignment);

    const after = await expectTasks(
      request,
      {processInstanceKey},
      before.map((task) => task.userTaskKey),
      true,
    );
    for (const task of before.slice(0, 2)) {
      expect(after).toContainEqual(
        expect.objectContaining({
          userTaskKey: task.userTaskKey,
          state: task.state,
          assignee: task.assignee,
          isSuspended: true,
        }),
      );
    }
  });

  test('Every multi-instance and parallel task is marked and cleared', async ({
    request,
  }) => {
    const processDefinitionId = await deployModel(
      'user_task_suspension_parallel',
    );
    const processInstanceKey = await createTrackedInstance(processDefinitionId);
    const tasks = await expectTasks(request, {processInstanceKey}, 4, false);
    expect(
      tasks.filter((task) => task.elementId === 'multi_task'),
    ).toHaveLength(3);
    expect(
      tasks.filter((task) => task.elementId === 'single_task'),
    ).toHaveLength(1);
    const keys = tasks.map((task) => task.userTaskKey);

    await suspendAndExpectSuspended(request, processInstanceKey);
    await expectTasks(request, {processInstanceKey}, keys, true);
    await assertStatusCode(
      await resumeProcessInstance(request, processInstanceKey),
      204,
    );
    await expectTasks(request, {processInstanceKey}, keys, false);
    await expectTasks(request, {processInstanceKey, isSuspended: true}, 0);
  });

  test('Tasks in embedded and non-interrupting event subprocesses are marked', async ({
    request,
  }) => {
    const messageName = uniquePrefixedId('ut-susp-message');
    const correlationKey = uniquePrefixedId('ut-susp-correlation');
    const processDefinitionId = await deployModel(
      'user_task_suspension_subprocesses',
      {'name="suspension-review-message"': `name="${messageName}"`},
    );
    const processInstanceKey = await createTrackedInstance(
      processDefinitionId,
      {correlationKey},
    );
    await expectTasks(request, {processInstanceKey}, 1, false);
    const publication = await request.post(buildUrl('/messages/publication'), {
      headers: jsonHeaders(),
      data: {name: messageName, correlationKey, timeToLive: 60_000},
    });
    await assertStatusCode(publication, 200);
    const tasks = await expectTasks(request, {processInstanceKey}, 2, false);
    expect(tasks.map((task) => task.elementId).sort()).toEqual([
      'embedded_task',
      'message_task',
    ]);
    const keys = tasks.map((task) => task.userTaskKey);

    await suspendAndExpectSuspended(request, processInstanceKey);
    await expectTasks(request, {processInstanceKey}, keys, true);
    await assertStatusCode(
      await resumeProcessInstance(request, processInstanceKey),
      204,
    );
    await expectTasks(request, {processInstanceKey}, keys, false);
  });

  test('A called child task stays active when its parent is suspended', async ({
    request,
  }) => {
    const childId = uniquePrefixedId('ut-susp-child');
    await deployUserTaskProcess(childId);
    const parentId = await deployModel('call_user_task_process_process', {
      'processId="process_with_task_listener"': `processId="${childId}"`,
    });
    const parentKey = await createTrackedInstance(parentId);
    const [childTask] = await expectTasks(
      request,
      {processDefinitionId: childId},
      1,
      false,
    );
    instancesToCancel.push(childTask.processInstanceKey);
    await suspendAndExpectSuspended(request, parentKey);

    await expectTaskSuspended(request, childTask.userTaskKey, false);
    await assertStatusCode(
      await completeUserTask(request, childTask.userTaskKey),
      204,
    );
    await expectProcessState(
      request,
      childTask.processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectProcessState(
      request,
      parentKey,
      'SUSPENDED',
      extendedAssertionOptions,
    );
    await assertStatusCode(
      await resumeProcessInstance(request, parentKey),
      204,
    );
    await expectProcessState(
      request,
      parentKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
  });

  test('Resuming immediately after suspension leaves no task marked', async ({
    request,
  }) => {
    const processDefinitionId = await deployModel(
      'user_task_suspension_parallel',
    );
    const processInstanceKey = await createTrackedInstance(processDefinitionId);
    const tasks = await expectTasks(request, {processInstanceKey}, 4, false);

    await assertStatusCode(
      await suspendProcessInstance(request, processInstanceKey),
      204,
    );
    await assertStatusCode(
      await resumeProcessInstance(request, processInstanceKey),
      204,
    );
    await expectProcessState(
      request,
      processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
    await expectTasks(
      request,
      {processInstanceKey},
      tasks.map((task) => task.userTaskKey),
      false,
    );
    for (const task of tasks) {
      await assertStatusCode(
        await completeUserTask(request, task.userTaskKey),
        204,
      );
    }
    await expectProcessState(
      request,
      processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectTasks(request, {processInstanceKey}, 4, false);
  });

  test('Cancelling a suspended instance runs its canceling task listener', async ({
    request,
  }) => {
    const jobType = uniquePrefixedId('ut-susp-canceling');
    const processDefinitionId = await deployModel(
      'process_with_task_listener',
      {
        'eventType="completing"': 'eventType="canceling"',
        'retries="0"': 'retries="3"',
        'type="blocker"': `type="${jobType}"`,
      },
    );
    const processInstanceKey = await createTrackedInstance(processDefinitionId);
    cancelingListeners.set(processInstanceKey, jobType);
    const userTaskKey = await findUserTask(
      request,
      processInstanceKey,
      'CREATED',
      undefined,
      extendedAssertionOptions,
    );
    await suspendAndExpectSuspended(request, processInstanceKey);
    await expectTaskSuspended(request, userTaskKey, true);

    await cancelProcessInstance(processInstanceKey, {ignoreNotFound: false});
    const jobKey = await activateSingleJob(
      request,
      jobType,
      processInstanceKey,
    );
    await completeJob(request, jobKey);

    await expectProcessState(
      request,
      processInstanceKey,
      'TERMINATED',
      extendedAssertionOptions,
    );
    await expectTasks(
      request,
      {processInstanceKey, state: 'CANCELED'},
      [userTaskKey],
      false,
    );
  });

  test('Sequential multi-instance tasks advance only after resumption', async ({
    request,
  }) => {
    const processDefinitionId = await deployModel(
      'user_task_suspension_multi_instance',
      {
        'isSequential="false"': 'isSequential="true"',
        'inputCollection="=items"': 'inputCollection="=[1,2,3]"',
      },
    );
    const processInstanceKey = await createTrackedInstance(processDefinitionId);
    const [first] = await expectTasks(
      request,
      {processInstanceKey, state: 'CREATED'},
      1,
      false,
    );
    await suspendAndExpectSuspended(request, processInstanceKey);
    await expectTaskSuspended(request, first.userTaskKey, true);
    await assertInvalidState(
      await completeUserTask(request, first.userTaskKey),
    );
    await expectTasks(request, {processInstanceKey}, [first.userTaskKey], true);

    await assertStatusCode(
      await resumeProcessInstance(request, processInstanceKey),
      204,
    );
    await expectTaskSuspended(request, first.userTaskKey, false);
    await assertStatusCode(
      await completeUserTask(request, first.userTaskKey),
      204,
    );
    await expectTasks(
      request,
      {userTaskKey: first.userTaskKey, state: 'COMPLETED'},
      [first.userTaskKey],
      false,
    );
    const [second] = await expectTasks(
      request,
      {processInstanceKey, state: 'CREATED'},
      1,
      false,
    );
    expect(second.userTaskKey).not.toBe(first.userTaskKey);
    await expectTasks(request, {processInstanceKey}, 2, false);
    await suspendAndExpectSuspended(request, processInstanceKey);
    await expectTaskSuspended(request, second.userTaskKey, true);
    await expectTaskSuspended(request, first.userTaskKey, false);

    await assertStatusCode(
      await resumeProcessInstance(request, processInstanceKey),
      204,
    );
    await expectTaskSuspended(request, second.userTaskKey, false);
    await assertStatusCode(
      await completeUserTask(request, second.userTaskKey),
      204,
    );
    await expectTasks(
      request,
      {userTaskKey: second.userTaskKey, state: 'COMPLETED'},
      [second.userTaskKey],
      false,
    );
    const [third] = await expectTasks(
      request,
      {processInstanceKey, state: 'CREATED'},
      1,
      false,
    );
    expect([first.userTaskKey, second.userTaskKey]).not.toContain(
      third.userTaskKey,
    );
    await assertStatusCode(
      await completeUserTask(request, third.userTaskKey),
      204,
    );
    await expectProcessState(
      request,
      processInstanceKey,
      'COMPLETED',
      extendedAssertionOptions,
    );
    await expectTasks(
      request,
      {processInstanceKey, state: 'COMPLETED'},
      [first.userTaskKey, second.userTaskKey, third.userTaskKey],
      false,
    );
    await expectTasks(request, {processInstanceKey, isSuspended: true}, 0);
  });
});
