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
  paginatedResponseFields,
  assertPaginatedRequest,
  assertStatusCode,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  assertGroupsInResponse,
  assignGroupsToTenant,
  createGroupAndStoreResponseFields,
  createTenant,
  groupIdFromState,
} from '@requestHelpers';
import {GROUPS_EXPECTED_BODY} from '../../../../utils/beans/requestBeans';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Tenant Groups API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createTenant(request, state, '1');
    await createTenant(request, state, '2');
    await createTenant(request, state, '3');
    await assignGroupsToTenant(request, 2, 'tenantId1', state);
    await assignGroupsToTenant(request, 1, 'tenantId2', state);
    await assignGroupsToTenant(request, 3, 'tenantId3', state);
  });

  test('Assign Group To Tenant', async ({request}) => {
    const groupKey = `${state['tenantId1']}`;
    await createGroupAndStoreResponseFields(request, 1, state, groupKey);
    const p = {
      groupId: groupIdFromState('tenantId1', state, 1) as string,
      tenantId: state['tenantId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/groups/{groupId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res, 204);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Group To Tenant Non Existent Group Not Found', async ({
    request,
  }) => {
    const stateParams: Record<string, string> = {
      groupId: 'invalidGroupId',
      tenantId: state['tenantId1'] as string,
    };

    const res = await request.put(
      buildUrl('/tenants/{tenantId}/groups/{groupId}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Group To Tenant Non Existent Tenant Not Found', async ({
    request,
  }) => {
    const stateParams: Record<string, string> = {
      groupId: groupIdFromState('tenantId1', state) as string,
      tenantId: 'invalidTenantId',
    };

    const res = await request.put(
      buildUrl('/tenants/{tenantId}/groups/{groupId}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Group To Tenant Unauthorized', async ({request}) => {
    const stateParams: Record<string, string> = {
      groupId: groupIdFromState('tenantId1', state) as string,
      tenantId: state['tenantId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/groups/{groupId}', stateParams),
        {
          headers: {},
        },
      );
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added Group To Tenant Conflict', async ({request}) => {
    const stateParams: Record<string, string> = {
      groupId: groupIdFromState('tenantId1', state) as string,
      tenantId: state['tenantId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/groups/{groupId}', stateParams),
        {
          headers: jsonHeaders(),
        },
      );

      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Unassign Group From Tenant', async ({request}) => {
    const p = {
      groupId: groupIdFromState('tenantId2', state) as string,
      tenantId: state['tenantId2'] as string,
    };
    await test.step('Unassign Group From Tenant', async () => {
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/tenants/{tenantId}/groups/{groupId}', p),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Tenant Groups For Group After Deletion', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/tenants/{tenantId}/groups/search', p),
          {
            headers: jsonHeaders(),
            data: {},
          },
        );

        await assertStatusCode(res, 200);
        const json = await res.json();
        assertRequiredFields(json, paginatedResponseFields);
        expect(json.page.totalItems).toBe(0);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Unassign Group From Tenant Unauthorized', async ({request}) => {
    const p = {
      groupId: groupIdFromState('tenantId2', state) as string,
      tenantId: state['tenantId2'] as string,
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/groups/{groupId}', p),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign Group From Tenant Non Existent Group Not Found', async ({
    request,
  }) => {
    const p = {
      groupId: 'invalidGroupId',
      tenantId: state['tenantId2'] as string,
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/groups/{groupId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Unassign Group From Tenant Non Existent Tenant Not Found', async ({
    request,
  }) => {
    const p = {
      groupId: groupIdFromState('tenantId2', state) as string,
      tenantId: 'invalidTenantId',
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/groups/{groupId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Search Tenant Groups', async ({request}) => {
    const p = {tenantId: state['tenantId3'] as string};
    const group1 = groupIdFromState('tenantId3', state, 1);
    const group2 = groupIdFromState('tenantId3', state, 2);
    const group3 = groupIdFromState('tenantId3', state, 3);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/tenants/{tenantId}/groups/search', p),
        {headers: jsonHeaders(), data: {}},
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 3,
        totalItemsEqualTo: 3,
      });
      const json = await res.json();
      assertGroupsInResponse(json, GROUPS_EXPECTED_BODY(group1), group1);
      assertGroupsInResponse(json, GROUPS_EXPECTED_BODY(group2), group2);
      assertGroupsInResponse(json, GROUPS_EXPECTED_BODY(group3), group3);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenant Groups Tenant With No Assignments Returns Empty', async ({
    request,
  }) => {
    const tenant = await createTenant(request);
    const p = {tenantId: tenant.tenantId as string};

    await expect(async () => {
      const res = await request.post(
        buildUrl('/tenants/{tenantId}/groups/search', p),
        {headers: jsonHeaders(), data: {}},
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 0,
        totalItemsEqualTo: 0,
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenant Groups Unauthorized', async ({request}) => {
    const p = {tenantId: state['tenantId3'] as string};
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/groups/search', p),
      {headers: {}, data: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Tenant Groups Tenant Not Found', async ({request}) => {
    const p = {tenantId: 'invalid-tenant-id'};
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/groups/search', p),
      {headers: jsonHeaders(), data: {}},
    );
    await assertPaginatedRequest(res, {
      itemsLengthEqualTo: 0,
      totalItemsEqualTo: 0,
    });
  });
});
