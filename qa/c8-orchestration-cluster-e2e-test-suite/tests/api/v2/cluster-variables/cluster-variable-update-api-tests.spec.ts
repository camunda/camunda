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
  assertNotFoundRequest,
  assertBadRequest,
  assertStatusCode,
} from '../../../../utils/http';
import {UPDATE_CLUSTER_VARIABLE_VALUE} from '../../../../utils/beans/requestBeans';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  createGlobalClusterVariable,
  createTenantClusterVariable,
  createTenantAndStoreResponseFields,
  assertClusterVariableUpdate,
} from '@requestHelpers';
import {
  validateResponseShape,
  validateResponse,
} from '../../../../json-body-assertions';
import {
  cleanupGlobalClusterVariables,
  cleanupTenantClusterVariables,
} from '../../../../utils/clusterVariablesCleanup';

/* eslint-disable playwright/expect-expect */
test.describe
  .parallel('Cluster Variable API Tests - Global Scope UPDATE', () => {
  const state: Record<string, unknown> = {};
  const createdVariableNames: string[] = [];

  test.afterAll(async ({request}) => {
    await cleanupGlobalClusterVariables(request, createdVariableNames);
  });

  test('Update Global Cluster Variable With Object Value', async ({
    request,
  }) => {
    await createGlobalClusterVariable(request, state, 'updateObjVar');
    const variableName = state['updateObjVarName'] as string;
    createdVariableNames.push(variableName);

    const newValue = {updatedKey: 'updatedValue', nested: {value: 123}};

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      newValue,
      variableName,
      'GLOBAL',
    );
  });

  test('Update Global Cluster Variable With String Value', async ({
    request,
  }) => {
    await createGlobalClusterVariable(request, state, 'updateStringVar');
    const variableName = state['updateStringVarName'] as string;
    createdVariableNames.push(variableName);

    const newValue = 'production';

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      newValue,
      variableName,
      'GLOBAL',
    );
  });

  test('Update Global Cluster Variable With Number Value', async ({
    request,
  }) => {
    await createGlobalClusterVariable(request, state, 'updateNumberVar');
    const variableName = state['updateNumberVarName'] as string;
    createdVariableNames.push(variableName);

    const newValue = 42;

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      newValue,
      variableName,
      'GLOBAL',
    );
  });

  test('Update Global Cluster Variable With Boolean True Value', async ({
    request,
  }) => {
    await createGlobalClusterVariable(request, state, 'updateBoolTrueVar');
    const variableName = state['updateBoolTrueVarName'] as string;
    createdVariableNames.push(variableName);

    const newValue = true;

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      newValue,
      variableName,
      'GLOBAL',
    );
  });

  test('Update Global Cluster Variable With Boolean False Value', async ({
    request,
  }) => {
    await createGlobalClusterVariable(request, state, 'updateBoolFalseVar');
    const variableName = state['updateBoolFalseVarName'] as string;
    createdVariableNames.push(variableName);

    const newValue = false;

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      newValue,
      variableName,
      'GLOBAL',
    );
  });

  test('Update Global Cluster Variable With Array Value', async ({request}) => {
    await createGlobalClusterVariable(request, state, 'updateArrayVar');
    const variableName = state['updateArrayVarName'] as string;
    createdVariableNames.push(variableName);

    const newValue = [1, 2, 3, 'four'];

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      newValue,
      variableName,
      'GLOBAL',
    );
  });

  test('Update Global Cluster Variable With Nested Object', async ({
    request,
  }) => {
    await createGlobalClusterVariable(request, state, 'updateNestedVar');
    const variableName = state['updateNestedVarName'] as string;
    createdVariableNames.push(variableName);

    const newValue = {
      feature_flags: {
        new_ui: true,
        beta_features: false,
      },
      config: {
        timeout: 5000,
      },
    };

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      newValue,
      variableName,
      'GLOBAL',
    );
  });

  test('Update Global Cluster Variable With Empty Object', async ({
    request,
  }) => {
    await createGlobalClusterVariable(request, state, 'updateEmptyObjVar');
    const variableName = state['updateEmptyObjVarName'] as string;
    createdVariableNames.push(variableName);

    const newValue = {};

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      newValue,
      variableName,
      'GLOBAL',
    );
  });

  test('Update Global Cluster Variable With Empty Array', async ({request}) => {
    await createGlobalClusterVariable(request, state, 'updateEmptyArrVar');
    const variableName = state['updateEmptyArrVarName'] as string;
    createdVariableNames.push(variableName);

    const newValue: unknown[] = [];

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      newValue,
      variableName,
      'GLOBAL',
    );
  });

  test('Update Global Cluster Variable Multiple Times', async ({request}) => {
    await createGlobalClusterVariable(request, state, 'updateMultipleVar');
    const variableName = state['updateMultipleVarName'] as string;
    createdVariableNames.push(variableName);

    const firstValue = {version: 1};
    const secondValue = {version: 2};

    await test.step('First update', async () => {
      await assertClusterVariableUpdate(
        request,
        buildUrl('/cluster-variables/global/{name}', {name: variableName}),
        firstValue,
        variableName,
        'GLOBAL',
      );
    });

    await test.step('Second update', async () => {
      await assertClusterVariableUpdate(
        request,
        buildUrl('/cluster-variables/global/{name}', {name: variableName}),
        secondValue,
        variableName,
        'GLOBAL',
      );
    });
  });

  test('Update Global Cluster Variable Unauthorized', async ({request}) => {
    await createGlobalClusterVariable(request, state, 'updateUnauthorized');
    const variableName = state['updateUnauthorizedName'] as string;
    createdVariableNames.push(variableName);

    const res = await request.put(
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      {
        headers: {},
        data: UPDATE_CLUSTER_VARIABLE_VALUE({updated: true}),
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Update Global Cluster Variable Not Found', async ({request}) => {
    const res = await request.put(
      buildUrl('/cluster-variables/global/{name}', {
        name: 'does-not-exist',
      }),
      {
        headers: jsonHeaders(),
        data: UPDATE_CLUSTER_VARIABLE_VALUE('test'),
      },
    );
    await assertNotFoundRequest(res, 'does-not-exist');
  });

  test('Update Global Cluster Variable Missing Value Field Invalid Body 400', async ({
    request,
  }) => {
    await createGlobalClusterVariable(request, state, 'updateMissingValue');
    const variableName = state['updateMissingValueName'] as string;
    createdVariableNames.push(variableName);

    const res = await request.put(
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );
    await assertBadRequest(res, /value/i, 'Bad Request');
  });

  test('Update Global Cluster Variable Verify Response Structure', async ({
    request,
  }) => {
    await createGlobalClusterVariable(request, state, 'updateVerifyResp');
    const variableName = state['updateVerifyRespName'] as string;
    createdVariableNames.push(variableName);

    const newValue = {verified: true, timestamp: Date.now()};

    await test.step('Update using helper function with retry logic', async () => {
      await assertClusterVariableUpdate(
        request,
        buildUrl('/cluster-variables/global/{name}', {name: variableName}),
        newValue,
        variableName,
        'GLOBAL',
      );
    });

    await test.step('Verify variable structure with retry', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl('/cluster-variables/global/{name}', {name: variableName}),
          {
            headers: jsonHeaders(),
          },
        );

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/cluster-variables/global/{name}',
            method: 'GET',
            status: '200',
          },
          res,
        );
        const json = await res.json();

        expect(json.name).toBe(variableName);
        expect(json.scope).toBe('GLOBAL');
        expect(typeof json.value).toBe('string');
        expect(JSON.parse(json.value)).toEqual(newValue);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Update Global Cluster Variable Immediately Retrievable', async ({
    request,
  }) => {
    await createGlobalClusterVariable(
      request,
      state,
      'updateImmediateRetrieval',
    );
    const variableName = state['updateImmediateRetrievalName'] as string;
    createdVariableNames.push(variableName);

    const newValue = {consistency: 'strong'};

    await test.step('Update the variable with retry', async () => {
      await assertClusterVariableUpdate(
        request,
        buildUrl('/cluster-variables/global/{name}', {name: variableName}),
        newValue,
        variableName,
        'GLOBAL',
      );
    });

    await test.step('Verify variable is immediately retrievable', async () => {
      await expect(async () => {
        const getRes = await request.get(
          buildUrl('/cluster-variables/global/{name}', {name: variableName}),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(getRes, 200);
        const json = await getRes.json();
        expect(JSON.parse(json.value)).toEqual(newValue);
      }).toPass(defaultAssertionOptions);
    });
  });
});

