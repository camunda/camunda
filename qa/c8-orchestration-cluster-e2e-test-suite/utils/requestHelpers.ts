/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  CREATE_NEW_GROUP,
  CREATE_NEW_MAPPING_RULE,
  CREATE_NEW_ROLE,
  groupRequiredFields,
} from './beans/requestBeans';
import {assertRequiredFields, buildUrl, jsonHeaders} from './http';
import {expect} from '@playwright/test';
import type {APIRequestContext} from 'playwright-core';
import {defaultAssertionOptions, generateUniqueId} from './constants';

export async function createGroupAndStoreResponseFields(
  request: APIRequestContext,
  numberOfGroups: number,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfGroups; i++) {
    const requestBody = CREATE_NEW_GROUP();
    const res = await request.post(buildUrl('/groups'), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    expect(res.status()).toBe(201);
    const json = await res.json();
    assertRequiredFields(json, groupRequiredFields);
    state[`groupId${i}`] = json.groupId;
    state[`name${i}`] = json.name;
    state[`description${i}`] = json.description;
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
      state[`${groupId}clientId${i}`] = clientId;
    }).toPass(defaultAssertionOptions);
  }
}

export async function assignMappingToGroup(
  request: APIRequestContext,
  numberOfMappings: number,
  groupId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfMappings; i++) {
    const mappingRule = await createMappingRule(request);
    const p = {
      groupId: groupId as string,
      mappingRuleId: mappingRule.mappingRuleId as string,
    };

    const res = await request.put(
      buildUrl('/groups/{groupId}/mapping-rules/{mappingRuleId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    expect(res.status()).toBe(204);
    state[`${groupId}mappingRule${i}`] = mappingRule.mappingRuleId;
    state[`${groupId}claimName${i}`] = mappingRule.claimName;
    state[`${groupId}claimValue${i}`] = mappingRule.claimValue;
    state[`${groupId}name${i}`] = mappingRule.name;
  }
}

export async function assignRolesToGroup(
  request: APIRequestContext,
  numberOfRoles: number,
  groupId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfRoles; i++) {
    const role = await createRole(request);
    const p = {
      groupId: groupId as string,
      roleId: role.roleId as string,
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/groups/{groupId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    expect(res.status()).toBe(204);
    state[`${groupId}roleId${i}`] = role.roleId;
    state[`${groupId}name${i}`] = role.name;
    state[`${groupId}description${i}`] = role.description;
  }
}

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
    state[`${groupId}user${i}`] = user;
  }
}

export async function createMappingRule(
  request: APIRequestContext,
  state?: Record<string, unknown>,
  key?: string,
) {
  const body = CREATE_NEW_MAPPING_RULE();
  await expect(async () => {
    const res = await request.post(buildUrl('/mapping-rules'), {
      headers: jsonHeaders(),
      data: body,
    });
    expect(res.status()).toBe(201);
    if (state && key) {
      const json = await res.json();
      state[`mappingRuleId${key}`] = json.mappingRuleId;
      state[`claimName${key}`] = json.claimName;
      state[`claimValue${key}`] = json.claimValue;
      state[`name${key}`] = json.name;
    }
  }).toPass(defaultAssertionOptions);
  return body;
}

export async function createRole(request: APIRequestContext) {
  const body = CREATE_NEW_ROLE();

  const res = await request.post(buildUrl('/roles'), {
    headers: jsonHeaders(),
    data: body,
  });

  expect(res.status()).toBe(201);
  return body;
}

export function groupMappingRuleFromState(
  group: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`${state[group]}mappingRule${nth}`] as string;
}

export function roleIdFromState(
  group: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`${state[group]}roleId${nth}`] as string;
}

export function userFromState(
  group: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`${state[group]}user${nth}`] as string;
}

export function clientIdFromState(
  group: string,
  state: Record<string, unknown>,
  nth: number = 1,
): string {
  return state[`${state[group]}clientId${nth}`] as string;
}

export function mappingRuleIdFromState(
  key: string,
  state: Record<string, unknown>,
): string {
  return state[`mappingRuleId${key}`] as string;
}
