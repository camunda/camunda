/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  buildUrl,
  jsonHeaders,
  assertUnauthorizedRequest,
  assertEqualsForKeys,
  encode,
  assertStatusCode,
} from '../../../utils/http';
import {validateResponse} from '../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../utils/constants';
import {
  cleanupAuthorizations,
  createComponentAuthorization,
  createGroupAndStoreResponseFields,
  createRole,
  createTenant,
  createUser,
  createUsersAndStoreResponseFields,
  expectAuthorizationCanBeFound,
  verifyAuthorizationFields,
} from '@requestHelpers';
import {
  authenticationRequiredFields,
  CREATE_COMPONENT_AUTHORIZATION,
  GET_CURRENT_USER_EXPECTED_BODY,
  TENANT_EXPECTED_BODY,
} from '../../../utils/beans/requestBeans';
import {cleanupUsers} from '../../../utils/usersCleanup';

test.describe.parallel('Authentication API Tests', () => {
  const state: Record<string, unknown> = {};
  const createdUserIds: string[] = [];

  test.beforeAll(async ({request}) => {
    await createUsersAndStoreResponseFields(request, 2, state);

    createdUserIds.push(
      ...(Object.values(state).filter(
        (value) => typeof value === 'string' && value.startsWith('user'),
      ) as string[]),
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, createdUserIds);
  });

  test('Get Current User', async ({request}) => {
    await expect(async () => {
      const res = await request.get(buildUrl('/authentication/me'), {
        headers: jsonHeaders(),
        data: {},
      });
      const expectedBody = GET_CURRENT_USER_EXPECTED_BODY(
        'demo',
        'Demo',
        'demo@example.com',
        {
          authorizedComponents: ['*'],
          tenants: [
            {
              name: 'Default',
              tenantId: '<default>',
              description: '',
            },
          ],
          roles: ['admin'],
        },
      );

      const isOracle = process.env.DATABASE_CONTAINER?.startsWith('oracle');
      const json = await res.json();

      if (isOracle) {
        json.tenants?.forEach((t: {description?: string | null}) => {
          if (t.description == null) {
            t.description = '';
          }
        });
      }

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/authentication/me',
          method: 'GET',
          status: '200',
        },
        res,
      );
      assertEqualsForKeys(json, expectedBody, authenticationRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Current User Unauthorized', async ({request}) => {
    const res = await request.get(buildUrl('/authentication/me'), {
      headers: {},
      data: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Get Current User With Group, Tenants and Authorization', async ({
    request,
  }) => {
    await test.step('Create Group and Assign User', async () => {
      await createGroupAndStoreResponseFields(request, 1, state, 'Auth');
      const stateParams: Record<string, string> = {
        groupId: state['groupIdAuth1'] as string,
        username: state['username1'] as string,
      };

      await expect(async () => {
        const res = await request.put(
          buildUrl('/groups/{groupId}/users/{username}', stateParams),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Create Role and Assign User', async () => {
      await createRole(request, state, 'Auth1');
      const p: Record<string, string> = {
        roleId: state['roleIdAuth1'] as string,
        username: state['username1'] as string,
      };

      await expect(async () => {
        const res = await request.put(
          buildUrl('/roles/{roleId}/users/{username}', p),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Create Tenant and Assign User', async () => {
      const tenant = await createTenant(request, state, '1');
      const p = {
        username: state['username1'] as string,
        tenantId: tenant.tenantId as string,
      };

      await expect(async () => {
        const res = await request.put(
          buildUrl('/tenants/{tenantId}/users/{username}', p),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Create Authorization for User', async () => {
      await createComponentAuthorization(
        request,
        CREATE_COMPONENT_AUTHORIZATION('USER', state['username1'] as string),
      );
    });

    await test.step('Get Current User', async () => {
      const expectedBody = GET_CURRENT_USER_EXPECTED_BODY(
        state['username1'] as string,
        state['name1'] as string,
        state['email1'] as string,
        {
          authorizedComponents: ['*'],
          tenants: [
            TENANT_EXPECTED_BODY(
              state['tenantName1'] as string,
              state['tenantId1'] as string,
              state['tenantDescription1'] as string,
            ),
          ],
          groups: [state['groupIdAuth1'] as string],
          roles: [state['roleIdAuth1'] as string],
        },
      );

      await expect(async () => {
        const res = await request.get(buildUrl('/authentication/me'), {
          headers: jsonHeaders(
            encode(`${state['username1']}:${state['password1']}`),
          ),
        });

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/authentication/me',
            method: 'GET',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        assertEqualsForKeys(json, expectedBody, authenticationRequiredFields);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Get Current User Without Group, Tenants and Authorization', async ({
    request,
  }) => {
    const expectedBody = GET_CURRENT_USER_EXPECTED_BODY(
      state['username2'] as string,
      state['name2'] as string,
      state['email2'] as string,
    );

    await expect(async () => {
      const res = await request.get(buildUrl('/authentication/me'), {
        headers: jsonHeaders(
          encode(`${state['username2']}:${state['password2']}`),
        ),
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/authentication/me',
          method: 'GET',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      assertEqualsForKeys(json, expectedBody, authenticationRequiredFields);
    }).toPass(defaultAssertionOptions);
  });
});

test.describe.parallel('Search Own Authorizations API Tests', () => {
  const authorizationKeysToCleanup: string[] = [];
  const usernamesToCleanup: string[] = [];
  const groupIdsToCleanup: string[] = [];
  const roleIdsToCleanup: string[] = [];

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeysToCleanup);
    await cleanupUsers(request, usernamesToCleanup);
    await Promise.allSettled([
      ...groupIdsToCleanup.map((groupId) =>
        request.delete(buildUrl('/groups/{groupId}', {groupId}), {
          headers: jsonHeaders(),
        }),
      ),
      ...roleIdsToCleanup.map((roleId) =>
        request.delete(buildUrl('/roles/{roleId}', {roleId}), {
          headers: jsonHeaders(),
        }),
      ),
    ]);
  });

  test('Search Own Authorizations Returns Direct Grant', async ({request}) => {
    const user = await createUser(request);
    usernamesToCleanup.push(user.username);

    const authorizationKey = await createComponentAuthorization(request, {
      ownerId: user.username,
      ownerType: 'USER',
      resourceId: '*',
      resourceType: 'CLUSTER_VARIABLE',
      permissionTypes: ['READ'],
    });
    authorizationKeysToCleanup.push(authorizationKey);
    await expectAuthorizationCanBeFound(request, authorizationKey);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/authentication/me/authorizations/search'),
        {
          headers: jsonHeaders(encode(`${user.username}:${user.password}`)),
          data: {},
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/authentication/me/authorizations/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const json = await res.json();
      const item = json.items.find(
        (it: {authorizationKey: string}) =>
          it.authorizationKey === authorizationKey,
      );
      expect(item).toBeDefined();
      verifyAuthorizationFields(item, {
        ownerId: user.username,
        ownerType: 'USER',
        resourceId: '*',
        resourceType: 'CLUSTER_VARIABLE',
        permissionTypes: ['READ'],
        authorizationKey,
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Own Authorizations Filtered By Resource Type Excludes Others', async ({
    request,
  }) => {
    const user = await createUser(request);
    usernamesToCleanup.push(user.username);

    const matchingKey = await createComponentAuthorization(request, {
      ownerId: user.username,
      ownerType: 'USER',
      resourceId: '*',
      resourceType: 'CLUSTER_VARIABLE',
      permissionTypes: ['READ'],
    });
    authorizationKeysToCleanup.push(matchingKey);
    const otherKey = await createComponentAuthorization(request, {
      ownerId: user.username,
      ownerType: 'USER',
      resourceId: '*',
      resourceType: 'MESSAGE',
      permissionTypes: ['READ'],
    });
    authorizationKeysToCleanup.push(otherKey);
    await expectAuthorizationCanBeFound(request, matchingKey);
    await expectAuthorizationCanBeFound(request, otherKey);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/authentication/me/authorizations/search'),
        {
          headers: jsonHeaders(encode(`${user.username}:${user.password}`)),
          data: {filter: {resourceType: 'CLUSTER_VARIABLE'}},
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/authentication/me/authorizations/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const keys = (await res.json()).items.map(
        (it: {authorizationKey: string}) => it.authorizationKey,
      );
      expect(keys).toContain(matchingKey);
      expect(keys).not.toContain(otherKey);
    }).toPass(defaultAssertionOptions);
  });

  test("Search Own Authorizations Never Leaks Another User's Grants", async ({
    request,
  }) => {
    const caller = await createUser(request);
    usernamesToCleanup.push(caller.username);
    const stranger = await createUser(request);
    usernamesToCleanup.push(stranger.username);

    // Both grants are identical apart from their owner, so the only thing that
    // can keep the stranger's out of the caller's results is owner scoping.
    const callerKey = await createComponentAuthorization(request, {
      ownerId: caller.username,
      ownerType: 'USER',
      resourceId: '*',
      resourceType: 'CLUSTER_VARIABLE',
      permissionTypes: ['READ'],
    });
    authorizationKeysToCleanup.push(callerKey);
    const strangerKey = await createComponentAuthorization(request, {
      ownerId: stranger.username,
      ownerType: 'USER',
      resourceId: '*',
      resourceType: 'CLUSTER_VARIABLE',
      permissionTypes: ['READ'],
    });
    authorizationKeysToCleanup.push(strangerKey);
    await expectAuthorizationCanBeFound(request, callerKey);
    await expectAuthorizationCanBeFound(request, strangerKey);

    await test.step('Own grant is returned, stranger grant is not', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl('/authentication/me/authorizations/search'),
          {
            headers: jsonHeaders(
              encode(`${caller.username}:${caller.password}`),
            ),
            data: {},
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/authentication/me/authorizations/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const keys = (await res.json()).items.map(
          (it: {authorizationKey: string}) => it.authorizationKey,
        );
        expect(keys).toContain(callerKey);
        expect(keys).not.toContain(strangerKey);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Filtering by the stranger as owner returns nothing', async () => {
      const res = await request.post(
        buildUrl('/authentication/me/authorizations/search'),
        {
          headers: jsonHeaders(encode(`${caller.username}:${caller.password}`)),
          data: {filter: {ownerId: stranger.username, ownerType: 'USER'}},
        },
      );
      await assertStatusCode(res, 200);
      expect((await res.json()).items).toEqual([]);
    });
  });

  test('Search Own Authorizations Returns Grants Inherited Via Group And Role', async ({
    request,
  }) => {
    const user = await createUser(request);
    usernamesToCleanup.push(user.username);

    const state: Record<string, unknown> = {};
    await createGroupAndStoreResponseFields(request, 1, state, 'Inherited');
    const groupId = state['groupIdInherited1'] as string;
    groupIdsToCleanup.push(groupId);
    await createRole(request, state, 'Inherited');
    const roleId = state['roleIdInherited'] as string;
    roleIdsToCleanup.push(roleId);

    await test.step('Assign the user to the group and the role', async () => {
      await expect(async () => {
        const res = await request.put(
          buildUrl('/groups/{groupId}/users/{username}', {
            groupId,
            username: user.username,
          }),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);

      await expect(async () => {
        const res = await request.put(
          buildUrl('/roles/{roleId}/users/{username}', {
            roleId,
            username: user.username,
          }),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);
    });

    // Neither grant names the user as owner, so they can only surface through
    // the group/role membership traversal rather than a direct owner match.
    const groupOwnedKey = await createComponentAuthorization(request, {
      ownerId: groupId,
      ownerType: 'GROUP',
      resourceId: '*',
      resourceType: 'CLUSTER_VARIABLE',
      permissionTypes: ['READ'],
    });
    authorizationKeysToCleanup.push(groupOwnedKey);
    const roleOwnedKey = await createComponentAuthorization(request, {
      ownerId: roleId,
      ownerType: 'ROLE',
      resourceId: '*',
      resourceType: 'MESSAGE',
      permissionTypes: ['READ'],
    });
    authorizationKeysToCleanup.push(roleOwnedKey);
    await expectAuthorizationCanBeFound(request, groupOwnedKey);
    await expectAuthorizationCanBeFound(request, roleOwnedKey);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/authentication/me/authorizations/search'),
        {
          headers: jsonHeaders(encode(`${user.username}:${user.password}`)),
          data: {},
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/authentication/me/authorizations/search',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const items = (await res.json()).items;
      const byKey = (key: string) =>
        items.find((it: {authorizationKey: string}) => {
          return it.authorizationKey === key;
        });

      expect(byKey(groupOwnedKey)).toBeDefined();
      verifyAuthorizationFields(byKey(groupOwnedKey), {
        ownerId: groupId,
        ownerType: 'GROUP',
        resourceId: '*',
        resourceType: 'CLUSTER_VARIABLE',
        permissionTypes: ['READ'],
        authorizationKey: groupOwnedKey,
      });

      expect(byKey(roleOwnedKey)).toBeDefined();
      verifyAuthorizationFields(byKey(roleOwnedKey), {
        ownerId: roleId,
        ownerType: 'ROLE',
        resourceId: '*',
        resourceType: 'MESSAGE',
        permissionTypes: ['READ'],
        authorizationKey: roleOwnedKey,
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Own Authorizations Filters Inherited Grants By Resource Type', async ({
    request,
  }) => {
    const user = await createUser(request);
    usernamesToCleanup.push(user.username);

    const state: Record<string, unknown> = {};
    await createGroupAndStoreResponseFields(request, 1, state, 'Filtered');
    const groupId = state['groupIdFiltered1'] as string;
    groupIdsToCleanup.push(groupId);

    await expect(async () => {
      const res = await request.put(
        buildUrl('/groups/{groupId}/users/{username}', {
          groupId,
          username: user.username,
        }),
        {headers: jsonHeaders()},
      );
      await assertStatusCode(res, 204);
    }).toPass(defaultAssertionOptions);

    const matchingKey = await createComponentAuthorization(request, {
      ownerId: groupId,
      ownerType: 'GROUP',
      resourceId: '*',
      resourceType: 'CLUSTER_VARIABLE',
      permissionTypes: ['READ'],
    });
    authorizationKeysToCleanup.push(matchingKey);
    const otherKey = await createComponentAuthorization(request, {
      ownerId: groupId,
      ownerType: 'GROUP',
      resourceId: '*',
      resourceType: 'MESSAGE',
      permissionTypes: ['READ'],
    });
    authorizationKeysToCleanup.push(otherKey);
    await expectAuthorizationCanBeFound(request, matchingKey);
    await expectAuthorizationCanBeFound(request, otherKey);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/authentication/me/authorizations/search'),
        {
          headers: jsonHeaders(encode(`${user.username}:${user.password}`)),
          data: {filter: {resourceType: 'CLUSTER_VARIABLE'}},
        },
      );
      await assertStatusCode(res, 200);
      const keys = (await res.json()).items.map(
        (it: {authorizationKey: string}) => it.authorizationKey,
      );
      expect(keys).toContain(matchingKey);
      expect(keys).not.toContain(otherKey);
    }).toPass(defaultAssertionOptions);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Search Own Authorizations Unauthorized', async ({request}) => {
    const res = await request.post(
      buildUrl('/authentication/me/authorizations/search'),
      {
        headers: {'Content-Type': 'application/json'},
        data: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });
});
