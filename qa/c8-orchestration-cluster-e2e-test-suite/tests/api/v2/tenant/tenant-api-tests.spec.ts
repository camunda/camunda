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
  assertConflictRequest,
  assertEqualsForKeys,
  assertBadRequest,
  assertPaginatedRequest,
} from '../../../../utils/http';
import {
  CREATE_NEW_TENANT,
  tenantRequiredFields,
  UPDATE_TENANT,
} from '../../../../utils/beans/requestBeans';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  assertTenantInResponse,
  createTenantAndStoreResponseFields,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Tenants API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createTenantAndStoreResponseFields(request, 3, state);
  });

  test('Create Tenant', async ({request}) => {
    await expect(async () => {
      const tenant = CREATE_NEW_TENANT();
      const res = await request.post(buildUrl('/tenants'), {
        headers: jsonHeaders(),
        data: tenant,
      });

      expect(res.status()).toBe(201);
      const json = await res.json();
      assertRequiredFields(json, tenantRequiredFields);
      assertEqualsForKeys(json, tenant, tenantRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Create Tenant Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/tenants'), {
      headers: {},
      data: CREATE_NEW_TENANT(),
    });
    await assertUnauthorizedRequest(res);
  });

  test('Create Tenant Missing Name Invalid Body 400', async ({request}) => {
    const body = {description: 'only description provided'};
    const res = await request.post(buildUrl('/tenants'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(res, /.*\b(name)\b.*/i, 'INVALID_ARGUMENT');
  });

  test('Create Tenant Empty Name Invalid Body 400', async ({request}) => {
    const body = {name: '', description: 'empty name'};
    const res = await request.post(buildUrl('/tenants'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(res, /.*\b(name)\b.*/i, 'INVALID_ARGUMENT');
  });

  test('Create Tenant Missing Tenant Id Invalid Body 400', async ({
    request,
  }) => {
    const body = {
      name: 'tenant name',
      description: 'only description provided',
    };
    const res = await request.post(buildUrl('/tenants'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(res, /.*\b(tenantId)\b.*/i, 'INVALID_ARGUMENT');
  });

  test('Create Tenant Conflict', async ({request}) => {
    await expect(async () => {
      const tenant = CREATE_NEW_TENANT();
      tenant['tenantId'] = state['tenantId1'] as string;
      const res = await request.post(buildUrl('/tenants'), {
        headers: jsonHeaders(),
        data: tenant,
      });

      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Tenant', async ({request}) => {
    const p = {tenantId: state['tenantId1'] as string};
    const expectedBody = {
      tenantId: state['tenantId1'],
      name: state['tenantName1'],
      description: state['tenantDescription1'],
    };

    await expect(async () => {
      const res = await request.get(buildUrl('/tenants/{tenantId}', p), {
        headers: jsonHeaders(),
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, tenantRequiredFields);
      assertEqualsForKeys(json, expectedBody, tenantRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Tenant Unauthorized', async ({request}) => {
    const p = {tenantId: state['tenantId2'] as string};
    const res = await request.get(buildUrl('/tenants/{tenantId}', p), {
      headers: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Get Tenant Not Found', async ({request}) => {
    const p = {tenantId: 'does-not-exist'};
    const res = await request.get(buildUrl('/tenants/{tenantId}', p), {
      headers: jsonHeaders(),
    });
    await assertNotFoundRequest(
      res,
      `Tenant with id '${p.tenantId}' not found`,
    );
  });

  test('Update Tenant', async ({request}) => {
    const p = {tenantId: state['tenantId2'] as string};
    const requestBody = UPDATE_TENANT();
    const expectedBody = {
      ...p,
      ...requestBody,
    };

    await expect(async () => {
      const res = await request.put(buildUrl('/tenants/{tenantId}', p), {
        headers: jsonHeaders(),
        data: requestBody,
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, tenantRequiredFields);
      assertEqualsForKeys(json, expectedBody, tenantRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Update Tenant Empty Name Invalid Body 400', async ({request}) => {
    const p = {tenantId: state['tenantId1'] as string};

    await expect(async () => {
      const res = await request.put(buildUrl('/tenants/{tenantId}', p), {
        headers: jsonHeaders(),
        data: {name: ''},
      });
      await assertBadRequest(res, /.*\b(name)\b.*/i, 'INVALID_ARGUMENT');
    }).toPass(defaultAssertionOptions);
  });

  test('Update Tenant Missing Name Invalid Body 400', async ({request}) => {
    const p = {tenantId: state['tenantId1'] as string};

    await expect(async () => {
      const res = await request.put(buildUrl('/tenants/{tenantId}', p), {
        headers: jsonHeaders(),
        data: {description: 'missing name only description'},
      });
      await assertBadRequest(res, /.*\b(name)\b.*/i, 'INVALID_ARGUMENT');
    }).toPass(defaultAssertionOptions);
  });

  test('Update Tenant Missing Description success 200', async ({request}) => {
    const p = {tenantId: state['tenantId1'] as string};
    const expectedBody = {
      ...p,
      name: 'missing description',
      description: '',
    };
    await expect(async () => {
      const res = await request.put(buildUrl('/tenants/{tenantId}', p), {
        headers: jsonHeaders(),
        data: {name: 'missing description'},
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, tenantRequiredFields);
      assertEqualsForKeys(json, expectedBody, tenantRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Update Tenant Unauthorized', async ({request}) => {
    const p = {tenantId: state['tenantId2'] as string};
    const requestBody = UPDATE_TENANT();

    const res = await request.put(buildUrl('/tenants/{tenantId}', p), {
      headers: {},
      data: requestBody,
    });
    await assertUnauthorizedRequest(res);
  });

  test('Update Tenant Not Found', async ({request}) => {
    const p = {tenantId: 'invalid-tenant'};
    const res = await request.put(buildUrl('/tenants/{tenantId}', p), {
      headers: jsonHeaders(),
      data: UPDATE_TENANT(),
    });
    await assertNotFoundRequest(
      res,
      "Command 'UPDATE' rejected with code 'NOT_FOUND'",
    );
  });

  test('Delete tenant', async ({request}) => {
    const p = {tenantId: state['tenantId3'] as string};
    await test.step('Delete tenant 204', async () => {
      await expect(async () => {
        const res = await request.delete(buildUrl('/tenants/{tenantId}', p), {
          headers: jsonHeaders(),
        });
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Get Tenant After Deletion', async () => {
      await expect(async () => {
        const after = await request.get(buildUrl('/tenants/{tenantId}', p), {
          headers: jsonHeaders(),
        });
        await assertNotFoundRequest(
          after,
          `Tenant with id '${p.tenantId}' not found`,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Delete tenant Unauthorized', async ({request}) => {
    const p = {tenantId: state['tenantId3'] as string};
    const res = await request.delete(buildUrl('/tenants/{tenantId}', p), {
      headers: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Delete tenant Not Found', async ({request}) => {
    const p = {tenantId: 'invalid-tenant'};
    const res = await request.delete(buildUrl('/tenants/{tenantId}', p), {
      headers: jsonHeaders(),
    });
    await assertNotFoundRequest(
      res,
      "Command 'DELETE' rejected with code 'NOT_FOUND'",
    );
  });

  test('Search Tenants', async ({request}) => {
    const tenant1ExpectedBody = {
      tenantId: state['tenantId1'],
      name: state['tenantName1'],
      description: state['tenantDescription1'],
    };
    const tenant2ExpectedBody = {
      tenantId: state['tenantId2'],
      name: state['tenantName2'],
      description: state['tenantDescription2'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/tenants/search'), {
        headers: jsonHeaders(),
        data: {},
      });

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 2,
        totalItemGreaterThan: 2,
      });
      const json = await res.json();
      assertTenantInResponse(
        json,
        tenant1ExpectedBody,
        tenant1ExpectedBody.tenantId as string,
      );
      assertTenantInResponse(
        json,
        tenant2ExpectedBody,
        tenant2ExpectedBody.tenantId as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenants By Name', async ({request}) => {
    const tenantName = state['tenantName1'];
    const body = {
      filter: {
        name: tenantName,
      },
    };
    const expectedBody = {
      tenantId: state['tenantId1'],
      name: state['tenantName1'],
      description: state['tenantDescription1'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/tenants/search'), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      const item = json.items[0];
      expect(item).toBeDefined();
      assertRequiredFields(item, tenantRequiredFields);
      assertEqualsForKeys(item, expectedBody, tenantRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenants By Tenant Id', async ({request}) => {
    const tenantId = state['tenantId2'];
    const body = {
      filter: {
        tenantId: tenantId,
      },
    };
    const expectedBody = {
      tenantId: state['tenantId2'],
      name: state['tenantName2'],
      description: state['tenantDescription2'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/tenants/search'), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      const item = json.items[0];
      expect(item).toBeDefined();
      assertRequiredFields(item, tenantRequiredFields);
      assertEqualsForKeys(item, expectedBody, tenantRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenants By Multiple Fields', async ({request}) => {
    const requestBody = {
      filter: {
        tenantId: state['tenantId1'],
        name: state['tenantName1'],
      },
    };
    const expectedBody = {
      ...requestBody.filter,
      description: state['tenantDescription1'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/tenants/search'), {
        headers: jsonHeaders(),
        data: requestBody,
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      const json = await res.json();
      const item = json.items[0];
      expect(item).toBeDefined();
      assertRequiredFields(item, tenantRequiredFields);
      assertEqualsForKeys(item, expectedBody, tenantRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenants By Invalid Id', async ({request}) => {
    const body = {
      filter: {
        tenantId: 'invalidtenantId',
      },
    };

    const res = await request.post(buildUrl('/tenants/search'), {
      headers: jsonHeaders(),
      data: body,
    });

    await assertPaginatedRequest(res, {
      itemsLengthEqualTo: 0,
      totalItemsEqualTo: 0,
    });
  });

  test('Search Tenants Unauthorized', async ({request}) => {
    const body = {
      filter: {
        name: state['tenantName1'],
      },
    };
    const res = await request.post(buildUrl('/tenants/search'), {
      headers: {},
      data: body,
    });
    await assertUnauthorizedRequest(res);
  });
});
