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
import {validateResponse} from 'json-body-assertions';

/**
 * Creates a global cluster variable and stores the response fields in state
 */
export async function createGlobalClusterVariable(
  request: APIRequestContext,
  state: Record<string, unknown>,
  stateKey: string,
  variableData?: {
    name: string;
    value: unknown;
    metadata?: Record<string, unknown>;
  },
): Promise<void> {
  const data = variableData || CREATE_CLUSTER_VARIABLE();

  await expect(async () => {
    const res = await request.post(buildUrl('/cluster-variables/global'), {
      headers: jsonHeaders(),
      data,
    });
    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/cluster-variables/global',
        method: 'POST',
        status: '200',
      },
      res,
    );
    await extractAndStoreIds(res, state);
    const json = await res.json();
    state[`${stateKey}Name`] = json.name;
    state[`${stateKey}Value`] = json.value;
    state[`${stateKey}Scope`] = json.scope;
    state[`${stateKey}Metadata`] = json.metadata;
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
  variableData?: {
    name: string;
    value: unknown;
    metadata?: Record<string, unknown>;
  },
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
    await validateResponse(
      {
        path: '/cluster-variables/tenants/{tenantId}',
        method: 'POST',
        status: '200',
      },
      res,
    );
    await extractAndStoreIds(res, state);
    const json = await res.json();
    state[`${stateKey}Name`] = json.name;
    state[`${stateKey}Value`] = json.value;
    state[`${stateKey}Scope`] = json.scope;
    state[`${stateKey}TenantId`] = json.tenantId;
    state[`${stateKey}Metadata`] = json.metadata;
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

// Collects every object key and leaf value in a payload. Preferred over substring
// matching on serialized JSON, which can collide with generated base36 ids.
function collectKeysAndValues(payload: unknown): {
  keys: string[];
  values: unknown[];
} {
  const keys: string[] = [];
  const values: unknown[] = [];

  const walk = (node: unknown): void => {
    if (Array.isArray(node)) {
      node.forEach(walk);
      return;
    }
    if (node !== null && typeof node === 'object') {
      for (const [key, child] of Object.entries(node)) {
        keys.push(key);
        walk(child);
      }
      return;
    }
    values.push(node);
  };

  walk(payload);
  return {keys, values};
}

/**
 * Asserts a FEEL-resolved cluster variable carries only its value, and that no
 * metadata key or value appears anywhere in the surrounding runtime payload.
 */
export function assertNoMetadataLeak(
  resolved: unknown,
  expectedValue: Record<string, unknown>,
  runtimePayload: unknown,
  metadata: Record<string, string | number>,
): void {
  expect(resolved).toEqual(expectedValue);

  const resolvedKeys = Object.keys(resolved as Record<string, unknown>);
  expect(resolvedKeys).not.toContain('metadata');
  expect(resolvedKeys).not.toContain('value');

  const {keys, values} = collectKeysAndValues(runtimePayload);
  for (const metadataKey of Object.keys(metadata)) {
    expect(keys).not.toContain(metadataKey);
  }
  for (const metadataValue of Object.values(metadata)) {
    expect(values).not.toContain(metadataValue);
  }
}

/**
 * Performs a cluster variable update with retry and full validation.
 * Consolidates the repeated PUT + status/shape check pattern used across multiple tests.
 */
export async function assertClusterVariableUpdate(
  request: APIRequestContext,
  url: string,
  value: unknown,
  expectedName: string,
  expectedScope: 'GLOBAL' | 'TENANT',
  expectedTenantId?: string,
): Promise<void> {
  await expect(async () => {
    const res = await request.put(url, {
      headers: jsonHeaders(),
      data: {value},
    });

    await assertStatusCode(res, 200);

    // Determine the correct path for validation based on scope
    const path =
      expectedScope === 'GLOBAL'
        ? ('/cluster-variables/global/{name}' as const)
        : ('/cluster-variables/tenants/{tenantId}/{name}' as const);

    await validateResponse({path, method: 'PUT', status: '200'}, res);

    const json = await res.json();
    expect(json.name).toBe(expectedName);
    expect(json.scope).toBe(expectedScope);
    if (expectedTenantId) {
      expect(json.tenantId).toBe(expectedTenantId);
    }
    // Value is returned as JSON string
    expect(JSON.parse(json.value)).toEqual(value);
  }).toPass(defaultAssertionOptions);
}
