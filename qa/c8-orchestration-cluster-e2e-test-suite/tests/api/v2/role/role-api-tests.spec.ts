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
  CREATE_NEW_ROLE,
  roleRequiredFields,
  UPDATE_ROLE,
} from '../../../../utils/beans/requestBeans';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  assertRoleInResponse,
  createRoleAndStoreResponseFields,
} from '@requestHelpers';
import {cleanupRoles} from '../../../../utils/rolesCleanup';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Roles API Tests', () => {
  const state: Record<string, unknown> = {};
  const createdRoleIds: string[] = [];

  test.beforeAll(async ({request}) => {
    await createRoleAndStoreResponseFields(request, 3, state);

    createdRoleIds.push(
      ...Object.entries(state)
        .filter(([key]) => key.startsWith('roleId'))
        .map(([, value]) => value as string),
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupRoles(request, createdRoleIds);
  });

  test('Create Role', async ({request}) => {
    await expect(async () => {
      const role = CREATE_NEW_ROLE();
      const res = await request.post(buildUrl('/roles'), {
        headers: jsonHeaders(),
        data: role,
      });

      expect(res.status()).toBe(201);
      const json = await res.json();
      assertRequiredFields(json, roleRequiredFields);
      assertEqualsForKeys(json, role, roleRequiredFields);
      if (json && json.roleId) {
        createdRoleIds.push(json.roleId);
      }
    }).toPass(defaultAssertionOptions);
  });

  test('Create Role Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/roles'), {
      headers: {},
      data: CREATE_NEW_ROLE(),
    });
    await assertUnauthorizedRequest(res);
  });

  test('Create Role Missing Name Invalid Body 400', async ({request}) => {
    const body = {description: 'only description provided'};
    const res = await request.post(buildUrl('/roles'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(res, /.*\b(name)\b.*/i, 'INVALID_ARGUMENT');
  });

  test('Create Role Empty Name Invalid Body 400', async ({request}) => {
    const body = {name: '', description: 'empty name'};
    const res = await request.post(buildUrl('/roles'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(res, /.*\b(name)\b.*/i, 'INVALID_ARGUMENT');
  });

  test('Create Role Missing Role Id Invalid Body 400', async ({request}) => {
    const body = {name: 'role name', description: 'only description provided'};
    const res = await request.post(buildUrl('/roles'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertBadRequest(res, /.*\b(roleId)\b.*/i, 'INVALID_ARGUMENT');
  });

  test('Create Role Conflict', async ({request}) => {
    await expect(async () => {
      const role = CREATE_NEW_ROLE();
      role['roleId'] = state['roleId1'] as string;
      const res = await request.post(buildUrl('/roles'), {
        headers: jsonHeaders(),
        data: role,
      });

      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Role', async ({request}) => {
    const p = {roleId: state['roleId1'] as string};
    const expectedBody = {
      roleId: state['roleId1'],
      name: state['roleName1'],
      description: state['roleDescription1'],
    };

    await expect(async () => {
      const res = await request.get(buildUrl('/roles/{roleId}', p), {
        headers: jsonHeaders(),
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, roleRequiredFields);
      assertEqualsForKeys(json, expectedBody, roleRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Role Unauthorized', async ({request}) => {
    const p = {roleId: state['roleId2'] as string};
    const res = await request.get(buildUrl('/roles/{roleId}', p), {
      headers: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Get Role Not Found', async ({request}) => {
    const p = {roleId: 'does-not-exist'};
    const res = await request.get(buildUrl('/roles/{roleId}', p), {
      headers: jsonHeaders(),
    });
    await assertNotFoundRequest(res, `Role with id '${p.roleId}' not found`);
  });

  test('Update Role', async ({request}) => {
    const p = {roleId: state['roleId2'] as string};
    const requestBody = UPDATE_ROLE();
    const expectedBody = {
      ...p,
      ...requestBody,
    };

    await expect(async () => {
      const res = await request.put(buildUrl('/roles/{roleId}', p), {
        headers: jsonHeaders(),
        data: requestBody,
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, roleRequiredFields);
      assertEqualsForKeys(json, expectedBody, roleRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Update Role Empty Name Invalid Body 400', async ({request}) => {
    const p = {roleId: state['roleId1'] as string};

    await expect(async () => {
      const res = await request.put(buildUrl('/roles/{roleId}', p), {
        headers: jsonHeaders(),
        data: {name: ''},
      });
      await assertBadRequest(res, /.*\b(name)\b.*/i, 'INVALID_ARGUMENT');
    }).toPass(defaultAssertionOptions);
  });

  test('Update Role Missing Name Invalid Body 400', async ({request}) => {
    const p = {roleId: state['roleId1'] as string};

    await expect(async () => {
      const res = await request.put(buildUrl('/roles/{roleId}', p), {
        headers: jsonHeaders(),
        data: {description: 'missing name only description'},
      });
      await assertBadRequest(res, /.*\b(name)\b.*/i, 'INVALID_ARGUMENT');
    }).toPass(defaultAssertionOptions);
  });

  test('Update Role Missing Description Success 200', async ({request}) => {
    const p = {roleId: state['roleId1'] as string};
    const expectedBody = {
      ...p,
      name: 'missing description',
      description: '',
    };
    await expect(async () => {
      const res = await request.put(buildUrl('/roles/{roleId}', p), {
        headers: jsonHeaders(),
        data: {name: 'missing description'},
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, roleRequiredFields);
      assertEqualsForKeys(json, expectedBody, roleRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Update Role Unauthorized', async ({request}) => {
    const p = {roleId: state['roleId2'] as string};
    const requestBody = UPDATE_ROLE();

    const res = await request.put(buildUrl('/roles/{roleId}', p), {
      headers: {},
      data: requestBody,
    });
    await assertUnauthorizedRequest(res);
  });

  test('Update Role Not Found', async ({request}) => {
    const p = {roleId: 'invalid-role'};
    const res = await request.put(buildUrl('/roles/{roleId}', p), {
      headers: jsonHeaders(),
      data: UPDATE_ROLE(),
    });
    await assertNotFoundRequest(
      res,
      "Command 'UPDATE' rejected with code 'NOT_FOUND'",
    );
  });

  test('Delete Role', async ({request}) => {
    const p = {roleId: state['roleId3'] as string};
    await test.step('Delete Role 204', async () => {
      await expect(async () => {
        const res = await request.delete(buildUrl('/roles/{roleId}', p), {
          headers: jsonHeaders(),
        });
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Get Role After Deletion', async () => {
      await expect(async () => {
        const after = await request.get(buildUrl('/roles/{roleId}', p), {
          headers: jsonHeaders(),
        });
        await assertNotFoundRequest(
          after,
          `Role with id '${p.roleId}' not found`,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Delete Role Unauthorized', async ({request}) => {
    const p = {roleId: state['roleId3'] as string};
    const res = await request.delete(buildUrl('/roles/{roleId}', p), {
      headers: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Delete Role Not Found', async ({request}) => {
    const p = {roleId: 'invalid-role'};
    const res = await request.delete(buildUrl('/roles/{roleId}', p), {
      headers: jsonHeaders(),
    });
    await assertNotFoundRequest(
      res,
      "Command 'DELETE' rejected with code 'NOT_FOUND'",
    );
  });

  test('Search Roles', async ({request}) => {
    const role1ExpectedBody = {
      roleId: state['roleId1'],
      name: state['roleName1'],
      description: state['roleDescription1'],
    };
    const role2ExpectedBody = {
      roleId: state['roleId2'],
      name: state['roleName2'],
      description: state['roleDescription2'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/roles/search'), {
        headers: jsonHeaders(),
        data: {},
      });

      await assertPaginatedRequest(res, {
        itemLengthGreaterThan: 2,
        totalItemGreaterThan: 2,
      });
      const json = await res.json();
      assertRoleInResponse(
        json,
        role1ExpectedBody,
        role1ExpectedBody.roleId as string,
      );
      assertRoleInResponse(
        json,
        role2ExpectedBody,
        role2ExpectedBody.roleId as string,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Roles By Name', async ({request}) => {
    const roleName = state['roleName1'];
    const body = {
      filter: {
        name: roleName,
      },
    };
    const expectedBody = {
      roleId: state['roleId1'],
      name: state['roleName1'],
      description: state['roleDescription1'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/roles/search'), {
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
      assertRequiredFields(item, roleRequiredFields);
      assertEqualsForKeys(item, expectedBody, roleRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Roles By Role Id', async ({request}) => {
    const roleId = state['roleId2'];
    const body = {
      filter: {
        roleId: roleId,
      },
    };
    const expectedBody = {
      roleId: state['roleId2'],
      name: state['roleName2'],
      description: state['roleDescription2'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/roles/search'), {
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
      assertRequiredFields(item, roleRequiredFields);
      assertEqualsForKeys(item, expectedBody, roleRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Roles By Multiple Fields', async ({request}) => {
    const requestBody = {
      filter: {
        roleId: state['roleId1'],
        name: state['roleName1'],
      },
    };
    const expectedBody = {
      ...requestBody.filter,
      description: state['roleDescription1'],
    };

    await expect(async () => {
      const res = await request.post(buildUrl('/roles/search'), {
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
      assertRequiredFields(item, roleRequiredFields);
      assertEqualsForKeys(item, expectedBody, roleRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Roles By Invalid Id', async ({request}) => {
    const body = {
      filter: {
        roleId: 'invalidRoleId',
      },
    };

    const res = await request.post(buildUrl('/roles/search'), {
      headers: jsonHeaders(),
      data: body,
    });

    await assertPaginatedRequest(res, {
      itemsLengthEqualTo: 0,
      totalItemsEqualTo: 0,
    });
  });

  test('Search Roles Unauthorized', async ({request}) => {
    const body = {
      filter: {
        name: state['roleName1'],
      },
    };
    const res = await request.post(buildUrl('/roles/search'), {
      headers: {},
      data: body,
    });
    await assertUnauthorizedRequest(res);
  });
});
