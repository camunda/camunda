/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {
  groupIdFromState,
  roleIdValueUsingKey,
  mappingRuleIdFromState,
} from './get-value-from-state-requestHelpers';
import {
  assertEqualsForKeys,
  assertRequiredFields,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
} from '../http';
import {expect} from '@playwright/test';
import {generateUniqueId} from '../constants';
import {
  CREATE_NEW_TENANT,
  roleRequiredFields,
  tenantRequiredFields,
  mappingRuleRequiredFields,
} from '../beans/requestBeans';
import {Serializable} from 'playwright-core/types/structs';
import {validateResponse} from 'json-body-assertions';
import {
  createUser,
  createRole,
  createGroupAndStoreResponseFields,
  createMappingRule,
} from '@requestHelpers'

export async function assignUsersToTenant(
  request: APIRequestContext,
  numberOfUsers: number,
  tenantId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfUsers; i++) {
    const user = await createUser(request, {}, 'user' + generateUniqueId());
    const p = {
      userId: user.username,
      tenantId: tenantId,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/users/{userId}', p),
      {headers: jsonHeaders()},
    );
    await assertStatusCode(res, 204);
    state[`username${tenantId}${i}`] = user.username;
  }
}

export async function assignClientsToTenant(
  request: APIRequestContext,
  numberOfClients: number,
  tenantId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfClients; i++) {
    const clientId = 'client' + generateUniqueId();
    const p = {
      tenantId: tenantId,
      clientId: clientId,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/clients/{clientId}', p),
      {headers: jsonHeaders()},
    );
    await assertStatusCode(res, 204);
    state[`client${tenantId}${i}`] = clientId;
  }
}

export async function createTenant(
  request: APIRequestContext,
  state?: Record<string, unknown>,
  key?: string,
) {
  const body = CREATE_NEW_TENANT();

  const res = await request.post(buildUrl('/tenants'), {
    headers: jsonHeaders(),
    data: body,
  });

  await assertStatusCode(res, 201);
  await validateResponse(
    {
      path: '/tenants',
      method: 'POST',
      status: '201',
    },
    res,
  );
  const json = await res.json();
  assertRequiredFields(json, tenantRequiredFields);
  if (state && key) {
    state[`tenantId${key}`] = json.tenantId;
    state[`tenantName${key}`] = json.name;
    state[`tenantDescription${key}`] = json.description;
  }
  return body;
}

export async function createTenantAndStoreResponseFields(
  request: APIRequestContext,
  numberOfTenants: number,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfTenants; i++) {
    await createTenant(request, state, `${i}`);
  }
}
export async function assignGroupsToTenant(
  request: APIRequestContext,
  numberOfGroups: number,
  tenantIdKey: string,
  state: Record<string, unknown>,
) {
  const tenantId = state[tenantIdKey] as string;
  await createGroupAndStoreResponseFields(
    request,
    numberOfGroups,
    state,
    tenantId,
  );
  for (let i = 1; i <= numberOfGroups; i++) {
    const p = {
      groupId: groupIdFromState(tenantIdKey, state, i) as string,
      tenantId: tenantId as string,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/groups/{groupId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(res, 204);
  }
}
export async function assignRolesToTenant(
  request: APIRequestContext,
  numberOfRoles: number,
  tenantIdKey: string,
  state: Record<string, unknown>,
) {
  const tenantId = state[tenantIdKey] as string;
  for (let i = 1; i <= numberOfRoles; i++) {
    await createRole(request, state, `${tenantId}${i}`);
    const p = {
      roleId: roleIdValueUsingKey(tenantIdKey, state, i) as string,
      tenantId: tenantId as string,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/roles/{roleId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(res, 204);
  }
}

export async function assignMappingRulesToTenant(
  request: APIRequestContext,
  numberOfMappingRules: number,
  tenantIdKey: string,
  state: Record<string, unknown>,
) {
  const tenantId = state[tenantIdKey] as string;
  for (let i = 1; i <= numberOfMappingRules; i++) {
    await createMappingRule(request, state, `${tenantId}${i}`);
    const p = {
      mappingRuleId: mappingRuleIdFromState(tenantIdKey, state, i) as string,
      tenantId: tenantId as string,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/mapping-rules/{mappingRuleId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(res, 204);
  }
}

export function assertRolesInResponse(
  json: Serializable,
  expectedBody: Serializable,
  roleId: string,
) {
  const matchingItem = json.items.find(
    (it: {roleId: string}) => it.roleId === roleId,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, roleRequiredFields);
  assertEqualsForKeys(matchingItem, expectedBody, ['roleId']);
}

export function assertMappingRulesInResponse(
  json: Serializable,
  expectedBody: Serializable,
  mappingRuleId: string,
) {
  const matchingItem = json.items.find(
    (it: {mappingRuleId: string}) => it.mappingRuleId === mappingRuleId,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, mappingRuleRequiredFields);
  assertEqualsForKeys(matchingItem, expectedBody, ['mappingRuleId']);
}

export function assertTenantInResponse(
  json: Serializable,
  expectedBody: Serializable,
  role: string,
) {
  const matchingItem = json.items.find(
    (it: {tenantId: string}) => it.tenantId === role,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, tenantRequiredFields);
  assertEqualsForKeys(matchingItem, expectedBody, tenantRequiredFields);
}
