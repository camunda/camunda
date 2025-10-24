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
import {defaultAssertionOptions} from '../constants';

export async function findUserTask(
  request: APIRequestContext,
  procKey: string,
  state: string,
  elementId?: string,
) {
  const localState: Record<string, unknown> = {};
  await expect(async () => {
    const searchRes = await request.post(buildUrl('/user-tasks/search'), {
      headers: jsonHeaders(),
      data: {filter: {processInstanceKey: procKey}},
    });
    await assertStatusCode(searchRes, 200);
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
  }).toPass(defaultAssertionOptions);
  return localState['userTaskKey'] as string;
}
export async function completeUserTask(
  request: APIRequestContext,
  userTaskKey: string,
  payload: unknown = {},
) {
  return await request.post(buildUrl(`/user-tasks/${userTaskKey}/completion`), {
    headers: jsonHeaders(),
    data: payload,
  });
}
