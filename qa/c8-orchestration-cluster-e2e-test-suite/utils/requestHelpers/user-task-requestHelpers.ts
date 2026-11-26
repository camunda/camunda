/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import type {JSONDoc} from '@camunda8/sdk/dist/zeebe/types.js';
import {expect} from '@playwright/test';
import {
  assertRequiredFields,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
  paginatedResponseFields,
} from '../http';
import {userTaskSearchPageResponseRequiredFields} from '../beans/requestBeans';
import {
  defaultAssertionOptions,
  extendedAssertionOptions,
  uniquePrefixedId,
} from '../constants';
import {validateResponse} from 'json-body-assertions';
import {
  createInstanceOnceDeployed,
  deployProcessModel,
  deployUserTaskProcess,
  partitionOf,
  partitionsCount,
} from './process-definition-requestHelpers';
import {deployWithSubstitutions} from '../zeebeClient';
import {activateSingleJob, completeJob} from './job-requestHelpers';

export interface UserTask {
  userTaskKey: string;
  processInstanceKey: string;
  elementId: string;
  state: string;
  assignee: string | null;
  isSuspended: boolean;
}

export function searchUserTasks(
  request: APIRequestContext,
  body: Record<string, unknown>,
) {
  return request.post(buildUrl('/user-tasks/search'), {
    headers: jsonHeaders(),
    data: body,
  });
}

export async function findUserTask(
  request: APIRequestContext,
  procKey: string,
  state: string,
  elementId?: string,
  assertionOptions = defaultAssertionOptions,
) {
  const localState: Record<string, unknown> = {};
  await expect(async () => {
    // Include elementId in the filter when provided so the API only returns
    // results once the indexer has reflected the correct element — avoids
    // intermediate states where the task is found but still carries the old
    // elementId, which would burn retry budget and require a larger timeout.
    const filter: Record<string, string> = {processInstanceKey: procKey};
    if (elementId) {
      filter.elementId = elementId;
    }
    const searchRes = await searchUserTasks(request, {filter});
    await assertStatusCode(searchRes, 200);
    await validateResponse(
      {
        path: '/user-tasks/search',
        method: 'POST',
        status: '200',
      },
      searchRes,
    );
    const searchJson = await searchRes.json();

    assertRequiredFields(searchJson, paginatedResponseFields);
    assertRequiredFields(
      searchJson.page,
      userTaskSearchPageResponseRequiredFields,
    );
    expect(searchJson.page.totalItems).toBe(1);
    expect(searchJson.items.length).toBe(1);
    expect(searchJson.items[0].state).toBe(state);
    if (elementId) {
      expect(searchJson.items[0].elementId).toBe(elementId);
    }
    localState['userTaskKey'] = searchJson.items[0].userTaskKey;
  }).toPass(assertionOptions);
  return localState['userTaskKey'] as string;
}

