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
} from '../../../../utils/http';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';
import {
  assertUsersInResponse,
  assignRoleToUsers,
  createRole,
  userFromState,
} from '../../../../utils/requestHelpers';

test.describe.parallel('Role Users API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createRole(request, state, '1');
    await createRole(request, state, '2');
    await assignRoleToUsers(request, 3, state['roleId1'] as string, state);
    await assignRoleToUsers(request, 1, state['roleId2'] as string, state);
  });

  test('Assign Role To User', async ({request}) => {
    const role = await createRole(request);
    const user = 'test-user' + generateUniqueId();
    const p = {
      userId: user,
      roleId: role.roleId as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/users/{userId}', p),
        {headers: jsonHeaders()},
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Role To User Non Existent User Success', async ({request}) => {
    const p = {
      userId: 'invalidUserId',
      roleId: state['roleId1'] as string,
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/users/{userId}', p),
      {headers: jsonHeaders()},
    );
    expect(res.status()).toBe(204);
  });

  test('Assign Role To User Non Existent Role Not Found', async ({request}) => {
    const p = {
      userId: userFromState('roleId1', state) as string,
      roleId: 'invalidRoleId',
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/users/{userId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Role To User Unauthorized', async ({request}) => {
    const roleId: string = state['roleId1'] as string;
    const p = {
      userId: userFromState('roleId1', state) as string,
      roleId: roleId,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/users/{userId}', p),
        {headers: {}},
      );
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added User To Role Conflict', async ({request}) => {
    const roleId: string = state['roleId1'] as string;
    const p = {
      userId: userFromState('roleId1', state) as string,
      roleId: roleId,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/users/{userId}', p),
        {headers: jsonHeaders()},
      );
      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Role Users', async ({request}) => {
    const roleId: string = state['roleId1'] as string;
    const p = {roleId: roleId};
    const user1: string = userFromState('roleId1', state, 1);
    const user2: string = userFromState('roleId1', state, 2);
    const user3: string = userFromState('roleId1', state, 3);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/roles/{roleId}/users/search', p),
        {headers: jsonHeaders(), data: {}},
      );
      await assertPaginatedRequest(res, {
        totalItemsEqualTo: 3,
        itemsLengthEqualTo: 3,
      });
      const json = await res.json();
      assertUsersInResponse(json, user1);
      assertUsersInResponse(json, user2);
      assertUsersInResponse(json, user3);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Role Users Unauthorized', async ({request}) => {
    const roleId: string = state['roleId1'] as string;
    const p = {userId: userFromState(roleId, state) as string};
    const res = await request.post(
      buildUrl('/roles/{userId}/users/search', p),
      {headers: {}, data: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Role Users For Non Existent User Empty', async ({request}) => {
    const p = {roleId: 'invalidRoleId'};
    const res = await request.post(
      buildUrl('/roles/{roleId}/users/search', p),
      {headers: jsonHeaders(), data: {}},
    );

    await assertPaginatedRequest(res, {
      totalItemsEqualTo: 0,
      itemsLengthEqualTo: 0,
    });
  });

  test('Unassign Role From User', async ({request}) => {
    const roleId: string = state['roleId2'] as string;
    const roleUser: string = userFromState('roleId2', state, 1);
    const p = {
      userId: roleUser,
      roleId: roleId,
    };

    await test.step('Unassign Role From User', async () => {
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/roles/{roleId}/users/{userId}', p),
          {headers: jsonHeaders()},
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Role Users After Unassign', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/roles/{userId}/users/search', p),
          {headers: jsonHeaders(), data: {}},
        );
        await assertPaginatedRequest(res, {
          totalItemsEqualTo: 0,
          itemsLengthEqualTo: 0,
        });
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Unassign Role From User Unauthorized', async ({request}) => {
    const roleId: string = state['roleId1'] as string;
    const p = {
      userId: userFromState('roleId1', state) as string,
      roleId: roleId,
    };
    const res = await request.delete(
      buildUrl('/roles/{roleId}/users/{userId}', p),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign Role From User Non Existent User Not Found', async ({
    request,
  }) => {
    const p = {
      userId: 'invalidUserId',
      roleId: state['roleId1'] as string,
    };
    const res = await request.delete(
      buildUrl('/roles/{roleId}/users/{userId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Unassign Role From User Non Existent Role Not Found', async ({
    request,
  }) => {
    const p = {
      userId: userFromState('roleId1', state) as string,
      roleId: 'invalidRoleId',
    };
    const res = await request.delete(
      buildUrl('/roles/{roleId}/users/{userId}', p),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });
});
