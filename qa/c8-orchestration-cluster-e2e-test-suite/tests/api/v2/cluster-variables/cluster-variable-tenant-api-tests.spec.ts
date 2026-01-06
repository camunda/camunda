/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  jsonHeaders,
  buildUrl,
  assertRequiredFields,
  assertUnauthorizedRequest,
  assertNotFoundRequest,
  assertBadRequest,
} from '../../../../utils/http';
import {
  CREATE_CLUSTER_VARIABLE,
  clusterVariableRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  createTenantClusterVariable,
  deleteTenantClusterVariable,
  createTenantAndStoreResponseFields,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Cluster Variable API Tests - Tenant Scope', () => {
  const state: Record<string, unknown> = {};
  const createdVariables: {tenantId: string; name: string}[] = [];

  test.beforeAll(async ({request}) => {
    // Create tenants for testing
    await createTenantAndStoreResponseFields(request, 2, state);
  });

  test.afterAll(async ({request}) => {
    for (const variable of createdVariables) {
      try {
        await request.delete(
          buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
            tenantId: variable.tenantId,
            name: variable.name,
          }),
          {
            headers: jsonHeaders(),
          },
        );
      } catch {
        // Ignore cleanup errors
      }
    }
  });

  test('Create Tenant Cluster Variable', async ({request}) => {
    const tenantId = state['tenantId1'] as string;

    await expect(async () => {
      const variable = CREATE_CLUSTER_VARIABLE();
      const res = await request.post(
        buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId}),
        {
          headers: jsonHeaders(),
          data: variable,
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, clusterVariableRequiredFields);
      expect(json.name).toBe(variable.name);
      expect(json.scope).toBe('TENANT');
      expect(json.tenantId).toBe(tenantId);
      createdVariables.push({tenantId, name: json.name});
    }).toPass(defaultAssertionOptions);
  });

  test('Create Tenant Cluster Variable Unauthorized', async ({request}) => {
    const tenantId = state['tenantId1'] as string;
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId}),
      {
        headers: {},
        data: CREATE_CLUSTER_VARIABLE(),
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Create Tenant Cluster Variable Missing Name Invalid Body 400', async ({
    request,
  }) => {
    const tenantId = state['tenantId1'] as string;
    const body = {value: {testKey: 'testValue'}};
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId}),
      {
        headers: jsonHeaders(),
        data: body,
      },
    );
    await assertBadRequest(res, /name/i, 'INVALID_ARGUMENT');
  });

  test('Create Tenant Cluster Variable Missing Value Invalid Body 400', async ({
    request,
  }) => {
    const tenantId = state['tenantId1'] as string;
    const body = {name: 'test-var'};
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {tenantId}),
      {
        headers: jsonHeaders(),
        data: body,
      },
    );
    await assertBadRequest(res, /value/i, 'INVALID_ARGUMENT');
  });

  // skipped due to bug 42049: https://github.com/camunda/camunda/issues/42049
  test.skip('Create Tenant Cluster Variable Invalid Tenant Not Found', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/cluster-variables/tenants/{tenantId}', {
        tenantId: 'invalid-tenant-id',
      }),
      {
        headers: jsonHeaders(),
        data: CREATE_CLUSTER_VARIABLE(),
      },
    );
    await assertNotFoundRequest(res, 'invalid-tenant-id');
  });

  test('Get Tenant Cluster Variable', async ({request}) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(request, state, 'getTenantVar', tenantId);
    const variableName = state['getTenantVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    await expect(async () => {
      const res = await request.get(
        buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
          tenantId,
          name: variableName,
        }),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, clusterVariableRequiredFields);
      expect(json.name).toBe(variableName);
      expect(json.scope).toBe('TENANT');
      expect(json.tenantId).toBe(tenantId);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Tenant Cluster Variable Unauthorized', async ({request}) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'getUnauthorizedTenantVar',
      tenantId,
    );
    const variableName = state['getUnauthorizedTenantVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const res = await request.get(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Tenant Cluster Variable Not Found', async ({request}) => {
    const tenantId = state['tenantId1'] as string;
    const res = await request.get(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: 'does-not-exist',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(res, 'does-not-exist');
  });

  test('Delete Tenant Cluster Variable', async ({request}) => {
    const tenantId = state['tenantId2'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'deleteTenantVar',
      tenantId,
    );
    const variableName = state['deleteTenantVarName'] as string;

    await test.step('Delete Tenant Cluster Variable 204', async () => {
      await deleteTenantClusterVariable(request, tenantId, variableName);
    });

    await test.step('Get Tenant Cluster Variable After Deletion', async () => {
      await expect(async () => {
        const after = await request.get(
          buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
            tenantId,
            name: variableName,
          }),
          {
            headers: jsonHeaders(),
          },
        );
        await assertNotFoundRequest(after, variableName);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Delete Tenant Cluster Variable Unauthorized', async ({request}) => {
    const tenantId = state['tenantId2'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'deleteUnauthorizedTenantVar',
      tenantId,
    );
    const variableName = state['deleteUnauthorizedTenantVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const res = await request.delete(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Delete Tenant Cluster Variable Not Found', async ({request}) => {
    const tenantId = state['tenantId1'] as string;
    const res = await request.delete(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: 'invalid-var',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(res, 'invalid-var');
  });
});
