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
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';
import {
  assertUserNameInResponse,
  assignUsersToTenant,
  createTenant,
  createUser,
  userFromState,
} from '@requestHelpers';
import {cleanupUsers} from '../../../../utils/usersCleanup';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Tenant Users API Tests', () => {
  const state: Record<string, unknown> = {};
  const createdUserIds: string[] = [];

  test.beforeAll(async ({request}) => {
    await createTenant(request, state, '1');
    await createTenant(request, state, '2');
    await assignUsersToTenant(request, 3, state['tenantId1'] as string, state);
    await assignUsersToTenant(request, 1, state['tenantId2'] as string, state);

    createdUserIds.push(
      ...(Object.values(state).filter(
        (value) => typeof value === 'string' && value.startsWith('user'),
      ) as string[]),
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, createdUserIds);
  });

  test('Assign User To Tenant', async ({request}) => {
    const tenant = await createTenant(request);
    const user = await createUser(
      request,
      {},
      'test-user' + generateUniqueId(),
    );
    const p = {
      userName: user.username,
      tenantId: tenant.tenantId as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/users/{userName}', p),
        {headers: jsonHeaders()},
      );
      await assertStatusCode(res, 204);
    }).toPass(defaultAssertionOptions);
    createdUserIds.push(user.username);
  });

  test('Assign User To Tenant Non Existent User Success', async ({request}) => {
    const p = {
      userName: 'invalidUserName',
      tenantId: state['tenantId1'] as string,
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/users/{userName}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign User To Tenant Non Existent Tenant Not Found', async ({
    request,
  }) => {
    const p = {
      userName: userFromState('tenantId1', state) as string,
      tenantId: 'invalidTenantId',
    };
    const res = await request.put(
      buildUrl('/tenants/{tenantId}/users/{userName}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign User To Tenant Unauthorized', async ({request}) => {
    const tenantId: string = state['tenantId1'] as string;
    const p = {
      userName: userFromState('tenantId1', state) as string,
      tenantId: tenantId,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/users/{userName}', p),
        {headers: {}},
      );
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added User To Tenant Conflict', async ({request}) => {
    const tenantId: string = state['tenantId1'] as string;
    const p = {
      userName: userFromState('tenantId1', state) as string,
      tenantId: tenantId,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/tenants/{tenantId}/users/{userName}', p),
        {headers: jsonHeaders()},
      );
      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenant Users', async ({request}) => {
    const tenantId: string = state['tenantId1'] as string;
    const p = {tenantId: tenantId};
    const user1: string = userFromState('tenantId1', state, 1);
    const user2: string = userFromState('tenantId1', state, 2);
    const user3: string = userFromState('tenantId1', state, 3);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/tenants/{tenantId}/users/search', p),
        {headers: jsonHeaders(), data: {}},
      );
      await assertPaginatedRequest(res, {
        totalItemsEqualTo: 3,
        itemsLengthEqualTo: 3,
      });
      const json = await res.json();
      assertUserNameInResponse(json, user1);
      assertUserNameInResponse(json, user2);
      assertUserNameInResponse(json, user3);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Tenant Users Unauthorized', async ({request}) => {
    const tenantId: string = state['tenantId1'] as string;
    const p = {userName: userFromState(tenantId, state) as string};
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/users/search', p),
      {headers: {}, data: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Tenant Users For Non Existent User Empty', async ({request}) => {
    const p = {tenantId: 'invalidTenantId'};
    const res = await request.post(
      buildUrl('/tenants/{tenantId}/users/search', p),
      {headers: jsonHeaders(), data: {}},
    );

    await assertPaginatedRequest(res, {
      totalItemsEqualTo: 0,
      itemsLengthEqualTo: 0,
    });
  });

  test('Unassign User From Tenant', async ({request}) => {
    const tenantId: string = state['tenantId2'] as string;
    const tenantUser: string = userFromState('tenantId2', state, 1);
    const p = {
      userName: tenantUser,
      tenantId: tenantId,
    };

    await test.step('Unassign User From Tenant', async () => {
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/tenants/{tenantId}/users/{userName}', p),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Tenant Users After Unassign', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/tenants/{tenantId}/users/search', p),
          {headers: jsonHeaders(), data: {}},
        );
        await assertPaginatedRequest(res, {
          totalItemsEqualTo: 0,
          itemsLengthEqualTo: 0,
        });
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Unassign User From Tenant Unauthorized', async ({request}) => {
    const tenantId: string = state['tenantId1'] as string;
    const p = {
      userName: userFromState('tenantId1', state) as string,
      tenantId: tenantId,
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/users/{userName}', p),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign User From Tenant Non Existent User Not Found', async ({
    request,
  }) => {
    const p = {
      userName: 'invalidUserId',
      tenantId: state['tenantId1'] as string,
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/users/{userName}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Unassign User From Tenant Non Existent Tenant Not Found', async ({
    request,
  }) => {
    const p = {
      userName: userFromState('tenantId1', state) as string,
      tenantId: 'invalidTenantId',
    };
    const res = await request.delete(
      buildUrl('/tenants/{tenantId}/users/{userName}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });
});
