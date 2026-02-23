/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect, APIRequestContext} from '@playwright/test';
import {
  jsonHeaders,
  buildUrl,
  assertBadRequest,
  assertUnauthorizedRequest,
  encode,
  assertStatusCode,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {
  createUser,
  createComponentAuthorization,
  createRole,
  createGroup,
  createMappingRule,
  expectAuthorizationCanBeFound,
  verifyAuthorizationFields,
  type Authorization,
  grantUserResourceAuthorization,
} from '@requestHelpers';
import {CREATE_CUSTOM_AUTHORIZATION_BODY} from '../../../../utils/beans/requestBeans';
import {cleanupRoles} from 'utils/rolesCleanup';
import {cleanupGroups} from 'utils/groupsCleanup';
import {cleanupMappingRules} from 'utils/mappingRuleCleanup';
import {validateResponse} from 'json-body-assertions';

const AUTHORIZATION_SEARCH_ENDPOINT = '/authorizations/search';

test.describe.parallel('Search Authorization API', () => {
  const cleanups: ((request: APIRequestContext) => Promise<void>)[] = [];
  let user: {
    username: string;
    name: string;
    email: string;
    password: string;
  };
  let userAuthorizationKey: string;
  let originalUserAuthorization: Authorization = {} as Authorization;
  let originalRole: {
    roleId: string;
    name: string;
    description: string;
  };
  let roleAuthorizationKey: string;
  let userForRoleAuthorization: {
    username: string;
    name: string;
    email: string;
    password: string;
  };

  let originalMappingRule: {
    mappingRuleId: string;
    claimName: string;
    claimValue: string;
    name: string;
  };
  let originalGroup: {
    groupId: string;
    name: string;
    description: string;
  };
  let mappingRuleAuthorizationKey: string;
  let expectedUserAuthorization: Authorization = {} as Authorization;

  test.beforeAll(async ({request}) => {
    await test.step('Setup - Create user for authorization tests', async () => {
      user = await createUser(request);
      cleanups.push(async (request) => {
        await cleanupUsers(request, [user.username]);
      });

      userForRoleAuthorization = await createUser(request);
      cleanups.push(async (request) => {
        await cleanupUsers(request, [userForRoleAuthorization.username]);
      });
    });

    await test.step('Setup - Grant user necessary authorizations', async () => {
      const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
        user.username,
        'USER',
        '*',
        'ROLE',
        ['READ'],
      );
      userAuthorizationKey = await createComponentAuthorization(
        request,
        authorizationBody,
      );
      originalUserAuthorization = authorizationBody;
    });

    expectedUserAuthorization = {
      ...originalUserAuthorization,
      authorizationKey: userAuthorizationKey,
    };

    await test.step('Setup - Create role for Authorization tests', async () => {
      originalRole = await createRole(request);
      cleanups.push(async (request) => {
        await cleanupRoles(request, [originalRole.roleId]);
      });
    });

    await test.step('Setup - Grant created role authorization', async () => {
      const roleAuthorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
        originalRole.roleId,
        'ROLE',
        '*',
        'USER',
        ['DELETE'],
      );
      roleAuthorizationKey = await createComponentAuthorization(
        request,
        roleAuthorizationBody,
      );
    });

    await test.step('Setup - Create group for Authorization tests', async () => {
      originalGroup = await createGroup(request);
      cleanups.push(async (request) => {
        await cleanupGroups(request, [originalGroup.groupId]);
      });
    });

    await test.step('Setup - Create Mapping Rule for Authorization tests', async () => {
      originalMappingRule = await createMappingRule(request);
      cleanups.push(async (request) => {
        await cleanupMappingRules(request, [originalMappingRule.mappingRuleId]);
      });
    });

    await test.step('Setup - Grant created mapping rule authorization', async () => {
      const mappingRuleAuthorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
        originalMappingRule.mappingRuleId,
        'MAPPING_RULE',
        '*',
        'GROUP',
        ['CREATE'],
      );
      mappingRuleAuthorizationKey = await createComponentAuthorization(
        request,
        mappingRuleAuthorizationBody,
      );
    });

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(request, userAuthorizationKey);
      await expectAuthorizationCanBeFound(request, roleAuthorizationKey);
      await expectAuthorizationCanBeFound(request, mappingRuleAuthorizationKey);
    });
  });

  test.afterAll(async ({request}) => {
    for (const cleanup of cleanups) {
      await cleanup(request);
    }
  });

  test('Search Authorization - no filter, multiple results - 200 Success', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUTHORIZATION_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUTHORIZATION_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(3);
      expect(body.items.length).toBeGreaterThanOrEqual(3);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Authorization - results sorted by resourceType and filtered by ownerId - 200 Success', async ({
    request,
  }) => {
    const ownerIdToSearch = user.username;
    await expect(async () => {
      const res = await request.post(buildUrl(AUTHORIZATION_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 'resourceType',
              order: 'ASC',
            },
          ],
          filter: {
            ownerId: ownerIdToSearch,
          },
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUTHORIZATION_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBe(2);
      expect(body.items.length).toBe(2);
      const authorization: Authorization = body.items[0];
      verifyAuthorizationFields(authorization, expectedUserAuthorization);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Authorization - filtered by ownerId, ownerType, and resourceType - single result - 200 Success', async ({
    request,
  }) => {
    const ownerTypeToSearch = 'USER';
    const ownerIdToSearch = user.username;
    const resourceTypeToSearch = 'ROLE';

    await expect(async () => {
      const res = await request.post(buildUrl(AUTHORIZATION_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            ownerId: ownerIdToSearch,
            ownerType: ownerTypeToSearch,
            resourceType: resourceTypeToSearch,
          },
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUTHORIZATION_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toEqual(1);
      expect(body.items.length).toEqual(1);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Authorization - filtered by ownerId and resourceIds - multiple results - 200 Success', async ({
    request,
  }) => {
    const ownerIdToSearch = 'admin';
    const resourceIdsToSearch = '*';

    await expect(async () => {
      const res = await request.post(buildUrl(AUTHORIZATION_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            ownerId: ownerIdToSearch,
            resourceIds: [resourceIdsToSearch],
          },
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUTHORIZATION_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(15);
      expect(body.items.length).toBeGreaterThanOrEqual(15);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Authorization - no results - 200 Success', async ({request}) => {
    const ownerIdToSearch = 'non.existent.user';
    await expect(async () => {
      const res = await request.post(buildUrl(AUTHORIZATION_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            ownerId: ownerIdToSearch,
          },
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUTHORIZATION_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toEqual(0);
      expect(body.items.length).toEqual(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Authorization - invalid sort field - 400 Bad Request', async ({
    request,
  }) => {
    const invalidSortField = 'invalidField';
    await expect(async () => {
      const res = await request.post(buildUrl(AUTHORIZATION_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: invalidSortField,
              order: 'ASC',
            },
          ],
        },
      });
      await assertBadRequest(
        res,
        "Unexpected value 'invalidField' for enum field 'field'. Use any of the following values: [ownerId, ownerType, resourceId, resourcePropertyName, resourceType]",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Authorization - invalid filter field - 400 Bad Request', async ({
    request,
  }) => {
    const invalidFilterField = 'meow';
    await expect(async () => {
      const res = await request.post(buildUrl(AUTHORIZATION_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            [invalidFilterField]: 'someValue',
          },
        },
      });
      await assertBadRequest(
        res,
        `Request property [filter.${invalidFilterField}] cannot be parsed`,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Authorization - Unauthorized - 401 Unauthorized', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUTHORIZATION_SEARCH_ENDPOINT), {
        // No auth headers
        data: {},
      });
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Authorization - Returns empty results for user without permission - 200 Success', async ({
    request,
  }) => {
    let userWithResourcesAuthorizationToSendRequest: {
      username: string;
      name: string;
      email: string;
      password: string;
    } = {} as {
      username: string;
      name: string;
      email: string;
      password: string;
    };
    let resourceAuthorizationKey: {authorizationKey: string};

    await test.step('Setup - Create user for authorization tests', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      resourceAuthorizationKey = await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
      cleanups.push(async (request) => {
        await cleanupUsers(request, [
          userWithResourcesAuthorizationToSendRequest.username,
        ]);
      });
    });

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(
        request,
        resourceAuthorizationKey.authorizationKey,
      );
    });

    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );

    await test.step('Attempt to search authorizations without proper permission', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(AUTHORIZATION_SEARCH_ENDPOINT),
          {
            headers: jsonHeaders(token), // overrides default demo:demo
            data: {},
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: AUTHORIZATION_SEARCH_ENDPOINT,
            method: 'POST',
            status: '200',
          },
          res,
        );
        const body = await res.json();
        expect(body.page.totalItems).toEqual(0);
        expect(body.items.length).toEqual(0);
      }).toPass(defaultAssertionOptions);
    });
  });

  // Skiped due to bug 39372: https://github.com/camunda/camunda/issues/39372
  test.skip('Search Authorization - Negative pagination values (known bug) - 200 instead of 400', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUTHORIZATION_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          page: {from: -1, limit: -1},
        },
      });
      await assertBadRequest(res, /page\.(from|limit)/i);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Authorization - Pagination 0', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl(AUTHORIZATION_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          page: {from: 0, limit: 0},
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: AUTHORIZATION_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(35);
      expect(body.items.length).toEqual(0);
    }).toPass(defaultAssertionOptions);
  });
});
