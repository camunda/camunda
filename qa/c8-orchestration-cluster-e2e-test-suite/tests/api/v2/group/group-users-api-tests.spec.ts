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
import {CREATE_GROUP_USERS_EXPECTED_BODY_USING_GROUP} from '../../../../utils/beans/requestBeans';
import {
  assignUsersToGroup,
  createGroupAndStoreResponseFields,
  userFromState,
} from '../../../../utils/requestHelpers';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

test.describe.parallel('Group Users API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createGroupAndStoreResponseFields(request, 3, state);
    await assignUsersToGroup(request, 1, state['groupId2'] as string, state);
    await assignUsersToGroup(request, 1, state['groupId3'] as string, state);
  });

  test('Assign User To Group Not Found', async ({request}) => {
    state['username'] = 'demo';
    const stateParams: Record<string, string> = {
      groupId: 'invalidGroupId',
      username: state['username'] as string,
    };
    const res = await request.put(
      buildUrl('/groups/{groupId}/users/{username}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Command 'ADD_ENTITY' rejected with code 'NOT_FOUND'`,
    );
  });

  test('Assign User To Group', async ({request}) => {
    const user = 'test-user' + generateUniqueId();
    const stateParams: Record<string, string> = {
      groupId: state['groupId1'] as string,
      username: user,
    };

    await expect(async () => {
      const res = await request.put(
        buildUrl('/groups/{groupId}/users/{username}', stateParams),
        {
          headers: jsonHeaders(),
        },
      );
      expect(res.status()).toBe(204);
    }).toPass(defaultAssertionOptions);
  });

  test('Assign Already Added User To Group Conflict', async ({request}) => {
    const stateParams: Record<string, string> = {
      groupId: state['groupId2'] as string,
      username: userFromState('groupId2', state) as string,
    };
    const res = await request.put(
      buildUrl('/groups/{groupId}/users/{username}', stateParams),
      {
        headers: jsonHeaders(),
      },
    );

    await assertConflictRequest(res);
  });

  test('Search Users For Group', async ({request}) => {
    const groupId: string = state['groupId2'] as string;
    const expectedBody = CREATE_GROUP_USERS_EXPECTED_BODY_USING_GROUP(
      groupId,
      state,
    );
    const requiredFields = Object.keys(expectedBody);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/groups/{groupId}/users/search', {groupId: groupId}),
        {headers: jsonHeaders()},
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(1);
      assertRequiredFields(json.items[0], requiredFields);
      assertEqualsForKeys(json.items[0], expectedBody, requiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Users For Group Unauthorized', async ({request}) => {
    const p = {groupId: state['groupId1'] as string};

    const res = await request.post(
      buildUrl('/groups/{groupId}/users/search', p),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Users For Group Not Found', async ({request}) => {
    const p = {groupId: 'invalidGroupId'};

    const res = await request.post(
      buildUrl('/groups/{groupId}/users/search', p),
      {headers: jsonHeaders()},
    );

    expect(res.status()).toBe(200);
    const json = await res.json();
    assertRequiredFields(json, paginatedResponseFields);
    expect(json.page.totalItems).toBe(0);
    expect(json.items.length).toBe(0);
  });

  test('Unassign User From Group', async ({request}) => {
    await test.step('Unassign User', async () => {
      const p = {
        groupId: state['groupId3'] as string,
        username: userFromState('groupId3', state) as string,
      };

      await expect(async () => {
        const res = await request.delete(
          buildUrl('/groups/{groupId}/users/{username}', p),
          {
            headers: jsonHeaders(),
          },
        );
        expect(res.status()).toBe(204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search Users After Deletion', async () => {
      const p = {groupId: state['groupId3'] as string};

      await expect(async () => {
        const res = await request.post(
          buildUrl('/groups/{groupId}/users/search', p),
          {
            headers: jsonHeaders(),
            data: {},
          },
        );
        expect(res.status()).toBe(200);
        const json = await res.json();
        assertRequiredFields(json, paginatedResponseFields);
        expect(json.page.totalItems).toBe(0);
        expect(json.items.length).toBe(0);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Unassign User From Group Unauthorized', async ({request}) => {
    const p = {
      groupId: state['groupId2'] as string,
      username: userFromState('groupId2', state) as string,
    };

    const res = await request.delete(
      buildUrl('/groups/{groupId}/users/{username}', p),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Unassign User From Group Not Found', async ({request}) => {
    const p = {
      groupId: 'invalidGroupId',
      username: userFromState('groupId2', state) as string,
    };
    const res = await request.delete(
      buildUrl('/groups/{groupId}/users/{username}', p),
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
