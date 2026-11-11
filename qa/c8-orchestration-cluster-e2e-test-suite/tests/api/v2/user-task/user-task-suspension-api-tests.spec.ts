/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  activateJobsByType,
  activateSingleJob,
  assignUserTask,
  completeJob,
  completeUserTask,
  createInstanceOnceDeployed,
  deployProcessModel,
  deployUserTaskProcess,
  expectProcessState,
  expectUserTaskAssigned,
  expectUserTasks,
  expectUserTaskSuspended,
  findUserTask,
  getProcessInstance,
  resumeProcessInstance,
  resumeProcessInstanceAfterSuspension,
  runProcessInstanceBatch,
  searchUserTasks,
  startUserTaskInstances,
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
import {cancelProcessInstance} from '../../../../utils/zeebeClient';

const instancesToCancel: string[] = [];
const cancelingListeners = new Map<string, string>();

test.describe.parallel('User Task Suspension API', () => {
  test.afterAll(async ({request}) => {
    const cancellations = await Promise.allSettled(
      instancesToCancel.map((key) =>
        cancelProcessInstance(key, {
          ignorePendingCancellation: cancelingListeners.has(key),
        }),
      ),
    );
    // A failed listener test must not leave cancellation waiting on its job.
    const listeners = await Promise.allSettled(
      [...cancelingListeners].map(async ([processInstanceKey, jobType]) => {
        await expect(async () => {
          const instance = await getProcessInstance(
            request,
            processInstanceKey,
          );
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
      }),
    );
    instancesToCancel.length = 0;
    cancelingListeners.clear();
    expect(
      [...cancellations, ...listeners].filter(
        (result) => result.status === 'rejected',
      ),
    ).toEqual([]);
  });

  test('The isSuspended filter splits suspended and active tasks', async ({
    request,
  }) => {
    const {processDefinitionId, processInstanceKeys, userTaskKeys} =
      await startUserTaskInstances(
        request,
        'ut-susp-filter',
        instancesToCancel,
        2,
      );
    await suspendAndExpectSuspended(request, processInstanceKeys[0]);
    await expectUserTaskSuspended(request, userTaskKeys[0], true);

    await expectUserTasks(
      request,
      {processDefinitionId, isSuspended: true},
      [userTaskKeys[0]],
      true,
    );
    await expectUserTasks(
      request,
      {processDefinitionId, isSuspended: false},
      [userTaskKeys[1]],
      false,
    );
    const allTasks = await expectUserTasks(
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
      await startUserTaskInstances(
        request,
        'ut-susp-intersection',
        instancesToCancel,
        4,
      );
    await assertStatusCode(
      await completeUserTask(request, userTaskKeys[3]),
      204,
    );
    for (const userTaskKey of userTaskKeys.slice(0, 2)) {
      await assertStatusCode(
        await assignUserTask(request, userTaskKey, 'demo'),
        204,
      );
      await expectUserTaskAssigned(request, userTaskKey, 'demo');
    }
    for (const index of [0, 2]) {
      await suspendAndExpectSuspended(request, processInstanceKeys[index]);
      await expectUserTaskSuspended(request, userTaskKeys[index], true);
    }

    const [assignedTask] = await expectUserTasks(
      request,
      {processDefinitionId, isSuspended: true, assignee: 'demo'},
      [userTaskKeys[0]],
      true,
    );
    expect(assignedTask.assignee).toBe('demo');
    await expectUserTasks(
      request,
      {processDefinitionId, isSuspended: false, state: 'CREATED'},
      [userTaskKeys[1]],
      false,
    );
    await expectUserTasks(
      request,
      {processDefinitionId, isSuspended: false, state: 'COMPLETED'},
      [userTaskKeys[3]],
      false,
    );
    await expectUserTasks(
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
    const {processDefinitionId, userTaskKeys} = await startUserTaskInstances(
      request,
      'ut-susp-batch',
      instancesToCancel,
      count,
    );
    await runProcessInstanceBatch(
      request,
      '/process-instances/suspension',
      processDefinitionId,
      'ACTIVE',
      'SUSPENDED',
      count,
    );
    await expectUserTasks(
      request,
      {processDefinitionId, isSuspended: true},
      userTaskKeys,
      true,
    );

    await runProcessInstanceBatch(
      request,
      '/process-instances/resumption',
      processDefinitionId,
      'SUSPENDED',
      'ACTIVE',
      count,
    );
    const resumed = await expectUserTasks(
      request,
      {processDefinitionId},
      userTaskKeys,
      false,
    );
    expect(resumed.map((task) => task.state)).toEqual(
      Array(count).fill('CREATED'),
    );
    await expectUserTasks(request, {processDefinitionId, isSuspended: true}, 0);
  });

  test('A second suspension marks the task again', async ({request}) => {
    const {processInstanceKeys, userTaskKeys} = await startUserTaskInstances(
      request,
      'ut-susp-cycle',
      instancesToCancel,
    );
    for (let cycle = 0; cycle < 2; cycle++) {
      await suspendAndExpectSuspended(request, processInstanceKeys[0]);
      await expectUserTaskSuspended(request, userTaskKeys[0], true);
      await assertStatusCode(
        await resumeProcessInstance(request, processInstanceKeys[0]),
        204,
      );
      const resumed = await expectUserTaskSuspended(
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
      await startUserTaskInstances(
        request,
        'ut-susp-invalid',
        instancesToCancel,
        2,
      );
    await suspendAndExpectSuspended(request, processInstanceKeys[0]);
    await expectUserTaskSuspended(request, userTaskKeys[0], true);
    for (const isSuspended of ['true', 'yes', 1, {}]) {
      await test.step(`Reject isSuspended=${JSON.stringify(isSuspended)}`, async () => {
        const response = await searchUserTasks(request, {
          filter: {processDefinitionId, isSuspended},
        });
        await assertBadRequest(response, /isSuspended/);
      });
    }
    const unfiltered = await expectUserTasks(
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
    const response = await searchUserTasks(request, {
      sort: [{field: 'isSuspended', order: 'ASC'}],
    });
    await assertStatusCode(response, 400);
    expect(await response.json()).toMatchObject({
      title: 'Bad Request',
      detail: expect.stringContaining('isSuspended'),
    });
  });

  test('Finished tasks never report suspended', async ({request}) => {
    const {processDefinitionId, processInstanceKeys, userTaskKeys} =
      await startUserTaskInstances(
        request,
        'ut-susp-finished',
        instancesToCancel,
        2,
      );
    await assertStatusCode(
      await completeUserTask(request, userTaskKeys[0]),
      204,
    );
    await suspendAndExpectSuspended(request, processInstanceKeys[1]);
    await expectUserTaskSuspended(request, userTaskKeys[1], true);
    await cancelProcessInstance(processInstanceKeys[1], {
      ignoreNotFound: false,
    });

    for (const [state, userTaskKey] of [
      ['COMPLETED', userTaskKeys[0]],
      ['CANCELED', userTaskKeys[1]],
    ]) {
      const [terminalTask] = await expectUserTasks(
        request,
        {processDefinitionId, state},
        [userTaskKey],
        false,
      );
      expect(terminalTask.state).toBe(state);
      await expectUserTasks(
        request,
        {processDefinitionId, state, isSuspended: true},
        0,
      );
    }
  });

  test('Task completion and assignment are rejected after suspension is requested', async ({
    request,
  }) => {
    const count = 200;
    const processDefinitionId = await deployProcessModel(
      'user_task_suspension_multi_instance',
    );
    const {processInstanceKey} = await createInstanceOnceDeployed(
      processDefinitionId,
      1,
      {items: Array.from({length: count}, (_, index) => index + 1)},
    );
    instancesToCancel.push(processInstanceKey);
    const before = await expectUserTasks(
      request,
      {processInstanceKey, state: 'CREATED'},
      count,
      false,
    );

    const suspension = await suspendProcessInstance(
      request,
      processInstanceKey,
    );
    const [completion, assignment] = await Promise.all([
      completeUserTask(request, before[0].userTaskKey),
      assignUserTask(request, before[1].userTaskKey, 'demo'),
    ]);
    await assertStatusCode(suspension, 204);
    await assertInvalidState(completion);
    await assertInvalidState(assignment);

    const after = await expectUserTasks(
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
    const processDefinitionId = await deployProcessModel(
      'user_task_suspension_parallel',
    );
    const {processInstanceKey} = await createInstanceOnceDeployed(
      processDefinitionId,
      1,
    );
    instancesToCancel.push(processInstanceKey);
    const tasks = await expectUserTasks(
      request,
      {processInstanceKey},
      4,
      false,
    );
    expect(
      tasks.filter((task) => task.elementId === 'multi_task'),
    ).toHaveLength(3);
    expect(
      tasks.filter((task) => task.elementId === 'single_task'),
    ).toHaveLength(1);
    const keys = tasks.map((task) => task.userTaskKey);

    await suspendAndExpectSuspended(request, processInstanceKey);
    await expectUserTasks(request, {processInstanceKey}, keys, true);
    await assertStatusCode(
      await resumeProcessInstance(request, processInstanceKey),
      204,
    );
    await expectUserTasks(request, {processInstanceKey}, keys, false);
    await expectUserTasks(request, {processInstanceKey, isSuspended: true}, 0);
  });

  test('Tasks in embedded and non-interrupting event subprocesses are marked', async ({
    request,
  }) => {
    const messageName = uniquePrefixedId('ut-susp-message');
    const correlationKey = uniquePrefixedId('ut-susp-correlation');
    const processDefinitionId = await deployProcessModel(
      'user_task_suspension_subprocesses',
      {'name="suspension-review-message"': `name="${messageName}"`},
    );
    const {processInstanceKey} = await createInstanceOnceDeployed(
      processDefinitionId,
      1,
      {correlationKey},
    );
    instancesToCancel.push(processInstanceKey);
    await expectUserTasks(request, {processInstanceKey}, 1, false);
    const publication = await request.post(buildUrl('/messages/publication'), {
      headers: jsonHeaders(),
      data: {name: messageName, correlationKey, timeToLive: 60_000},
    });
    await assertStatusCode(publication, 200);
    const tasks = await expectUserTasks(
      request,
      {processInstanceKey},
      2,
      false,
    );
    expect(tasks.map((task) => task.elementId).sort()).toEqual([
      'embedded_task',
      'message_task',
    ]);
    const keys = tasks.map((task) => task.userTaskKey);

    await suspendAndExpectSuspended(request, processInstanceKey);
    await expectUserTasks(request, {processInstanceKey}, keys, true);
    await assertStatusCode(
      await resumeProcessInstance(request, processInstanceKey),
      204,
    );
    await expectUserTasks(request, {processInstanceKey}, keys, false);
  });

  test('A called child task stays active when its parent is suspended', async ({
    request,
  }) => {
    const childId = uniquePrefixedId('ut-susp-child');
    await deployUserTaskProcess(childId);
    const parentId = await deployProcessModel(
      'call_user_task_process_process',
      {
        'processId="process_with_task_listener"': `processId="${childId}"`,
      },
    );
    const {processInstanceKey: parentKey} = await createInstanceOnceDeployed(
      parentId,
      1,
    );
    instancesToCancel.push(parentKey);
    const [childTask] = await expectUserTasks(
      request,
      {processDefinitionId: childId},
      1,
      false,
    );
    await suspendAndExpectSuspended(request, parentKey);

    await expectUserTaskSuspended(request, childTask.userTaskKey, false);
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
    const processDefinitionId = await deployProcessModel(
      'user_task_suspension_parallel',
    );
    const {processInstanceKey} = await createInstanceOnceDeployed(
      processDefinitionId,
      1,
    );
    instancesToCancel.push(processInstanceKey);
    const tasks = await expectUserTasks(
      request,
      {processInstanceKey},
      4,
      false,
    );

    await assertStatusCode(
      await suspendProcessInstance(request, processInstanceKey),
      204,
    );
    // A 204 can acknowledge SUSPENDING, where resume returns 409. That
    // transient rejection is accepted before retrying once after SUSPENDED.
    await assertStatusCode(
      await resumeProcessInstanceAfterSuspension(request, processInstanceKey),
      204,
    );
    await expectProcessState(
      request,
      processInstanceKey,
      'ACTIVE',
      extendedAssertionOptions,
    );
    await expectUserTasks(
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
    await expectUserTasks(request, {processInstanceKey}, 4, false);
  });

  test('Cancelling a suspended instance runs its canceling task listener', async ({
    request,
  }) => {
    const jobType = uniquePrefixedId('ut-susp-canceling');
    const processDefinitionId = await deployProcessModel(
      'process_with_task_listener',
      {
        'eventType="completing"': 'eventType="canceling"',
        'retries="0"': 'retries="3"',
        'type="blocker"': `type="${jobType}"`,
      },
    );
    const {processInstanceKey} = await createInstanceOnceDeployed(
      processDefinitionId,
      1,
    );
    instancesToCancel.push(processInstanceKey);
    cancelingListeners.set(processInstanceKey, jobType);
    const userTaskKey = await findUserTask(
      request,
      processInstanceKey,
      'CREATED',
      undefined,
      extendedAssertionOptions,
    );
    await suspendAndExpectSuspended(request, processInstanceKey);
    await expectUserTaskSuspended(request, userTaskKey, true);

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
    await expectUserTasks(
      request,
      {processInstanceKey, state: 'CANCELED'},
      [userTaskKey],
      false,
    );
  });

  test('Sequential multi-instance tasks advance only after resumption', async ({
    request,
  }) => {
    const processDefinitionId = await deployProcessModel(
      'user_task_suspension_multi_instance',
      {
        'isSequential="false"': 'isSequential="true"',
        'inputCollection="=items"': 'inputCollection="=[1,2,3]"',
      },
    );
    const {processInstanceKey} = await createInstanceOnceDeployed(
      processDefinitionId,
      1,
    );
    instancesToCancel.push(processInstanceKey);
    const [first] = await expectUserTasks(
      request,
      {processInstanceKey, state: 'CREATED'},
      1,
      false,
    );
    await suspendAndExpectSuspended(request, processInstanceKey);
    await expectUserTaskSuspended(request, first.userTaskKey, true);
    await assertInvalidState(
      await completeUserTask(request, first.userTaskKey),
    );
    await expectUserTasks(
      request,
      {processInstanceKey},
      [first.userTaskKey],
      true,
    );

    await assertStatusCode(
      await resumeProcessInstance(request, processInstanceKey),
      204,
    );
    await expectUserTaskSuspended(request, first.userTaskKey, false);
    await assertStatusCode(
      await completeUserTask(request, first.userTaskKey),
      204,
    );
    await expectUserTasks(
      request,
      {userTaskKey: first.userTaskKey, state: 'COMPLETED'},
      [first.userTaskKey],
      false,
    );
    const [second] = await expectUserTasks(
      request,
      {processInstanceKey, state: 'CREATED'},
      1,
      false,
    );
    expect(second.userTaskKey).not.toBe(first.userTaskKey);
    await expectUserTasks(request, {processInstanceKey}, 2, false);
    await suspendAndExpectSuspended(request, processInstanceKey);
    await expectUserTaskSuspended(request, second.userTaskKey, true);
    await expectUserTaskSuspended(request, first.userTaskKey, false);

    await assertStatusCode(
      await resumeProcessInstance(request, processInstanceKey),
      204,
    );
    await expectUserTaskSuspended(request, second.userTaskKey, false);
    await assertStatusCode(
      await completeUserTask(request, second.userTaskKey),
      204,
    );
    await expectUserTasks(
      request,
      {userTaskKey: second.userTaskKey, state: 'COMPLETED'},
      [second.userTaskKey],
      false,
    );
    const [third] = await expectUserTasks(
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
    await expectUserTasks(
      request,
      {processInstanceKey, state: 'COMPLETED'},
      [first.userTaskKey, second.userTaskKey, third.userTaskKey],
      false,
    );
    await expectUserTasks(request, {processInstanceKey, isSuspended: true}, 0);
  });
});
