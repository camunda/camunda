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
  assertConflictRequest,
  assertPaginatedRequest,
  assertStatusCode,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  assertRolesInResponse,
  assignRolesToTenant,
  createRole,
  createTenant,
  roleIdValueUsingKey,
} from '@requestHelpers';
import {ROLES_EXPECTED_BODY} from '../../../../utils/beans/requestBeans';
import {validateResponse} from 'json-body-assertions';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Tenant Roles API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createTenant(request, state, '1');
    await createTenant(request, state, '2');
    await createTenant(request, state, '3');
    await assignRolesToTenant(request, 2, 'tenantId1', state);
    await assignRolesToTenant(request, 1, 'tenantId2', state);
    await assignRolesToTenant(request, 3, 'tenantId3', state);
  });

  test('Assign Role To Tenant - Success', async ({request}) => {
    const roleBody = await createRole(request);
    const p = {
      roleId: roleBody.roleId as string,
      tenantId: state['tenantId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/roles/{roleId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res, 204);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Role To Tenant Non Existent Role - Not Found', async ({
    request,
  }) => {
    const stateParams: Record<string, string> = {
      roleId: 'invalidRoleId',
      tenantId: state['tenantId1'] as string,
    };

    const res = await request.put(
      buildUrl('/tenants/{tenantId}/roles/{roleId}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Role To Tenant Non Existent Tenant - Not Found', async ({
    request,
  }) => {
    const stateParams: Record<string, string> = {
      roleId: roleIdValueUsingKey('tenantId1', state) as string,
      tenantId: 'invalidTenantId',
    };

    const res = await request.put(
      buildUrl('/tenants/{tenantId}/roles/{roleId}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Role To Tenant - Unauthorized', async ({request}) => {
    const stateParams: Record<string, string> = {
      roleId: roleIdValueUsingKey('tenantId1', state) as string,
      tenantId: state['tenantId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/roles/{roleId}', stateParams),
        {
          headers: {},
        },
      );
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added Role To Tenant - Conflict', async ({request}) => {
    const stateParams: Record<string, string> = {
      roleId: roleIdValueUsingKey('tenantId1', state) as string,
      tenantId: state['tenantId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/roles/{roleId}', stateParams),
        {
          headers: jsonHeaders(),
        },
      );

      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Unassign Role From Tenant - Success', async ({request}) => {
    const p = {
      roleId: roleIdValueUsingKey('tenantId2', state) as string,
      tenantId: state['tenantId2'] as string,
    };

    await test.step('Unassign Role From Tenant', async () => {
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/tenants/{tenantId}/roles/{roleId}', p),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Tenant Roles After Deletion', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/tenants/{tenantId}/roles/search', p),
          {
            headers: jsonHeaders(),
            data: {},
          },
        );

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/tenants/{tenantId}/roles/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        expect(json.page.totalItems).toBe(0);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Unassign Role From Tenant - Unauthorized', async ({request}) => {
    const p = {
      roleId: roleIdValueUsingKey('tenantId2', state) as string,
      tenantId: state['tenantId2'] as string,
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/roles/{roleId}', p),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign Role From Tenant Non Existent Role - Not Found', async ({
    request,
  }) => {
    const p = {
      roleId: 'invalidRoleId',
      tenantId: state['tenantId2'] as string,
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/roles/{roleId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Unassign Role From Tenant Non Existent Tenant - Not Found', async ({
    request,
  }) => {
    const p = {
      roleId: roleIdValueUsingKey('tenantId2', state) as string,
      tenantId: 'invalidTenantId',
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/roles/{roleId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Search Tenant Roles', async ({request}) => {
    const p = {tenantId: state['tenantId3'] as string};
    const role1 = roleIdValueUsingKey('tenantId3', state, 1);
    const role2 = roleIdValueUsingKey('tenantId3', state, 2);
    const role3 = roleIdValueUsingKey('tenantId3', state, 3);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/tenants/{tenantId}/roles/search', p),
        {headers: jsonHeaders(), data: {}},
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 3,
        totalItemsEqualTo: 3,
      });
      const json = await res.json();
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/tenants/{tenantId}/roles/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      assertRolesInResponse(json, ROLES_EXPECTED_BODY(role1), role1);
      assertRolesInResponse(json, ROLES_EXPECTED_BODY(role2), role2);
      assertRolesInResponse(json, ROLES_EXPECTED_BODY(role3), role3);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenant Roles Tenant With No Assignments Returns Empty', async ({
    request,
  }) => {
    const tenant = await createTenant(request);
    const p = {tenantId: tenant.tenantId as string};

    await expect(async () => {
      const res = await request.post(
        buildUrl('/tenants/{tenantId}/roles/search', p),
        {headers: jsonHeaders(), data: {}},
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/tenants/{tenantId}/roles/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 0,
        totalItemsEqualTo: 0,
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenant Roles - Unauthorized', async ({request}) => {
    const p = {tenantId: state['tenantId3'] as string};
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/roles/search', p),
      {headers: {}, data: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Tenant Roles Tenant - Not Found (empty response)', async ({
    request,
  }) => {
    const p = {tenantId: 'invalid-tenant-id'};
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/roles/search', p),
      {headers: jsonHeaders(), data: {}},
    );

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: '/tenants/{tenantId}/roles/search',
        method: 'POST',
        status: '200',
      },
      res,
    );
    await assertPaginatedRequest(res, {
      itemsLengthEqualTo: 0,
      totalItemsEqualTo: 0,
    });
  });
});
