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
  deployUserTaskProcess,
} from './process-definition-requestHelpers';
import {deployWithSubstitutions} from '../zeebeClient';

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

/**
 * Waits until the user task reports the expected assignee.
 *
 * The task-details view flips its assignment toggle ("Assign to me" vs.
 * "Unassign") only once this endpoint reports the assignee, and the read model
 * lags — and briefly oscillates between the old and new value — while the
 * assignment command propagates through secondary storage. A test that assigns
 * a task and then navigates before this endpoint has settled sees the stale
 * unassigned state and never finds the "Unassign" button. Waiting here on the
 * same endpoint the view reads, with the propagation budget, gates the UI flow
 * on the assignment actually being visible rather than on a fixed sleep.
 */
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
