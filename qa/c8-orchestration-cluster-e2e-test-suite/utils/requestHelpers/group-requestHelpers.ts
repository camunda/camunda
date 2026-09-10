/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {defaultAssertionOptions, generateUniqueId} from '../constants';
import {
  assertEqualsForKeys,
  assertRequiredFields,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
} from '../http';
import {expect} from '@playwright/test';
import {createMappingRule, createRole} from './role-requestHelpers';
import {CREATE_NEW_GROUP, groupRequiredFields} from '../beans/requestBeans';
import {Serializable} from 'playwright-core/types/structs';

export async function assignUsersToGroup(
  request: APIRequestContext,
  numberOfUsers: number,
  groupId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfUsers; i++) {
    const user = 'test-user' + generateUniqueId();
    const stateParams: Record<string, string> = {
      groupId: groupId,
      username: user,
    };
    const res = await request.put(
      buildUrl('/groups/{groupId}/users/{username}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );
    expect(res.status()).toBe(204);
    state[`username${groupId}${i}`] = user;
  }
}
export async function assignMappingToGroup(
  request: APIRequestContext,
  numberOfMappings: number,
  groupId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfMappings; i++) {
    const mappingRule = await createMappingRule(
      request,
      state,
      `${groupId}${i}`,
    );
    const p = {
      groupId: groupId as string,
      mappingRuleId: mappingRule.mappingRuleId as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/groups/{groupId}/mapping-rules/{mappingRuleId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  }
}

export async function assignRoleToGroups(
  request: APIRequestContext,
  numberOfRoles: number,
  groupId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfRoles; i++) {
    const role = await createRole(request, state, `${groupId}${i}`);
    const p = {
      groupId: groupId as string,
      roleId: role.roleId as string,
    };
    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/groups/{groupId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  }
}
export async function assignClientToGroup(
  request: APIRequestContext,
  numberOfClients: number,
  groupId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfClients; i++) {
    const clientId = `test-client` + generateUniqueId();
    const p = {
      groupId: groupId as string,
      clientId: clientId as string,
    };
    await expect(async () => {
      const res = await request.put(
        buildUrl('/groups/{groupId}/clients/{clientId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
      state[`clientId${groupId}${i}`] = clientId;
    }).toPass(defaultAssertionOptions);
  }
}
export async function createGroupAndStoreResponseFields(
  request: APIRequestContext,
  numberOfGroups: number,
  state: Record<string, unknown>,
  key?: string,
) {
  for (let i = 1; i <= numberOfGroups; i++) {
    const requestBody = CREATE_NEW_GROUP();
    const res = await request.post(buildUrl('/groups'), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    await assertStatusCode(res, 201);
    const json = await res.json();
    assertRequiredFields(json, groupRequiredFields);
    const arrayKey = key ? `${key}${i}` : `${i}`;
    state[`groupId${arrayKey}`] = json.groupId;
    state[`name${arrayKey}`] = json.name;
    state[`description${arrayKey}`] = json.description;
  }
}

export function assertGroupsInResponse(
  json: Serializable,
  expectedBody: Serializable,
  group: string,
) {
  const matchingItem = json.items.find(
    (it: {groupId: string}) => it.groupId === group,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, ['groupId']);
  assertEqualsForKeys(matchingItem, expectedBody, ['groupId']);
}

export async function createGroup(
  request: APIRequestContext,
  state?: Record<string, unknown>,
  key?: string,
) {
  const body = CREATE_NEW_GROUP();

  const res = await request.post(buildUrl('/groups'), {
    headers: jsonHeaders(),
    data: body,
  });

  await assertStatusCode(res, 201);
  const json = await res.json();
  assertRequiredFields(json, groupRequiredFields);
  if (state && key) {
    state[`groupId${key}`] = json.groupId;
    state[`name${key}`] = json.name;
    state[`description${key}`] = json.description;
  }
  return body;
}
