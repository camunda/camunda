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
  assertNotFoundRequest,
  encode,
  assertForbiddenRequest,
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
  expectAuthorizationCanNotBeFound,
  grantUserResourceAuthorization,
} from '@requestHelpers';
import {CREATE_CUSTOM_AUTHORIZATION_BODY} from '../../../../utils/beans/requestBeans';
import {cleanupRoles} from 'utils/rolesCleanup';
import {cleanupGroups} from 'utils/groupsCleanup';
import {cleanupMappingRules} from 'utils/mappingRuleCleanup';

test.describe.parallel('Delete Authorization API', () => {
  const cleanups: ((request: APIRequestContext) => Promise<void>)[] = [];

  test.afterAll(async ({request}) => {
    for (const cleanup of cleanups) {
      await cleanup(request);
    }
  });

  test('Delete User Authorization - Success 204', async ({request}) => {
    let user: {
      username: string;
      name: string;
      email: string;
      password: string;
    };
    let userAuthorizationKey: string;
    await test.step('Setup - Create user for authorization tests', async () => {
      user = await createUser(request);
      cleanups.push(async (request) => {
        await cleanupUsers(request, [user.username]);
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
    });

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(request, userAuthorizationKey);
    });

    await test.step('Delete authorization', async () => {
      const deleteRes = await request.delete(
        buildUrl(`/authorizations/${userAuthorizationKey}`),
        {
          headers: jsonHeaders(),
        },
      );
      expect(deleteRes.status()).toBe(204);
    });

    await test.step('Verify authorization is deleted', async () => {
      await expectAuthorizationCanNotBeFound(request, userAuthorizationKey);
    });
  });

  test('Delete Role Authorization - Success 204', async ({request}) => {
    let originalRole: {
      roleId: string;
      name: string;
      description: string;
    };
    let roleAuthorizationKey: string;
    let user: {
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

    await test.step('Setup - Create role for Authorization tests', async () => {
      originalRole = await createRole(request);
      cleanups.push(async (request) => {
        await cleanupRoles(request, [originalRole.roleId]);
      });
    });

    await test.step('Setup - Create user for authorization tests', async () => {
      user = await createUser(request);
      cleanups.push(async (request) => {
        await cleanupUsers(request, [user.username]);
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

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(request, roleAuthorizationKey);
    });

    await test.step('Delete authorization', async () => {
      const deleteRes = await request.delete(
        buildUrl(`/authorizations/${roleAuthorizationKey}`),
        {
          headers: jsonHeaders(),
        },
      );
      expect(deleteRes.status()).toBe(204);
    });

    await test.step('Verify authorization is deleted', async () => {
      await expectAuthorizationCanNotBeFound(request, roleAuthorizationKey);
    });
  });

  test('Delete Mapping Rule Authorization - Success 204', async ({request}) => {
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
    } = {} as {
      groupId: string;
      name: string;
      description: string;
    };
    let mappingRuleAuthorizationKey: string;

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
      await expectAuthorizationCanBeFound(request, mappingRuleAuthorizationKey);
    });

    await test.step('Delete authorization', async () => {
      const deleteRes = await request.delete(
        buildUrl(`/authorizations/${mappingRuleAuthorizationKey}`),
        {
          headers: jsonHeaders(),
        },
      );
      expect(deleteRes.status()).toBe(204);
    });

    await test.step('Verify authorization is deleted', async () => {
      await expectAuthorizationCanNotBeFound(
        request,
        mappingRuleAuthorizationKey,
      );
    });
  });

  test('Delete Authorization - second delete attempt - Not Found 404', async ({
    request,
  }) => {
    let user: {
      username: string;
      name: string;
      email: string;
      password: string;
    };
    let userAuthorizationKey: string;
    await test.step('Setup - Create user for authorization tests', async () => {
      user = await createUser(request);
      cleanups.push(async (request) => {
        await cleanupUsers(request, [user.username]);
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
    });

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(request, userAuthorizationKey);
    });

    await test.step('Delete authorization', async () => {
      const deleteRes = await request.delete(
        buildUrl(`/authorizations/${userAuthorizationKey}`),
        {
          headers: jsonHeaders(),
        },
      );
      expect(deleteRes.status()).toBe(204);
    });

    await test.step('Verify authorization is deleted', async () => {
      await expectAuthorizationCanNotBeFound(request, userAuthorizationKey);
    });

    await test.step('Attempt to delete already deleted authorization', async () => {
      const deleteRes = await request.delete(
        buildUrl(`/authorizations/${userAuthorizationKey}`),
        {
          headers: jsonHeaders(),
        },
      );
      await assertNotFoundRequest(
        deleteRes,
        `Command 'DELETE' rejected with code 'NOT_FOUND': Expected to delete authorization with key ${userAuthorizationKey}, but an authorization with this key does not exist`,
      );
    });
  });

  test('Delete Authorization - Unauthorized Request - 401', async ({
    request,
  }) => {
    const deleteRes = await request.delete(
      buildUrl(`/authorizations/anyAuthorizationKey`),
      {
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
    await assertUnauthorizedRequest(deleteRes);
  });

  test('Delete Authorization - Invalid Authorization Key - Bad Request 400', async ({
    request,
  }) => {
    const invalidAuthorizationKey = 'meow';
    const deleteRes = await request.delete(
      buildUrl(`/authorizations/${invalidAuthorizationKey}`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertBadRequest(
      deleteRes,
      `Failed to convert 'authorizationKey' with value: '${invalidAuthorizationKey}'`,
    );
  });

  test('Delete Authorization - 403 Forbidden', async ({request}) => {
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
    let user: {
      username: string;
      name: string;
      email: string;
      password: string;
    };
    let userAuthorizationKey: string = '';

    await test.step('Setup - Create test user with Resource Authorization and user for granting Authorization', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
      cleanups.push(async (request) => {
        await cleanupUsers(request, [
          userWithResourcesAuthorizationToSendRequest.username,
        ]);
      });

      user = await createUser(request);
      cleanups.push(async (request) => {
        await cleanupUsers(request, [user.username]);
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
    });

    await test.step('Delete Authorization and assert results', async () => {
      const token = encode(
        `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
      );

      await expect(async () => {
        const deleteRes = await request.delete(
          buildUrl(`/authorizations/${userAuthorizationKey}`),
          {
            headers: jsonHeaders(token), // overrides default demo:demo
          },
        );
        await assertForbiddenRequest(
          deleteRes,
          "Command 'DELETE' rejected with code 'FORBIDDEN': Insufficient permissions to perform operation 'DELETE' on resource 'AUTHORIZATION'",
        );
      }).toPass(defaultAssertionOptions);
    });
  });
});
