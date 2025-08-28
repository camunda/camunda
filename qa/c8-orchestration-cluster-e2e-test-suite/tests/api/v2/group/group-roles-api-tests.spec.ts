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
  assertEqualsForKeys,
  paginatedResponseFields,
  assertUnauthorizedRequest,
  assertNotFoundRequest,
  assertConflictRequest,
} from '../../../../utils/http';
import {
  CREATE_GROUP_ROLE_EXPECTED_BODY_USING_GROUP,
  roleRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {
  assignRolesToGroup,
  createGroupAndStoreResponseFields,
  createRole,
  roleIdFromState,
} from '../../../../utils/requestHelpers';
import {defaultAssertionOptions} from '../../../../utils/constants';

test.describe.parallel('Group Roles API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createGroupAndStoreResponseFields(request, 3, state);
    await assignRolesToGroup(request, 1, state['groupId2'] as string, state);
    await assignRolesToGroup(request, 1, state['groupId3'] as string, state);
  });

  test('Assign Role To Group', async ({request}) => {
    const role = await createRole(request);
    const p = {
      groupId: state['groupId1'] as string,
      roleId: role.roleId as string,
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

  test('Assign Already Added Role To Group Conflict', async ({request}) => {
    const stateParams: Record<string, string> = {
      groupId: state['groupId2'] as string,
      roleId: roleIdFromState('groupId2', state) as string,
    };
    const res = await request.put(
      buildUrl('/roles/{roleId}/groups/{groupId}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );

    await assertConflictRequest(res);
  });

  test('Search Roles For Group', async ({request}) => {
    const groupId: string = state['groupId2'] as string;
    const expectedBody: Record<string, string> =
      CREATE_GROUP_ROLE_EXPECTED_BODY_USING_GROUP(groupId, state);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/groups/{groupId}/roles/search', {groupId: groupId}),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(1);
      assertRequiredFields(json.items[0], roleRequiredFields);
      assertEqualsForKeys(json.items[0], expectedBody, roleRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Roles For Group Unauthorized', async ({request}) => {
    const p = {groupId: state['groupId1'] as string};
    const res = await request.post(
      buildUrl('/groups/{groupId}/roles/search', p),
      {
        headers: {},
        data: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Roles For Group Not Found', async ({request}) => {
    const p = {groupId: 'invalidGroupId'};

    const res = await request.post(
      buildUrl('/groups/{groupId}/roles/search', p),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );

    const json = await res.json();
    assertRequiredFields(json, paginatedResponseFields);
    expect(json.page.totalItems).toBe(0);
    expect(json.items.length).toBe(0);
  });

  test('Unassign Role From Group', async ({request}) => {
    await test.step('Unassign Role From Group', async () => {
      const p = {
        groupId: state['groupId3'] as string,
        roleId: roleIdFromState('groupId3', state) as string,
      };

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

    await test.step('Search Roles For Group After Deletion', async () => {
      const p = {groupId: state['groupId3'] as string};

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
      groupId: state['groupId2'] as string,
      roleId: roleIdFromState('groupId2', state) as string,
    };
    const res = await request.delete(
      buildUrl('/roles/{roleId}/groups/{groupId}', p),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign Role From Group Not Found', async ({request}) => {
    const p = {
      groupId: 'invalidGroupId',
      roleId: roleIdFromState('groupId2', state) as string,
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
});
