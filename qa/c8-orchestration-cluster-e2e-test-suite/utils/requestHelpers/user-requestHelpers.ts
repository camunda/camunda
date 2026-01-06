/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {generateUniqueId} from '../constants';
import {
  assertEqualsForKeys,
  assertRequiredFields,
  buildUrl,
  jsonHeaders,
} from '../http';
import {expect} from '@playwright/test';
import {Serializable} from 'playwright-core/types/structs';
import {CREATE_NEW_USER, userRequiredFields} from '../beans/requestBeans';

export async function createUsersAndStoreResponseFields(
  request: APIRequestContext,
  numberOfUsers: number,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfUsers; i++) {
    await createUser(request, state, `${i}`);
  }
}

export async function assignRoleToUsers(
  request: APIRequestContext,
  numberOfUsers: number,
  roleId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfUsers; i++) {
    const user = 'user' + generateUniqueId();
    const p = {
      userId: user,
      roleId: roleId,
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/users/{userId}', p),
      {headers: jsonHeaders()},
    );
    expect(res.status()).toBe(204);
    state[`username${roleId}${i}`] = user;
  }
}

export async function createUser(
  request: APIRequestContext,
  state?: Record<string, unknown>,
  key?: string,
) {
  const body = CREATE_NEW_USER();

  const res = await request.post(buildUrl('/users'), {
    headers: jsonHeaders(),
    data: body,
  });

  expect(res.status()).toBe(201);
  const json = await res.json();
  assertRequiredFields(json, userRequiredFields);
  if (state && key) {
    state[`username${key}`] = json.username;
    state[`name${key}`] = json.name;
    state[`email${key}`] = json.email;
    state[`password${key}`] = body.password;
  }
  return body;
}

export function assertUserNameInResponse(json: Serializable, user: string) {
  const matchingItem = json.items.find(
    (it: {username: string}) => it.username === user,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, ['username']);
  assertEqualsForKeys(matchingItem, {username: user}, ['username']);
}

export function assertUserInResponse(
  json: Serializable,
  expectedBody: Serializable,
  user: string,
) {
  const matchingItem = json.items.find(
    (it: {username: string}) => it.username === user,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, userRequiredFields);
  assertEqualsForKeys(matchingItem, expectedBody, userRequiredFields);
}