export async function expectUserTasks(
  request: APIRequestContext,
  filter: Record<string, unknown>,
  expected: number | string[],
  isSuspended?: boolean,
): Promise<UserTask[]> {
  let tasks: UserTask[] = [];
  await expect(async () => {
    const response = await searchUserTasks(request, {
      filter,
      page: {limit: 1000},
    });
    await assertStatusCode(response, 200);
    const {items, page}: {items: UserTask[]; page: {totalItems: number}} =
      await response.json();
    const count = typeof expected === 'number' ? expected : expected.length;
    expect(page.totalItems).toBe(count);
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

export async function expectUserTaskSuspended(
  request: APIRequestContext,
  userTaskKey: string,
  expected: boolean,
) {
  const [task] = await expectUserTasks(
    request,
    {userTaskKey},
    [userTaskKey],
    expected,
  );
  return task;
}

export async function startUserTaskInstances(
  request: APIRequestContext,
  prefix: string,
  instancesToCancel: string[],
  count = 1,
) {
  const processDefinitionId = uniquePrefixedId(prefix);
  await deployUserTaskProcess(processDefinitionId);
  const processInstanceKeys: string[] = [];
  for (let i = 0; i < count; i++) {
    const {processInstanceKey} = await createInstanceOnceDeployed(
      processDefinitionId,
      1,
    );
    instancesToCancel.push(processInstanceKey);
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
  await expectUserTasks(request, {processDefinitionId}, userTaskKeys, false);
  return {processDefinitionId, processInstanceKeys, userTaskKeys};
}

export async function startUserTaskForSuspension(
  request: APIRequestContext,
  prefix: string,
  instancesToCancel: string[],
  variables: JSONDoc,
  withForm = false,
) {
  const processDefinitionId = uniquePrefixedId(prefix);
  if (withForm) {
    const formId = `${processDefinitionId}-form`;
    await deployWithSubstitutions('./resources/user_task_suspension.form', {
      user_task_suspension: formId,
    });
    await deployWithSubstitutions(
      './resources/User_Task_Process_With_Form.bpmn',
      {
        Form_User_Task: processDefinitionId,
        Form_00xfy2q: formId,
      },
    );
  } else {
    await deployUserTaskProcess(processDefinitionId);
  }
  const {processInstanceKey} = await createInstanceOnceDeployed(
    processDefinitionId,
    1,
    variables,
  );
  instancesToCancel.push(processInstanceKey);
  const userTaskKey = await findUserTask(
    request,
    processInstanceKey,
    'CREATED',
    undefined,
    extendedAssertionOptions,
  );
  await expectUserTaskSuspended(request, userTaskKey, false);
  return {processDefinitionId, processInstanceKey, userTaskKey};
}

export function assignUserTask(
  request: APIRequestContext,
  userTaskKey: string,
  assignee: string,
) {
  return request.post(
    buildUrl('/user-tasks/{userTaskKey}/assignment', {userTaskKey}),
    {headers: jsonHeaders(), data: {assignee}},
  );
}

export async function assignUserTaskAndExpectAssigned(
  request: APIRequestContext,
  userTaskKey: string,
  assignee: string,
) {
  await assertStatusCode(
    await assignUserTask(request, userTaskKey, assignee),
    204,
  );
  await expectUserTaskAssigned(request, userTaskKey, assignee);
}

/**
 * Waits until the user task reports no assignee.
 *
 * The task-details view flips its assignment toggle only once this endpoint
 * reports the change, so a UI test that just waits on the button cannot say
 * whether the command was lost or the propagation is slow. Waiting here, with
 * the propagation budget, keeps that distinction in the failure message.
 */
export async function expectUserTaskUnassigned(
  request: APIRequestContext,
  userTaskKey: string,
  assertionOptions = extendedAssertionOptions,
) {
  await expect(async () => {
    const res = await request.get(
      buildUrl('/user-tasks/{userTaskKey}', {userTaskKey}),
      {headers: jsonHeaders()},
    );
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(
      json.assignee ?? null,
      `user task ${userTaskKey} is still assigned`,
    ).toBeNull();
  }).toPass(assertionOptions);
}

export async function expectUserTaskAssigned(
  request: APIRequestContext,
  userTaskKey: string,
  assignee: string,
  assertionOptions = extendedAssertionOptions,
) {
  await expect(async () => {
    const res = await request.get(
      buildUrl('/user-tasks/{userTaskKey}', {userTaskKey}),
      {headers: jsonHeaders()},
    );
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(
      json.assignee ?? null,
      `user task ${userTaskKey} is not yet assigned to ${assignee}`,
    ).toBe(assignee);
  }).toPass(assertionOptions);
}

export async function completeUserTask(
  request: APIRequestContext,
  userTaskKey: string,
  payload: unknown = {},
) {
  return await request.post(
    buildUrl('/user-tasks/{userTaskKey}/completion', {userTaskKey}),
    {
      headers: jsonHeaders(),
      data: payload,
      timeout: 60_000,
    },
  );
}

export interface UserTaskLookup {
  userTaskKey: string;
  state: string;
  elementId: string;
  elementInstanceKey: string;
  processInstanceKey: string;
  rootProcessInstanceKey: string;
  processDefinitionId: string;
  tenantId: string;
  assignee: string | null;
  priority: number;
  businessId: string | null;
  formKey: string | null;
  variables: Record<string, unknown>;
  [field: string]: unknown;
}

/**
 * Looks up an active user task from the engine. `body: null` sends no request
 * body at all, which the endpoint accepts like an empty one.
 */
export function lookupUserTask(
  request: APIRequestContext,
  userTaskKey: string,
  body: Record<string, unknown> | null = {},
  headers: Record<string, string> = jsonHeaders(),
) {
  const url = buildUrl('/user-tasks/{userTaskKey}/lookup', {userTaskKey});
  return body === null
    ? request.post(url, {headers})
    : request.post(url, {headers, data: body});
}

export async function lookupUserTaskAndExpectFound(
  request: APIRequestContext,
  userTaskKey: string,
  body: Record<string, unknown> | null = {},
): Promise<UserTaskLookup> {
  const res = await lookupUserTask(request, userTaskKey, body);
  await assertStatusCode(res, 200);
  await validateResponse(
    {path: '/user-tasks/{userTaskKey}/lookup', method: 'POST', status: '200'},
    res,
  );
  return res.json();
}

/**
 * Starts `process_with_two_user_tasks` and returns the key of `first_task`.
 * The second task, `second_task`, starts once the first is completed.
 */
export async function startTwoUserTaskInstance(
  request: APIRequestContext,
  instancesToCancel: string[],
  variables?: JSONDoc,
) {
  const processDefinitionId = await deployProcessModel(
    'process_with_two_user_tasks',
  );
  const {processInstanceKey} = await createInstanceOnceDeployed(
    processDefinitionId,
    1,
    variables,
  );
  instancesToCancel.push(processInstanceKey);
  const userTaskKey = await findUserTask(
    request,
    processInstanceKey,
    'CREATED',
    'first_task',
    extendedAssertionOptions,
  );
  return {processDefinitionId, processInstanceKey, userTaskKey};
}

/**
 * Starts a single user task with one task listener of `eventType`, whose job
 * type is unique to this instance so parallel tests never take each other's
 * listener jobs. With a `creating` listener the task stays in `CREATING` until
 * that job is completed.
 */
export async function startUserTaskWithListener(
  request: APIRequestContext,
  eventType: 'creating' | 'assigning' | 'updating' | 'completing' | 'canceling',
  instancesToCancel: string[],
) {
  const jobType = uniquePrefixedId(`ut-lookup-${eventType}`);
  const processDefinitionId = await deployProcessModel(
    'process_with_task_listener',
    {
      'eventType="completing"': `eventType="${eventType}"`,
      'retries="0"': 'retries="3"',
      'type="blocker"': `type="${jobType}"`,
    },
  );
  const {processInstanceKey} = await createInstanceOnceDeployed(
    processDefinitionId,
    1,
  );
  instancesToCancel.push(processInstanceKey);
  const userTaskKey = await findUserTask(
    request,
    processInstanceKey,
    eventType === 'creating' ? 'CREATING' : 'CREATED',
    undefined,
    extendedAssertionOptions,
  );
  return {processInstanceKey, userTaskKey, jobType};
}

/** Polls the lookup until the task reports `state`, and returns that response. */
export async function expectLookupState(
  request: APIRequestContext,
  userTaskKey: string,
  state: string,
  assertionOptions = defaultAssertionOptions,
): Promise<UserTaskLookup> {
  let task: UserTaskLookup | undefined;
  await expect(async () => {
    task = await lookupUserTaskAndExpectFound(request, userTaskKey);
    expect(task.state).toBe(state);
  }).toPass(assertionOptions);
  return task as UserTaskLookup;
}

/**
 * Assigns a task whose `assigning` listener is held by the caller's job type,
 * releases that listener and waits until the lookup reports the new assignee.
 */
export async function assignThroughListener(
  request: APIRequestContext,
  userTaskKey: string,
  processInstanceKey: string,
  jobType: string,
  assignee: string,
) {
  const assignment = assignUserTask(request, userTaskKey, assignee);
  await completeJob(
    request,
    await activateSingleJob(request, jobType, processInstanceKey),
  );
  await assertStatusCode(await assignment, 204);
  const task = await expectLookupState(request, userTaskKey, 'CREATED');
  expect(task.assignee).toBe(assignee);
}

export async function findUserTaskKeys(
  request: APIRequestContext,
  filter: Record<string, unknown>,
  count: number,
): Promise<string[]> {
  const tasks = await expectUserTasks(request, filter, count);
  return tasks.map((task) => task.userTaskKey);
}

/**
 * Finds the active task of `user_task_lookup_child` started under `rootProcessInstanceKey`.
 * User task search has no root-instance filter yet (camunda/camunda#70260), so
 * the match on the root key is done on the search result.
 */
export async function findChildTaskKey(
  request: APIRequestContext,
  rootProcessInstanceKey: string,
): Promise<string> {
  let userTaskKey = '';
  await expect(async () => {
    const res = await searchUserTasks(request, {
      filter: {elementId: 'child_task', state: 'CREATED'},
      page: {limit: 1000},
    });
    await assertStatusCode(res, 200);
    const {items}: {items: Array<UserTask & {rootProcessInstanceKey: string}>} =
      await res.json();
    const match = items.find(
      (task) => task.rootProcessInstanceKey === rootProcessInstanceKey,
    );
    expect(match, `child task of ${rootProcessInstanceKey}`).toBeDefined();
    userTaskKey = match!.userTaskKey;
  }).toPass(extendedAssertionOptions);
  return userTaskKey;
}

/**
 * Starts `user_task_lookup_case` (a case-marked process whose stage holds
 * `stage_task`, followed by a call to `user_task_lookup_child`) with the given
 * business ID, and returns the key of `stage_task`.
 */
export async function startCaseInstance(
  request: APIRequestContext,
  businessId: string,
  instancesToCancel: string[],
  variables: JSONDoc = {},
) {
  const childId = await deployProcessModel('user_task_lookup_child');
  const processDefinitionId = await deployProcessModel(
    'user_task_lookup_case',
    {user_task_lookup_child: childId},
  );
  let processInstanceKey = '';
  await expect(async () => {
    const res = await request.post(buildUrl('/process-instances'), {
      headers: jsonHeaders(),
      data: {processDefinitionId, businessId, variables},
    });
    await assertStatusCode(res, 200);
    processInstanceKey = String((await res.json()).processInstanceKey);
  }).toPass(defaultAssertionOptions);
  instancesToCancel.push(processInstanceKey);
  const userTaskKey = await findUserTask(
    request,
    processInstanceKey,
    'CREATED',
    'stage_task',
    extendedAssertionOptions,
  );
  return {processDefinitionId, processInstanceKey, userTaskKey};
}

/**
 * Asserts the lookup answers 404 `NOT_FOUND` and returns its detail, so the
 * caller can check what the message names.
 */
export async function lookupUserTaskNotFoundDetail(
  request: APIRequestContext,
  userTaskKey: string,
): Promise<string> {
  const res = await lookupUserTask(request, userTaskKey);
  await assertStatusCode(res, 404);
  const json = await res.json();
  expect(json.title).toBe('NOT_FOUND');
  return json.detail as string;
}

/**
 * Starts `process_with_two_user_tasks` instances until every partition holds
 * one `first_task`, and returns the task keys by partition.
 */
export async function startTwoUserTaskInstanceOnEveryPartition(
  request: APIRequestContext,
  instancesToCancel: string[],
  maxInstances = 20,
): Promise<Map<number, string>> {
  const partitions = await partitionsCount(request);
  const tasksByPartition = new Map<number, string>();
  for (let i = 0; i < maxInstances && tasksByPartition.size < partitions; i++) {
    const {userTaskKey} = await startTwoUserTaskInstance(
      request,
      instancesToCancel,
    );
    tasksByPartition.set(partitionOf(userTaskKey), userTaskKey);
  }
  expect(tasksByPartition.size, 'partitions with a task').toBe(partitions);
  return tasksByPartition;
}
