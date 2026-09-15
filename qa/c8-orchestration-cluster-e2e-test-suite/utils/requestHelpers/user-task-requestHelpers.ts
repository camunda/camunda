/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {expect} from '@playwright/test';
import {
  assertRequiredFields,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
  paginatedResponseFields,
} from '../http';
import {userTaskSearchPageResponseRequiredFields} from '../beans/requestBeans';
import {defaultAssertionOptions, extendedAssertionOptions} from '../constants';
import {validateResponse} from 'json-body-assertions';

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
    const searchRes = await request.post(buildUrl('/user-tasks/search'), {
      headers: jsonHeaders(),
      data: {filter},
    });
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

export async function searchUserTasks(
  request: APIRequestContext,
  filter: Record<string, unknown>,
  sort?: Record<string, unknown>[],
) {
  const res = await request.post(buildUrl('/user-tasks/search'), {
    headers: jsonHeaders(),
    data: {filter, ...(sort ? {sort} : {})},
  });
  await assertStatusCode(res, 200);
  return res.json();
}
