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
  assignThroughListener,
  assignUserTask,
  completeJob,
  completeJobWithResult,
  completeUserTask,
  createInstanceOnceDeployed,
  deployProcessModel,
  expectLookupState,
  findChildTaskKey,
  findUserTask,
  findUserTaskKeys,
  getProcessInstance,
  lookupUserTaskAndExpectFound,
  lookupUserTaskNotFoundDetail,
  startCaseInstance,
  startTwoUserTaskInstance,
  startTwoUserTaskInstanceOnEveryPartition,
  startUserTaskWithListener,
} from '@requestHelpers';
import {
  defaultAssertionOptions,
  extendedAssertionOptions,
} from '../../../../utils/constants';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {cancelProcessInstance} from '../../../../utils/zeebeClient';

const APPLICANT_VARIABLES = {
  amount: 25000.5,
  approved: false,
  tags: ['vip', 'sme'],
  applicant: {
    name: 'Test Applicant Ü',
    address: {city: 'Testcity', zip: '00000'},
  },
  recommendation: 'pending',
};
const NOT_FOUND_DETAIL = 'but no such user task was found';

const instancesToCancel: string[] = [];
const cancelingListeners = new Map<string, string>();

test.describe.parallel('User Task Lookup API', () => {
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

  test.describe('Response and variables', () => {
    test('Every visible variable is returned without a body, with an empty body and with an empty fetchVariables', async ({
      request,
    }) => {
      const {userTaskKey} = await startTwoUserTaskInstance(
        request,
        instancesToCancel,
        APPLICANT_VARIABLES,
      );

      const withoutBody = await lookupUserTaskAndExpectFound(
        request,
        userTaskKey,
        null,
      );
      const emptyBody = await lookupUserTaskAndExpectFound(
        request,
        userTaskKey,
      );
      const emptyList = await lookupUserTaskAndExpectFound(
        request,
        userTaskKey,
        {fetchVariables: []},
      );

      expect(withoutBody.state).toBe('CREATED');
      expect(Object.keys(withoutBody.variables).sort()).toEqual(
        Object.keys(APPLICANT_VARIABLES).sort(),
      );
      expect(emptyBody).toEqual(withoutBody);
      expect(emptyList).toEqual(withoutBody);
    });

    test('fetchVariables returns only the named variables and leaves out unknown names', async ({
      request,
    }) => {
      const {userTaskKey} = await startTwoUserTaskInstance(
        request,
        instancesToCancel,
        APPLICANT_VARIABLES,
      );

      const named = await lookupUserTaskAndExpectFound(request, userTaskKey, {
        fetchVariables: ['amount', 'doesNotExist', 'applicant'],
      });
      const unknownOnly = await lookupUserTaskAndExpectFound(
        request,
        userTaskKey,
        {fetchVariables: ['doesNotExist']},
      );

      expect(named.variables).toEqual({
        amount: APPLICANT_VARIABLES.amount,
        applicant: APPLICANT_VARIABLES.applicant,
      });
      expect(unknownOnly.variables).toEqual({});
    });

    test('Variables keep their JSON types and nested values', async ({
      request,
    }) => {
      const {userTaskKey} = await startTwoUserTaskInstance(
        request,
        instancesToCancel,
        APPLICANT_VARIABLES,
      );

      const task = await lookupUserTaskAndExpectFound(request, userTaskKey);

      expect(task.variables).toEqual(APPLICANT_VARIABLES);
    });

    test('A large value is returned whole while variable search truncates it', async ({
      request,
    }) => {
      const contractText = 'x'.repeat(50_000);
      const {userTaskKey} = await startTwoUserTaskInstance(
        request,
        instancesToCancel,
        {...APPLICANT_VARIABLES, contractText},
      );

      const task = await lookupUserTaskAndExpectFound(request, userTaskKey, {
        fetchVariables: ['contractText'],
      });

      expect(task.variables.contractText).toBe(contractText);
      await expect(async () => {
        const res = await request.post(
          buildUrl('/user-tasks/{userTaskKey}/variables/search', {
            userTaskKey,
          }),
          {headers: jsonHeaders(), data: {filter: {name: 'contractText'}}},
        );
        await assertStatusCode(res, 200);
        const {items} = await res.json();
        expect(items).toHaveLength(1);
        expect(items[0].isTruncated).toBe(true);
      }).toPass(extendedAssertionOptions);
    });

    test('The task fields match the user task endpoint', async ({request}) => {
      const {userTaskKey} = await startTwoUserTaskInstance(
        request,
        instancesToCancel,
      );
      await assertStatusCode(
        await assignUserTask(request, userTaskKey, 'qa-user-a'),
        204,
      );
      await assertStatusCode(
        await request.patch(
          buildUrl('/user-tasks/{userTaskKey}', {userTaskKey}),
          {
            headers: jsonHeaders(),
            data: {
              changeset: {
                followUpDate: '2030-01-15T09:00:00.000Z',
                priority: 70,
              },
            },
          },
        ),
        204,
      );
      let exported: Record<string, unknown> = {};
      await expect(async () => {
        const res = await request.get(
          buildUrl('/user-tasks/{userTaskKey}', {userTaskKey}),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 200);
        exported = await res.json();
        expect(exported.assignee).toBe('qa-user-a');
        expect(exported.priority).toBe(70);
      }).toPass(extendedAssertionOptions);

      const task = await lookupUserTaskAndExpectFound(request, userTaskKey);

      for (const field of [
        'userTaskKey',
        'elementId',
        'elementInstanceKey',
        'processInstanceKey',
        'rootProcessInstanceKey',
        'processDefinitionId',
        'processDefinitionKey',
        'processDefinitionVersion',
        'tenantId',
        'assignee',
        'candidateGroups',
        'candidateUsers',
        'dueDate',
        'followUpDate',
        'formKey',
        'customHeaders',
        'priority',
        'tags',
        'businessId',
        'creationDate',
      ]) {
        expect(task[field], field).toEqual(exported[field]);
      }
    });

    test('The innermost scope wins and each multi-instance task sees its own element', async ({
      request,
    }) => {
      const documents = ['doc-1.pdf', 'doc-2.pdf', 'doc-3.pdf'];
      const childId = await deployProcessModel('user_task_lookup_child');
      const processDefinitionId = await deployProcessModel(
        'user_task_lookup_scopes',
        {user_task_lookup_child: childId},
      );
      const {processInstanceKey} = await createInstanceOnceDeployed(
        processDefinitionId,
        1,
        {status: 'root', amount: 100, documents},
      );
      instancesToCancel.push(processInstanceKey);
      const innerTaskKey = await findUserTask(
        request,
        processInstanceKey,
        'CREATED',
        'inner_task',
        extendedAssertionOptions,
      );

      const inner = await lookupUserTaskAndExpectFound(request, innerTaskKey);
      await assertStatusCode(
        await completeUserTask(request, innerTaskKey),
        204,
      );
      const miTaskKeys = await findUserTaskKeys(
        request,
        {processInstanceKey, elementId: 'mi_task'},
        documents.length,
      );
      const miTasks = await Promise.all(
        miTaskKeys.map((key) => lookupUserTaskAndExpectFound(request, key)),
      );

      expect(inner.variables.status).toBe('sub');
      expect(inner.variables.amount).toBe(100);
      expect(miTasks.map((task) => task.variables.document).sort()).toEqual(
        documents,
      );
      for (const task of miTasks) {
        expect(task.variables.documents).toEqual(documents);
      }
    });

    test('A task in a called process sees only the variables mapped into it', async ({
      request,
    }) => {
      const childId = await deployProcessModel('user_task_lookup_child');
      const processDefinitionId = await deployProcessModel(
        'user_task_lookup_scopes',
        {user_task_lookup_child: childId},
      );
      const {processInstanceKey} = await createInstanceOnceDeployed(
        processDefinitionId,
        1,
        {status: 'root', amount: 100, parentOnly: 'x', documents: []},
      );
      instancesToCancel.push(processInstanceKey);
      const innerTaskKey = await findUserTask(
        request,
        processInstanceKey,
        'CREATED',
        'inner_task',
        extendedAssertionOptions,
      );
      await assertStatusCode(
        await completeUserTask(request, innerTaskKey),
        204,
      );
      const childTaskKey = await findChildTaskKey(request, processInstanceKey);

      const child = await lookupUserTaskAndExpectFound(request, childTaskKey);

      expect(child.variables).toEqual({amount: 100});
      expect(child.rootProcessInstanceKey).toBe(processInstanceKey);
      expect(child.processInstanceKey).not.toBe(processInstanceKey);
    });

    test('Every partition serves the lookup of its own tasks', async ({
      request,
    }) => {
      const tasksByPartition = await startTwoUserTaskInstanceOnEveryPartition(
        request,
        instancesToCancel,
      );

      const found = await Promise.all(
        [...tasksByPartition.values()].map((key) =>
          lookupUserTaskAndExpectFound(request, key),
        ),
      );

      expect(found.map((task) => task.userTaskKey).sort()).toEqual(
        [...tasksByPartition.values()].sort(),
      );
    });
  });

  test.describe('Consistency with the process', () => {
    test('The next task carries the variables the previous task completed with', async ({
      request,
    }) => {
      const {processInstanceKey, userTaskKey} = await startTwoUserTaskInstance(
        request,
        instancesToCancel,
        APPLICANT_VARIABLES,
      );

      await assertStatusCode(
        await completeUserTask(request, userTaskKey, {
          variables: {recommendation: 'approve', reviewer: 'qa-user-a'},
        }),
        204,
      );
      const nextTaskKey = await findUserTask(
        request,
        processInstanceKey,
        'CREATED',
        'second_task',
        extendedAssertionOptions,
      );
      const next = await lookupUserTaskAndExpectFound(request, nextTaskKey);

      expect(next.variables.recommendation).toBe('approve');
      expect(next.variables.reviewer).toBe('qa-user-a');
      expect(
        await lookupUserTaskNotFoundDetail(request, userTaskKey),
      ).toContain(NOT_FOUND_DETAIL);
    });

    test('A task of a canceled instance is not found', async ({request}) => {
      const {processInstanceKey, userTaskKey} = await startTwoUserTaskInstance(
        request,
        instancesToCancel,
      );

      await cancelProcessInstance(processInstanceKey, {ignoreNotFound: false});

      // The cancellation is acknowledged before the task is terminated.
      await expect(async () => {
        expect(
          await lookupUserTaskNotFoundDetail(request, userTaskKey),
        ).toContain(NOT_FOUND_DETAIL);
      }).toPass(defaultAssertionOptions);
    });
  });

  test.describe('Lifecycle states', () => {
    test('A task waiting on its creating listener is CREATING', async ({
      request,
    }) => {
      const {processInstanceKey, userTaskKey, jobType} =
        await startUserTaskWithListener(request, 'creating', instancesToCancel);
      const jobKey = await activateSingleJob(
        request,
        jobType,
        processInstanceKey,
      );

      const held = await lookupUserTaskAndExpectFound(request, userTaskKey);
      await completeJob(request, jobKey);

      expect(held.state).toBe('CREATING');
      await expectLookupState(request, userTaskKey, 'CREATED');
    });

    test('ASSIGNING keeps the previous assignee until the listener completes', async ({
      request,
    }) => {
      const {processInstanceKey, userTaskKey, jobType} =
        await startUserTaskWithListener(
          request,
          'assigning',
          instancesToCancel,
        );
      await assignThroughListener(
        request,
        userTaskKey,
        processInstanceKey,
        jobType,
        'qa-user-a',
      );

      const assignment = assignUserTask(request, userTaskKey, 'qa-user-b');
      const jobKey = await activateSingleJob(
        request,
        jobType,
        processInstanceKey,
      );
      const held = await lookupUserTaskAndExpectFound(request, userTaskKey);
      await completeJob(request, jobKey);
      await assertStatusCode(await assignment, 204);

      expect(held.state).toBe('ASSIGNING');
      expect(held.assignee).toBe('qa-user-a');
      const released = await expectLookupState(request, userTaskKey, 'CREATED');
      expect(released.assignee).toBe('qa-user-b');
    });

    test('A claim in progress is reported as ASSIGNING', async ({request}) => {
      const {processInstanceKey, userTaskKey, jobType} =
        await startUserTaskWithListener(
          request,
          'assigning',
          instancesToCancel,
        );

      // With authorizations on, a claim must name the calling user.
      const claim = request.post(
        buildUrl('/user-tasks/{userTaskKey}/assignment', {userTaskKey}),
        {
          headers: jsonHeaders(),
          data: {assignee: 'demo', allowOverride: false},
        },
      );
      const jobKey = await activateSingleJob(
        request,
        jobType,
        processInstanceKey,
      );
      const held = await lookupUserTaskAndExpectFound(request, userTaskKey);
      await completeJob(request, jobKey);
      await assertStatusCode(await claim, 204);

      expect(held.state).toBe('ASSIGNING');
      expect(held.assignee).toBeNull();
      const released = await expectLookupState(request, userTaskKey, 'CREATED');
      expect(released.assignee).toBe('demo');
    });

    test('UPDATING keeps the previous priority until the listener completes', async ({
      request,
    }) => {
      const {processInstanceKey, userTaskKey, jobType} =
        await startUserTaskWithListener(request, 'updating', instancesToCancel);
      const before = await lookupUserTaskAndExpectFound(request, userTaskKey);

      const update = request.patch(
        buildUrl('/user-tasks/{userTaskKey}', {userTaskKey}),
        {headers: jsonHeaders(), data: {changeset: {priority: 80}}},
      );
      const jobKey = await activateSingleJob(
        request,
        jobType,
        processInstanceKey,
      );
      const held = await lookupUserTaskAndExpectFound(request, userTaskKey);
      await completeJob(request, jobKey);
      await assertStatusCode(await update, 204);

      expect(held.state).toBe('UPDATING');
      expect(held.priority).toBe(before.priority);
      const released = await expectLookupState(request, userTaskKey, 'CREATED');
      expect(released.priority).toBe(80);
    });

    test('COMPLETING leaves out the completion variables and the task is gone afterwards', async ({
      request,
    }) => {
      const {processInstanceKey, userTaskKey, jobType} =
        await startUserTaskWithListener(
          request,
          'completing',
          instancesToCancel,
        );

      const completion = completeUserTask(request, userTaskKey, {
        variables: {decision: 'reject'},
      });
      const jobKey = await activateSingleJob(
        request,
        jobType,
        processInstanceKey,
      );
      const held = await lookupUserTaskAndExpectFound(request, userTaskKey);
      await completeJob(request, jobKey);
      await assertStatusCode(await completion, 204);

      expect(held.state).toBe('COMPLETING');
      expect(held.variables).not.toHaveProperty('decision');
      expect(
        await lookupUserTaskNotFoundDetail(request, userTaskKey),
      ).toContain(NOT_FOUND_DETAIL);
    });

    test('CANCELING is returned until the canceling listener completes', async ({
      request,
    }) => {
      const {processInstanceKey, userTaskKey, jobType} =
        await startUserTaskWithListener(
          request,
          'canceling',
          instancesToCancel,
          cancelingListeners,
        );

      const cancellation = cancelProcessInstance(processInstanceKey, {
        ignoreNotFound: false,
      });
      const jobKey = await activateSingleJob(
        request,
        jobType,
        processInstanceKey,
      );
      const held = await lookupUserTaskAndExpectFound(request, userTaskKey);
      await completeJob(request, jobKey);
      await cancellation;

      expect(held.state).toBe('CANCELING');
      await expect(async () => {
        expect(
          await lookupUserTaskNotFoundDetail(request, userTaskKey),
        ).toContain(NOT_FOUND_DETAIL);
      }).toPass(defaultAssertionOptions);
    });

    test('A denied assignment keeps the previous assignee', async ({
      request,
    }) => {
      const {processInstanceKey, userTaskKey, jobType} =
        await startUserTaskWithListener(
          request,
          'assigning',
          instancesToCancel,
        );
      await assignThroughListener(
        request,
        userTaskKey,
        processInstanceKey,
        jobType,
        'qa-user-a',
      );

      const assignment = assignUserTask(request, userTaskKey, 'qa-user-b');
      const jobKey = await activateSingleJob(
        request,
        jobType,
        processInstanceKey,
      );
      await completeJobWithResult(request, jobKey, {
        type: 'userTask',
        denied: true,
      });
      await assertStatusCode(await assignment, 409);

      const task = await expectLookupState(request, userTaskKey, 'CREATED');
      expect(task.assignee).toBe('qa-user-a');
    });

    test('Listener corrections are returned once applied', async ({
      request,
    }) => {
      const {processInstanceKey, userTaskKey, jobType} =
        await startUserTaskWithListener(
          request,
          'assigning',
          instancesToCancel,
        );

      const assignment = assignUserTask(request, userTaskKey, 'qa-user-b');
      const jobKey = await activateSingleJob(
        request,
        jobType,
        processInstanceKey,
      );
      await completeJobWithResult(request, jobKey, {
        type: 'userTask',
        corrections: {assignee: 'qa-user-c', priority: 90},
      });
      await assertStatusCode(await assignment, 204);

      const task = await expectLookupState(request, userTaskKey, 'CREATED');
      expect(task.assignee).toBe('qa-user-c');
      expect(task.priority).toBe(90);
    });
  });

  test.describe('Keys that are not active user tasks', () => {
    test('An unknown key is not found', async ({request}) => {
      expect(
        await lookupUserTaskNotFoundDetail(request, '2251799813999999'),
      ).toContain(
        "Expected to fetch user task with key '2251799813999999', but no such user task was found",
      );
    });

    test('Process instance and element instance keys are not found', async ({
      request,
    }) => {
      const {processInstanceKey, userTaskKey} = await startTwoUserTaskInstance(
        request,
        instancesToCancel,
      );
      const {elementInstanceKey} = await lookupUserTaskAndExpectFound(
        request,
        userTaskKey,
      );

      expect(
        await lookupUserTaskNotFoundDetail(request, processInstanceKey),
      ).toContain(NOT_FOUND_DETAIL);
      expect(
        await lookupUserTaskNotFoundDetail(request, elementInstanceKey),
      ).toContain(NOT_FOUND_DETAIL);
    });
  });

  test.describe('Case-marked processes', () => {
    test('A task in a case stage is looked up like any other task', async ({
      request,
    }) => {
      const businessId = `QA-CASE-${Date.now()}`;
      const {processInstanceKey, userTaskKey} = await startCaseInstance(
        request,
        businessId,
        instancesToCancel,
        {...APPLICANT_VARIABLES, caseTitle: 'QA case'},
      );
      const {userTaskKey: plainTaskKey} = await startTwoUserTaskInstance(
        request,
        instancesToCancel,
      );

      const caseTask = await lookupUserTaskAndExpectFound(request, userTaskKey);
      const plainTask = await lookupUserTaskAndExpectFound(
        request,
        plainTaskKey,
      );

      expect(Object.keys(caseTask).sort()).toEqual(
        Object.keys(plainTask).sort(),
      );
      expect(caseTask.businessId).toBe(businessId);
      expect(caseTask.processInstanceKey).toBe(processInstanceKey);
      expect(caseTask.variables).toEqual({
        ...APPLICANT_VARIABLES,
        caseTitle: 'QA case',
      });
    });

    test('A task in a process called by a case points to the case root', async ({
      request,
    }) => {
      const {processInstanceKey, userTaskKey} = await startCaseInstance(
        request,
        `QA-CASE-${Date.now()}`,
        instancesToCancel,
      );
      await assertStatusCode(await completeUserTask(request, userTaskKey), 204);
      const childTaskKey = await findChildTaskKey(request, processInstanceKey);

      const child = await lookupUserTaskAndExpectFound(request, childTaskKey);

      expect(child.rootProcessInstanceKey).toBe(processInstanceKey);
      expect(child.processInstanceKey).not.toBe(processInstanceKey);
    });
  });
});
