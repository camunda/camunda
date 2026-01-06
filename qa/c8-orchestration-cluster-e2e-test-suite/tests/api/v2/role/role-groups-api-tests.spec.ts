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
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  assertGroupsInResponse,
  assignGroupsToRole,
  createGroupAndStoreResponseFields,
  createRole,
  groupIdFromState,
} from '@requestHelpers';
import {GROUPS_EXPECTED_BODY} from '../../../../utils/beans/requestBeans';
import {cleanupRoles} from '../../../../utils/rolesCleanup';
import {cleanupGroups} from '../../../../utils/groupsCleanup';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Role Groups API Tests', () => {
  const state: Record<string, unknown> = {};
  const createdRoleIds: string[] = [];
  const createdGroupIds: string[] = [];

  test.beforeAll(async ({request}) => {
    await createRole(request, state, '1');
    await createRole(request, state, '2');
    await createRole(request, state, '3');
    await assignGroupsToRole(request, 2, 'roleId1', state);
    await assignGroupsToRole(request, 3, 'roleId2', state);
    await assignGroupsToRole(request, 3, 'roleId3', state);

    createdRoleIds.push(
      ...Object.entries(state)
        .filter(([key]) => key.startsWith('roleId'))
        .map(([, value]) => value as string),
    );

    createdGroupIds.push(
      ...Object.entries(state)
        .filter(([key]) => key.startsWith('groupId'))
        .map(([, value]) => value as string),
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupRoles(request, createdRoleIds);
    await cleanupGroups(request, createdGroupIds);
  });

  test('Assign Role To Group', async ({request}) => {
    const groupKey = `${state['roleId1']}6`;
    await createGroupAndStoreResponseFields(request, 1, state, groupKey);
    const groupId = groupIdFromState(groupKey, state, 6) as string;
    createdGroupIds.push(groupId);
    const p = {
      groupId: groupId,
      roleId: state['roleId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/groups/{groupId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Role To Group Non Existent Group Sucess', async ({request}) => {
    const stateParams: Record<string, string> = {
      groupId: 'invalidGroupId',
      roleId: state['roleId1'] as string,
    };

    const res = await request.put(
      buildUrl('/roles/{roleId}/groups/{groupId}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );
    expect(res.status()).toBe(204);
  });

  test('Assign Role To Group Non Existent Role Not Found', async ({
    request,
  }) => {
    const stateParams: Record<string, string> = {
      groupId: groupIdFromState('roleId1', state) as string,
      roleId: 'invalidRoleId',
    };

    const res = await request.put(
      buildUrl('/roles/{roleId}/groups/{groupId}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign Role To Group Unauthorized', async ({request}) => {
    const stateParams: Record<string, string> = {
      groupId: groupIdFromState('roleId1', state) as string,
      roleId: state['roleId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/groups/{groupId}', stateParams),
        {
          headers: {},
        },
      );
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added Group To Role Conflict', async ({request}) => {
    const stateParams: Record<string, string> = {
      groupId: groupIdFromState('roleId1', state) as string,
      roleId: state['roleId1'] as string,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/roles/{roleId}/groups/{groupId}', stateParams),
        {
          headers: jsonHeaders(),
        },
      );

      await assertConflictRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Role From Group', async ({request}) => {
    const p = {
      groupId: groupIdFromState('roleId2', state) as string,
      roleId: state['roleId2'] as string,
    };
    await test.step('Unassign Role From Group', async () => {
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/roles/{roleId}/groups/{groupId}', p),
          {
            headers: jsonHeaders(),
          },
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Group Roles For Group After Deletion', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/groups/{groupId}/roles/search', p),
          {
            headers: jsonHeaders(),
            data: {},
          },
        );

        expect(res.status()).toBe(200);
        const json = await res.json();
        assertRequiredFields(json, paginatedResponseFields);
        expect(json.page.totalItems).toBe(0);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Unassign Role From Group Unauthorized', async ({request}) => {
    const p = {
      groupId: groupIdFromState('roleId2', state) as string,
      roleId: state['roleId2'] as string,
    };
    const res = await request.delete(
      buildUrl('/roles/{roleId}/groups/{groupId}', p),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign Role From Group Non Existent Group Not Found', async ({
    request,
  }) => {
    const p = {
      groupId: 'invalidGroupId',
      roleId: state['roleId2'] as string,
    };
    const res = await request.delete(
      buildUrl('/roles/{roleId}/groups/{groupId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Unassign Role From Group Non Existent Role Not Found', async ({
    request,
  }) => {
    const p = {
      groupId: groupIdFromState('roleId2', state) as string,
      roleId: 'invalidRoleId',
    };
    const res = await request.delete(
      buildUrl('/roles/{roleId}/groups/{groupId}', p),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Search Role Groups', async ({request}) => {
    const p = {roleId: state['roleId3'] as string};
    const group1 = groupIdFromState('roleId3', state, 1);
    const group2 = groupIdFromState('roleId3', state, 2);
    const group3 = groupIdFromState('roleId3', state, 3);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/roles/{roleId}/groups/search', p),
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

  test('Search Role Groups Role With No Assignments Returns Empty', async ({
    request,
  }) => {
    const role = await createRole(request);
    const p = {roleId: role.roleId as string};

    await expect(async () => {
      const res = await request.post(
        buildUrl('/roles/{roleId}/groups/search', p),
        {headers: jsonHeaders(), data: {}},
      );
      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 0,
        totalItemsEqualTo: 0,
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Role Groups Unauthorized', async ({request}) => {
    const p = {roleId: state['roleId3'] as string};
    const res = await request.post(
      buildUrl('/roles/{roleId}/groups/search', p),
      {headers: {}, data: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Role Groups Role Not Found', async ({request}) => {
    const p = {roleId: 'invalid-role-id'};
    const res = await request.post(
      buildUrl('/roles/{roleId}/groups/search', p),
      {headers: jsonHeaders(), data: {}},
    );
    await assertPaginatedRequest(res, {
      itemsLengthEqualTo: 0,
      totalItemsEqualTo: 0,
    });
  });
});
