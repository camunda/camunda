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
  createGlobalClusterVariable,
  deleteGlobalClusterVariable,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Cluster Variable API Tests - Global Scope', () => {
  const state: Record<string, unknown> = {};
  const createdVariableNames: string[] = [];

  test.afterAll(async ({request}) => {
    for (const name of createdVariableNames) {
      try {
        await request.delete(
          buildUrl('/cluster-variables/global/{name}', {name}),
          {
            headers: jsonHeaders(),
          },
        );
      } catch {
        // Ignore cleanup errors
      }
    }
  });

  test('Create Global Cluster Variable', async ({request}) => {
    await expect(async () => {
      const variable = CREATE_CLUSTER_VARIABLE();
      const res = await request.post(buildUrl('/cluster-variables/global'), {
        headers: jsonHeaders(),
        data: variable,
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, clusterVariableRequiredFields);
      expect(json.name).toBe(variable.name);
      expect(json.scope).toBe('GLOBAL');
      createdVariableNames.push(json.name);
    }).toPass(defaultAssertionOptions);
  });

  test('Create Global Cluster Variable Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/cluster-variables/global'), {
      headers: {},
      data: CREATE_CLUSTER_VARIABLE(),
    });
    await assertUnauthorizedRequest(res);
  });

  test('Create Global Cluster Variable Missing Name Invalid Body 400', async ({
    request,
  }) => {
    const body = {value: {testKey: 'testValue'}};
    const res = await request.post(buildUrl('/cluster-variables/global'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(res, /name/i, 'INVALID_ARGUMENT');
  });

  test('Create Global Cluster Variable Missing Value Invalid Body 400', async ({
    request,
  }) => {
    const body = {name: 'test-var'};
    const res = await request.post(buildUrl('/cluster-variables/global'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(res, /value/i, 'INVALID_ARGUMENT');
  });

  test('Get Global Cluster Variable', async ({request}) => {
    await createGlobalClusterVariable(request, state, 'getTestVar');
    const variableName = state['getTestVarName'] as string;
    createdVariableNames.push(variableName);

    await expect(async () => {
      const res = await request.get(
        buildUrl('/cluster-variables/global/{name}', {name: variableName}),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, clusterVariableRequiredFields);
      expect(json.name).toBe(variableName);
      expect(json.scope).toBe('GLOBAL');
    }).toPass(defaultAssertionOptions);
  });

  test('Get Global Cluster Variable Unauthorized', async ({request}) => {
    await createGlobalClusterVariable(request, state, 'getUnauthorizedVar');
    const variableName = state['getUnauthorizedVarName'] as string;
    createdVariableNames.push(variableName);

    const res = await request.get(
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Global Cluster Variable Not Found', async ({request}) => {
    const res = await request.get(
      buildUrl('/cluster-variables/global/{name}', {name: 'does-not-exist'}),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(res, 'does-not-exist');
  });

  test('Delete Global Cluster Variable', async ({request}) => {
    await createGlobalClusterVariable(request, state, 'deleteTestVar');
    const variableName = state['deleteTestVarName'] as string;

    await test.step('Delete Global Cluster Variable 204', async () => {
      await deleteGlobalClusterVariable(request, variableName);
    });

    await test.step('Get Global Cluster Variable After Deletion', async () => {
      await expect(async () => {
        const after = await request.get(
          buildUrl('/cluster-variables/global/{name}', {name: variableName}),
          {
            headers: jsonHeaders(),
          },
        );
        await assertNotFoundRequest(after, variableName);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Delete Global Cluster Variable Unauthorized', async ({request}) => {
    await createGlobalClusterVariable(request, state, 'deleteUnauthorizedVar');
    const variableName = state['deleteUnauthorizedVarName'] as string;
    createdVariableNames.push(variableName);

    const res = await request.delete(
      buildUrl('/cluster-variables/global/{name}', {name: variableName}),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Delete Global Cluster Variable Not Found', async ({request}) => {
    const res = await request.delete(
      buildUrl('/cluster-variables/global/{name}', {name: 'invalid-var'}),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(res, 'invalid-var');
  });
});
