/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, APIRequestContext} from '@playwright/test';
import {
  buildUrl,
  jsonHeaders,
  assertStatusCode,
  extractAndStoreIds,
} from '../http';
import {defaultAssertionOptions} from '../constants';
import {CREATE_CLUSTER_VARIABLE} from '../beans/requestBeans';

/**
 * Creates a global cluster variable and stores the response fields in state
 */
export async function createGlobalClusterVariable(
  request: APIRequestContext,
  state: Record<string, unknown>,
  stateKey: string,
  variableData?: {name: string; value: unknown},
): Promise<void> {
  const data = variableData || CREATE_CLUSTER_VARIABLE();

  await expect(async () => {
    const res = await request.post(buildUrl('/cluster-variables/global'), {
      headers: jsonHeaders(),
      data,
    });
    await assertStatusCode(res, 200);
    await extractAndStoreIds(res, state);
    const json = await res.json();
    state[`${stateKey}Name`] = json.name;
    state[`${stateKey}Value`] = json.value;
    state[`${stateKey}Scope`] = json.scope;
  }).toPass(defaultAssertionOptions);
}

/**
 * Creates a tenant-scoped cluster variable and stores the response fields in state
 */
export async function createTenantClusterVariable(
  request: APIRequestContext,
  state: Record<string, unknown>,
  stateKey: string,
  tenantId: string,
  variableData?: {name: string; value: unknown},
): Promise<void> {
  const data = variableData || CREATE_CLUSTER_VARIABLE();

  await expect(async () => {
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId}),
      {
        headers: jsonHeaders(),
        data,
      },
    );
    await assertStatusCode(res, 200);
    await extractAndStoreIds(res, state);
    const json = await res.json();
    state[`${stateKey}Name`] = json.name;
    state[`${stateKey}Value`] = json.value;
    state[`${stateKey}Scope`] = json.scope;
    state[`${stateKey}TenantId`] = json.tenantId;
  }).toPass(defaultAssertionOptions);
}

/**
 * Creates multiple global cluster variables and stores their info in state
 */
export async function createGlobalClusterVariablesAndStoreResponseFields(
  request: APIRequestContext,
  count: number,
  state: Record<string, unknown>,
): Promise<void> {
  for (let i = 1; i <= count; i++) {
    const variableData = CREATE_CLUSTER_VARIABLE();
    await createGlobalClusterVariable(
      request,
      state,
      `clusterVariable${i}`,
      variableData,
    );
  }
}

/**
 * Deletes a global cluster variable
 */
export async function deleteGlobalClusterVariable(
  request: APIRequestContext,
  name: string,
): Promise<void> {
  await expect(async () => {
    const res = await request.delete(
      buildUrl('/cluster-variables/global/{name}', {name}),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(res, 204);
  }).toPass(defaultAssertionOptions);
}

/**
 * Deletes a tenant-scoped cluster variable
 */
export async function deleteTenantClusterVariable(
  request: APIRequestContext,
  tenantId: string,
  name: string,
): Promise<void> {
  await expect(async () => {
    const res = await request.delete(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name,
      }),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(res, 204);
  }).toPass(defaultAssertionOptions);
}

/**
 * Asserts that a cluster variable exists in a search response
 */
export function assertClusterVariableInResponse(
  json: {items: Record<string, unknown>[]},
  expectedBody: Record<string, unknown>,
  variableName: string,
): void {
  const found = json.items.find(
    (item: Record<string, unknown>) => item.name === variableName,
  );
  expect(found).toBeDefined();
  for (const key of Object.keys(expectedBody)) {
    expect(found![key]).toEqual(expectedBody[key]);
  }
}
