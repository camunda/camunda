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
  extractAndStoreIds,
  buildUrl,
  assertRequiredFields,
  assertEqualsForKeys,
<<<<<<< HEAD
  paginatedResponseFields,
} from '../../../../utils/http';
import {
  CREATE_NEW_GROUP,
  CREATE_NEW_ROLE,
  groupRequiredFields,
  roleRequiredFields,
} from '../../../../utils/beans/request-beans';
import {sleep} from '../../../../utils/sleep';
=======
  assertUnauthorizedRequest,
  assertPaginatedRequest,
} from '../../../../utils/http';
import {
  ROLE_EXPECTED_BODY,
  roleRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {
  assignRoleToGroups,
  createGroupAndStoreResponseFields,
  roleNameFromState,
  roleIdValueUsingKey,
  assertRoleInResponse,
} from '../../../../utils/requestHelpers';
import {defaultAssertionOptions} from '../../../../utils/constants';
>>>>>>> 4fa4510d (test: v2 role endpoints implemented)

test.describe('Group Roles API Tests', () => {
  const state: Record<string, unknown> = {};

<<<<<<< HEAD
  test('CRUD', async ({request}) => {
    await test.step('Create Group', async () => {
      const requestBody = CREATE_NEW_GROUP();
      const res = await request.post(buildUrl('/groups'), {
        headers: jsonHeaders(),
        data: requestBody,
      });
      expect(res.status()).toBe(201);
      const json = await res.json();
      assertRequiredFields(json, groupRequiredFields);
      assertEqualsForKeys(json, requestBody, groupRequiredFields);
      state['groupId'] = json.groupId;
      await sleep(5000);
    });

    await test.step('Create Role', async () => {
      const body = CREATE_NEW_ROLE();

      const res = await request.post(buildUrl('/roles'), {
        headers: jsonHeaders(),
        data: body,
      });

      expect(res.status()).toBe(201);
      await extractAndStoreIds(res, state);
    });

    await test.step('Assign Role To Group', async () => {
      await sleep(5000);
      const p = {
        groupId: state['groupId'] as string,
        roleId: state['roleId'] as string,
      };
      const res = await request.put(
        buildUrl('/roles/{roleId}/groups/{groupId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    });

    await test.step('Search Roles For Group', async () => {
      await sleep(10000);
      const p = {groupId: state['groupId'] as string};
      const expectedBody: Record<string, string> = {
        name: state['name'] as string,
        roleId: state['roleId'] as string,
        description: state['description'] as string,
      };

      const res = await request.post(
        buildUrl('/groups/{groupId}/roles/search', p),
=======
  test.beforeAll(async ({request}) => {
    await createGroupAndStoreResponseFields(request, 1, state);
    await assignRoleToGroups(request, 2, state['groupId1'] as string, state);
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
>>>>>>> 4fa4510d (test: v2 role endpoints implemented)
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
<<<<<<< HEAD
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(1);
      assertRequiredFields(json.items[0], roleRequiredFields);
      assertEqualsForKeys(json.items[0], expectedBody, roleRequiredFields);
    });

    await test.step('Search Roles For Group Unauthorized', async () => {
      const p = {groupId: state['groupId'] as string};
      const res = await request.post(
        buildUrl('/groups/{groupId}/roles/search', p),
        {
          headers: {},
          data: {},
        },
      );
      expect(res.status()).toBe(401);
    });

    await test.step('Search Roles For Group Not Found', async () => {
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

    await test.step('Unassign Role From Group', async () => {
      const p = {
        groupId: state['groupId'] as string,
        roleId: state['roleId'] as string,
      };
      const res = await request.delete(
        buildUrl('/roles/{roleId}/groups/{groupId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    });

    await test.step('Search Roles For Group After Deletion', async () => {
      await sleep(5000);
      const p = {groupId: state['groupId'] as string};

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
    });

    await test.step('Unassign Role From Group Unauthorized', async () => {
      const p = {
        groupId: state['groupId'] as string,
        roleId: state['roleId'] as string,
      };
      const res = await request.delete(
        buildUrl('/roles/{roleId}/groups/{groupId}', p),
        {
          headers: {},
        },
      );
      expect(res.status()).toBe(401);
    });

    await test.step('Unassign Role From Group Not Found', async () => {
      const p = {
        groupId: state['groupId'] as string,
        roleId: state['roleId'] as string,
      };
      const res = await request.delete(
        buildUrl('/roles/{roleId}/groups/{groupId}', p),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(404);
    });
  });
=======
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
>>>>>>> 4fa4510d (test: v2 role endpoints implemented)
});
