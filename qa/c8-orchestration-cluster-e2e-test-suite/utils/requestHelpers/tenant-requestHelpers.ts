/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {groupIdFromState} from './get-value-from-state-requestHelpers';
import {
  assertEqualsForKeys,
  assertRequiredFields,
  buildUrl,
  jsonHeaders,
} from '../http';
import {expect} from '@playwright/test';
import {generateUniqueId} from '../constants';
import {CREATE_NEW_TENANT, tenantRequiredFields} from '../beans/requestBeans';
import {Serializable} from 'playwright-core/types/structs';
import {createGroupAndStoreResponseFields} from './group-requestHelpers';

export async function assignUsersToTenant(
  request: APIRequestContext,
  numberOfUsers: number,
  tenantId: string,
  state: Record<string, unknown>,
) {
  for (let i = 1; i <= numberOfUsers; i++) {
    const user = 'user' + generateUniqueId();
    const p = {
      userId: user,
      tenantId: tenantId,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/users/{userId}', p),
      {headers: jsonHeaders()},
    );
    expect(res.status()).toBe(204);
    state[`username${tenantId}${i}`] = user;
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
    expect(res.status()).toBe(204);
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

  expect(res.status()).toBe(201);
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
    expect(res.status()).toBe(204);
  }
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
