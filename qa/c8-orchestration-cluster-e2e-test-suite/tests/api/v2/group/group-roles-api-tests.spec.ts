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
  assertPaginatedRequest,
} from '../../../../utils/http';
import {ROLE_EXPECTED_BODY} from '../../../../utils/beans/requestBeans';
import {
  assignRoleToGroups,
  createGroupAndStoreResponseFields,
  roleNameFromState,
  roleIdValueUsingKey,
  assertRoleInResponse,
} from '@requestHelpers';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {cleanupGroups} from '../../../../utils/groupsCleanup';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Group Roles API Tests', () => {
  const state: Record<string, unknown> = {};
  state['createdIds'] = [];

  test.beforeAll(async ({request}) => {
    await createGroupAndStoreResponseFields(request, 1, state);

    await assignRoleToGroups(request, 2, state['groupId1'] as string, state);

    (state['createdIds'] as string[]).push(
      ...(Object.values(state).filter(
        (value) => typeof value === 'string' && value.startsWith('group'),
      ) as string[]),
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupGroups(request, state['createdIds'] as string[]);
  });

  test('Search Group Roles', async ({request}) => {
    const groupIdKey: string = 'groupId1';
    const role1: string = roleIdValueUsingKey('groupId1', state, 1);
    const role2: string = roleIdValueUsingKey('groupId1', state, 2);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/groups/{groupId}/roles/search', {
          groupId: state[groupIdKey] as string,
        }),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );

      await assertPaginatedRequest(res, {
        totalItemsEqualTo: 2,
        itemsLengthEqualTo: 2,
      });
      const json = await res.json();
      assertRoleInResponse(
        json,
        ROLE_EXPECTED_BODY(groupIdKey, state, 1),
        role1,
      );
      assertRoleInResponse(
        json,
        ROLE_EXPECTED_BODY(groupIdKey, state, 2),
        role2,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Group Roles Unauthorized', async ({request}) => {
    const res = await request.post(
      buildUrl('/groups/{groupId}/roles/search', {
        groupId: state['groupId1'] as string,
      }),
      {
        headers: {},
        data: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Group Roles Not Found', async ({request}) => {
    const res = await request.post(
      buildUrl('/groups/{groupId}/roles/search', {groupId: 'invalidGroup'}),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );

    await assertPaginatedRequest(res, {
      totalItemsEqualTo: 0,
      itemsLengthEqualTo: 0,
    });
  });

  test('Search Group Roles By Role Name', async ({request}) => {
    const roleName: string = roleNameFromState('groupId1', state, 1);
    const groupIdKey: string = 'groupId1';
    const roleId: string = roleIdValueUsingKey('groupId1', state, 1);
    const body = {
      filter: {
        name: roleName,
      },
    };

    await expect(async () => {
      const res = await request.post(
        buildUrl('/groups/{groupId}/roles/search', {
          groupId: state[groupIdKey] as string,
        }),
        {
          headers: jsonHeaders(),
          data: body,
        },
      );

      await assertPaginatedRequest(res, {
        totalItemsEqualTo: 1,
        itemsLengthEqualTo: 1,
      });
      const json = await res.json();
      assertRoleInResponse(
        json,
        ROLE_EXPECTED_BODY(groupIdKey, state, 1),
        roleId,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Group Roles By Role Id', async ({request}) => {
    const roleId: string = roleIdValueUsingKey('groupId1', state, 2);
    const groupIdKey: string = 'groupId1';
    const body = {
      filter: {
        roleId: roleId,
      },
    };
    await expect(async () => {
      const res = await request.post(
        buildUrl('/groups/{groupId}/roles/search', {
          groupId: state[groupIdKey] as string,
        }),
        {
          headers: jsonHeaders(),
          data: body,
        },
      );

      await assertPaginatedRequest(res, {
        totalItemsEqualTo: 1,
        itemsLengthEqualTo: 1,
      });
      const json = await res.json();
      assertRoleInResponse(
        json,
        ROLE_EXPECTED_BODY(groupIdKey, state, 2),
        roleId,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Group Roles By Multiple Fields', async ({request}) => {
    const roleId: string = roleIdValueUsingKey('groupId1', state, 1);
    const roleName: string = roleNameFromState('groupId1', state, 1);
    const groupIdKey: string = 'groupId1';
    const body = {
      filter: {
        roleId: roleId,
        name: roleName,
      },
    };
    await expect(async () => {
      const res = await request.post(
        buildUrl('/groups/{groupId}/roles/search', {
          groupId: state[groupIdKey] as string,
        }),
        {
          headers: jsonHeaders(),
          data: body,
        },
      );

      await assertPaginatedRequest(res, {
        totalItemsEqualTo: 1,
        itemsLengthEqualTo: 1,
      });
      const json = await res.json();
      assertRoleInResponse(
        json,
        ROLE_EXPECTED_BODY(groupIdKey, state, 1),
        roleId,
      );
    }).toPass(defaultAssertionOptions);
  });
});
