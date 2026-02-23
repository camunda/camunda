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
  assertUnauthorizedRequest,
  assertBadRequest,
  assertPaginatedRequest,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  createGlobalClusterVariable,
  createTenantClusterVariable,
  assertClusterVariableInResponse,
  createTenantAndStoreResponseFields,
} from '@requestHelpers';

test.describe.parallel('Search Cluster Variables API Tests', () => {
  const state: Record<string, unknown> = {};
  const createdVariables: {tenantId?: string; name: string}[] = [];

  test.beforeAll(async ({request}) => {
    // Create a tenant for testing
    await createTenantAndStoreResponseFields(request, 1, state);

    // Create global cluster variables for search tests
    await createGlobalClusterVariable(request, state, 'searchVar1');
    createdVariables.push({name: state['searchVar1Name'] as string});

    await createGlobalClusterVariable(request, state, 'searchVar2');
    createdVariables.push({name: state['searchVar2Name'] as string});

    // Create a tenant-scoped variable for search tests
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'searchTenantVar',
      tenantId,
    );
    createdVariables.push({
      tenantId,
      name: state['searchTenantVarName'] as string,
    });
  });

  test.afterAll(async ({request}) => {
    for (const variable of createdVariables) {
      try {
        if (variable.tenantId) {
          await request.delete(
            buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
              tenantId: variable.tenantId,
              name: variable.name,
            }),
            {headers: jsonHeaders()},
          );
        } else {
          await request.delete(
            buildUrl('/cluster-variables/global/{name}', {name: variable.name}),
            {headers: jsonHeaders()},
          );
        }
      } catch {
        // Ignore cleanup errors
      }
    }
  });

  test('Search Cluster Variables Success', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {},
      });

      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(Array.isArray(body.items)).toBe(true);
      expect(body.items.length).toBeGreaterThan(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables With Name Filter', async ({request}) => {
    const variableName = state['searchVar1Name'] as string;

    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            name: variableName,
          },
        },
      });

      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(body.items.length).toBeGreaterThan(0);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.name).toBe(variableName);
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables With Scope Filter GLOBAL', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            scope: 'GLOBAL',
          },
        },
      });

      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.scope).toBe('GLOBAL');
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables With Scope Filter TENANT', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            scope: 'TENANT',
          },
        },
      });

      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.scope).toBe('TENANT');
        expect(item.tenantId).toBeDefined();
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables With TenantId Filter', async ({request}) => {
    const tenantId = state['tenantId1'] as string;

    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            tenantId: tenantId,
          },
        },
      });

      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.tenantId).toBe(tenantId);
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables With Multiple Filters', async ({request}) => {
    const variableName = state['searchVar1Name'] as string;

    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            name: variableName,
            scope: 'GLOBAL',
          },
        },
      });

      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.name).toBe(variableName);
        expect(item.scope).toBe('GLOBAL');
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables Pagination Limit 1', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {
          page: {
            limit: 1,
          },
        },
      });

      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(body.items.length).toBe(1);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables Sort by Name ASC', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 'name',
              order: 'ASC',
            },
          ],
        },
      });

      expect(res.status()).toBe(200);
      const body = await res.json();
      const names = body.items.map(
        (item: Record<string, unknown>) => item.name as string,
      );
      const sortedNames = [...names].sort();
      expect(names).toEqual(sortedNames);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables Sort by Name DESC', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 'name',
              order: 'DESC',
            },
          ],
        },
      });

      expect(res.status()).toBe(200);
      const body = await res.json();
      const names = body.items.map(
        (item: Record<string, unknown>) => item.name as string,
      );
      const sortedNames = [...names].sort().reverse();
      expect(names).toEqual(sortedNames);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables With truncateValues=false', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl('/cluster-variables/search', undefined, {
          truncateValues: 'false',
        }),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );

      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(Array.isArray(body.items)).toBe(true);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables Finds Created Variables', async ({
    request,
  }) => {
    const var1Name = state['searchVar1Name'] as string;
    const var2Name = state['searchVar2Name'] as string;

    const expectedVar1 = {
      name: var1Name,
      scope: 'GLOBAL',
    };

    const expectedVar2 = {
      name: var2Name,
      scope: 'GLOBAL',
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            scope: 'GLOBAL',
          },
        },
      });

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 1,
        totalItemGreaterThan: 1,
      });
      const json = await res.json();
      assertClusterVariableInResponse(json, expectedVar1, var1Name);
      assertClusterVariableInResponse(json, expectedVar2, var2Name);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables Unauthorized', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: {
          'Content-Type': 'application/json',
        },
        data: {},
      });

      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables Invalid Filter', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            invalidField: 'someValue',
          },
        },
      });

      await assertBadRequest(
        res,
        'Request property [filter.invalidField] cannot be parsed',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables Invalid Sort Field', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              // field omitted on purpose
              order: 'DESC',
            },
          ],
        },
      });

      await assertBadRequest(
        res,
        'Sort field must not be null.',
        'INVALID_ARGUMENT',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Cluster Variables By Non-Existent Name Returns Empty', async ({
    request,
  }) => {
    const body = {
      filter: {
        name: 'non-existent-cluster-variable-name',
      },
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/cluster-variables/search'), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 0,
        totalItemsEqualTo: 0,
      });
    }).toPass(defaultAssertionOptions);
  });
});