test.describe
  .parallel('Cluster Variable API Tests - Tenant Scope UPDATE', () => {
  const state: Record<string, unknown> = {};
  const createdVariables: {tenantId: string; name: string}[] = [];

  test.beforeAll(async ({request}) => {
    // Create tenants for testing
    await createTenantAndStoreResponseFields(request, 2, state);
  });

  test.afterAll(async ({request}) => {
    await cleanupTenantClusterVariables(request, createdVariables);
  });

  test('Update Tenant Cluster Variable With Object Value', async ({
    request,
  }) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantObjVar',
      tenantId,
    );
    const variableName = state['updateTenantObjVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const newValue = {updatedKey: 'tenantValue', nested: {value: 456}};

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      newValue,
      variableName,
      'TENANT',
      tenantId,
    );
  });

  test('Update Tenant Cluster Variable With String Value', async ({
    request,
  }) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantStringVar',
      tenantId,
    );
    const variableName = state['updateTenantStringVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const newValue = 'tenant-production';

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      newValue,
      variableName,
      'TENANT',
      tenantId,
    );
  });

  test('Update Tenant Cluster Variable With Number Value', async ({
    request,
  }) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantNumberVar',
      tenantId,
    );
    const variableName = state['updateTenantNumberVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const newValue = 1000;

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      newValue,
      variableName,
      'TENANT',
      tenantId,
    );
  });

  test('Update Tenant Cluster Variable With Boolean Value', async ({
    request,
  }) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantBoolVar',
      tenantId,
    );
    const variableName = state['updateTenantBoolVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const newValue = true;

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      newValue,
      variableName,
      'TENANT',
      tenantId,
    );
  });

  test('Update Tenant Cluster Variable With Array Value', async ({request}) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantArrayVar',
      tenantId,
    );
    const variableName = state['updateTenantArrayVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const newValue = [10, 20, 30, 'tenant-array'];

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      newValue,
      variableName,
      'TENANT',
      tenantId,
    );
  });

  test('Update Tenant Cluster Variable With Complex Nested Object', async ({
    request,
  }) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantComplexVar',
      tenantId,
    );
    const variableName = state['updateTenantComplexVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const newValue = {
      feature_flags: {
        new_ui: true,
        beta_features: false,
      },
      settings: {
        theme: 'dark',
        notifications: {
          email: true,
          push: false,
        },
      },
    };

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      newValue,
      variableName,
      'TENANT',
      tenantId,
    );
  });

  test('Update Tenant Cluster Variable With Empty Object', async ({
    request,
  }) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantEmptyObjVar',
      tenantId,
    );
    const variableName = state['updateTenantEmptyObjVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const newValue = {};

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      newValue,
      variableName,
      'TENANT',
      tenantId,
    );
  });

  test('Update Tenant Cluster Variable With Empty Array', async ({request}) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantEmptyArrVar',
      tenantId,
    );
    const variableName = state['updateTenantEmptyArrVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const newValue: unknown[] = [];

    await assertClusterVariableUpdate(
      request,
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      newValue,
      variableName,
      'TENANT',
      tenantId,
    );
  });

  test('Update Tenant Cluster Variable Multiple Times', async ({request}) => {
    const tenantId = state['tenantId2'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantMultipleVar',
      tenantId,
    );
    const variableName = state['updateTenantMultipleVarName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const firstValue = {version: 1};
    const secondValue = {version: 2};

    await test.step('First update', async () => {
      await assertClusterVariableUpdate(
        request,
        buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
          tenantId,
          name: variableName,
        }),
        firstValue,
        variableName,
        'TENANT',
        tenantId,
      );
    });

    await test.step('Second update', async () => {
      await assertClusterVariableUpdate(
        request,
        buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
          tenantId,
          name: variableName,
        }),
        secondValue,
        variableName,
        'TENANT',
        tenantId,
      );
    });
  });

  test('Update Tenant Cluster Variable Unauthorized', async ({request}) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantUnauth',
      tenantId,
    );
    const variableName = state['updateTenantUnauthName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const res = await request.put(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      {
        headers: {},
        data: UPDATE_CLUSTER_VARIABLE_VALUE({updated: true}),
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Update Tenant Cluster Variable Not Found', async ({request}) => {
    const tenantId = state['tenantId1'] as string;
    const res = await request.put(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: 'does-not-exist',
      }),
      {
        headers: jsonHeaders(),
        data: UPDATE_CLUSTER_VARIABLE_VALUE('test'),
      },
    );
    await assertNotFoundRequest(res, 'does-not-exist');
  });

  test('Update Tenant Cluster Variable Missing Value Field Invalid Body 400', async ({
    request,
  }) => {
    const tenantId = state['tenantId1'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantMissingVal',
      tenantId,
    );
    const variableName = state['updateTenantMissingValName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const res = await request.put(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId,
        name: variableName,
      }),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );
    await assertBadRequest(res, /value/i, 'Bad Request');
  });

  test('Update Tenant Cluster Variable Invalid Tenant Not Found', async ({
    request,
  }) => {
    const res = await request.put(
      buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
        tenantId: 'invalid-tenant-id',
        name: 'some-variable',
      }),
      {
        headers: jsonHeaders(),
        data: UPDATE_CLUSTER_VARIABLE_VALUE('test'),
      },
    );
    await assertNotFoundRequest(res, 'invalid-tenant-id');
  });

  test('Update Tenant Cluster Variable Verify Response Structure', async ({
    request,
  }) => {
    const tenantId = state['tenantId2'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantVerifyResp',
      tenantId,
    );
    const variableName = state['updateTenantVerifyRespName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const newValue = {verified: true, timestamp: Date.now()};

    await test.step('Update using helper function with retry logic', async () => {
      await assertClusterVariableUpdate(
        request,
        buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
          tenantId,
          name: variableName,
        }),
        newValue,
        variableName,
        'TENANT',
        tenantId,
      );
    });

    await test.step('Verify variable structure with retry', async () => {
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

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/cluster-variables/tenants/{tenantId}/{name}',
            method: 'GET',
            status: '200',
          },
          res,
        );
        const json = await res.json();

        expect(json.name).toBe(variableName);
        expect(json.scope).toBe('TENANT');
        expect(json.tenantId).toBe(tenantId);
        expect(typeof json.value).toBe('string');
        expect(JSON.parse(json.value)).toEqual(newValue);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Update Tenant Cluster Variable Immediately Retrievable', async ({
    request,
  }) => {
    const tenantId = state['tenantId2'] as string;
    await createTenantClusterVariable(
      request,
      state,
      'updateTenantImmediate',
      tenantId,
    );
    const variableName = state['updateTenantImmediateName'] as string;
    createdVariables.push({tenantId, name: variableName});

    const newValue = {consistency: 'strong'};

    await test.step('Update the variable with retry', async () => {
      await assertClusterVariableUpdate(
        request,
        buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
          tenantId,
          name: variableName,
        }),
        newValue,
        variableName,
        'TENANT',
        tenantId,
      );
    });

    await test.step('Verify variable is immediately retrievable', async () => {
      await expect(async () => {
        const getRes = await request.get(
          buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
            tenantId,
            name: variableName,
          }),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(getRes, 200);
        const json = await getRes.json();
        expect(JSON.parse(json.value)).toEqual(newValue);
      }).toPass(defaultAssertionOptions);
    });
  });
});
