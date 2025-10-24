/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {
  CREATE_NEW_MAPPING_RULE,
  CREATE_NEW_ROLE,
  roleRequiredFields,
} from '../beans/requestBeans';
import {
  assertEqualsForKeys,
  assertRequiredFields,
  buildUrl,
  jsonHeaders,
} from '../http';
import {expect} from '@playwright/test';
import {defaultAssertionOptions, generateUniqueId} from '../constants';
import {groupIdFromState} from './get-value-from-state-requestHelpers';
import {createGroupAndStoreResponseFields} from './group-requestHelpers';
import {Serializable} from 'playwright-core/types/structs';

export async function createRole(
  request: APIRequestContext,
  state?: Record<string, unknown>,
  key?: string,
) {
  const body = CREATE_NEW_ROLE();

  const res = await request.post(buildUrl('/roles'), {
    headers: jsonHeaders(),
    data: body,
  });

  expect(res.status()).toBe(201);
  const json = await res.json();
  assertRequiredFields(json, roleRequiredFields);
  if (state && key) {
    state[`roleId${key}`] = json.roleId;
    state[`roleName${key}`] = json.name;
    state[`roleDescription${key}`] = json.description;
  }
  return body;
}

export async function createRoleAndStoreResponseFields(
  request: APIRequestContext,
  numberOfRoles: number,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfRoles; i++) {
    await createRole(request, state, `${i}`);
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

export async function assignRolesToMappingRules(
  request: APIRequestContext,
  numberOfRules: number,
  roleId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfRules; i++) {
    const rule = await createMappingRule(request, state, `${roleId}${i}`);
    const p = {
      roleId: roleId as string,
      mappingRuleId: rule.mappingRuleId as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/mapping-rules/{mappingRuleId}', p),
        {headers: jsonHeaders()},
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  }
}

export async function assignClientsToRole(
  request: APIRequestContext,
  numberOfClients: number,
  roleId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfClients; i++) {
    const clientId = 'client' + generateUniqueId();
    const p = {
      roleId: roleId,
      clientId: clientId,
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    expect(res.status()).toBe(204);
    state[`client${roleId}${i}`] = clientId;
  }
}

export async function assignGroupsToRole(
  request: APIRequestContext,
  numberOfGroups: number,
  roleIdKey: string,
  state: Record<string, unknown>,
) {
  const roleId = state[roleIdKey] as string;
  await createGroupAndStoreResponseFields(
    request,
    numberOfGroups,
    state,
    roleId,
  );
  for (let i = 1; i <= numberOfGroups; i++) {
    const p = {
      groupId: groupIdFromState(roleIdKey, state, i) as string,
      roleId: roleId as string,
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/groups/{groupId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    expect(res.status()).toBe(204);
  }
}

export function assertRoleInResponse(
  json: Serializable,
  expectedBody: Serializable,
  role: string,
) {
  const matchingItem = json.items.find(
    (it: {roleId: string}) => it.roleId === role,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, roleRequiredFields);
  assertEqualsForKeys(matchingItem, expectedBody, roleRequiredFields);
}
