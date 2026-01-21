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
  assertInvalidArgument,
  assertUnauthorizedRequest,
  assertNotFoundRequest,
  encode,
  assertStatusCode,
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
  verifyAuthorizationFields,
  type Authorization,
  grantUserResourceAuthorization,
} from '@requestHelpers';
import {CREATE_CUSTOM_AUTHORIZATION_BODY} from '../../../../utils/beans/requestBeans';
import {waitForAssertion} from 'utils/waitForAssertion';
import {cleanupRoles} from 'utils/rolesCleanup';
import {cleanupGroups} from 'utils/groupsCleanup';
import {cleanupMappingRules} from 'utils/mappingRuleCleanup';
import {sleep} from 'utils/sleep';

test.describe.parallel('Get Authorization API', () => {
  const cleanups: ((request: APIRequestContext) => Promise<void>)[] = [];

  test.afterAll(async ({request}) => {
    for (const cleanup of cleanups) {
      await cleanup(request);
    }
  });

  test('Get existing Authorization - success', async ({request}) => {
    let user: {
      username: string;
      name: string;
      email: string;
      password: string;
    };
    let userAuthorizationKey: string = '';
    let originalUserAuthorization: Authorization = {} as Authorization;
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
      originalUserAuthorization = authorizationBody;
    });

    const expectedUserAuthorization = {
      ...originalUserAuthorization,
        authorizationKey: userAuthorizationKey,
    };

    await test.step('Get Authorization and assert results', async () => {
        await expect(async () => {
            const res = await request.get(
              buildUrl(`/authorizations/${userAuthorizationKey}`),
              {
                headers: jsonHeaders(),
              },
            );
            await assertStatusCode(res, 200);
            const authBody = await res.json();
            verifyAuthorizationFields(authBody, expectedUserAuthorization);
        }).toPass(defaultAssertionOptions);
    });
  });

  test('Get existing Authorization - not found', async ({request}) => {
    const nonExistentAuthorizationKey = '9999999999999999';
    await test.step('Get Authorization and assert results', async () => {
        await expect(async () => {
            const res = await request.get(
              buildUrl(`/authorizations/${nonExistentAuthorizationKey}`),
              {
                headers: jsonHeaders(),
              },
            );
            await assertNotFoundRequest(
              res,
              `Authorization with key '${nonExistentAuthorizationKey}' not found`,
            );
        }).toPass(defaultAssertionOptions);
    });
  });

  test('Get existing Authorization - unauthorized', async ({request}) => {
    let user: {
      username: string;
      name: string;
      email: string;
      password: string;
    };
    let userAuthorizationKey: string = '';
    let originalUserAuthorization: Authorization = {} as Authorization;
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
      originalUserAuthorization = authorizationBody;
    });

    const expectedUserAuthorization = {
      ...originalUserAuthorization,
        authorizationKey: userAuthorizationKey,
    };

    await test.step('Get Authorization without authorization header', async () => {
        await expect(async () => {
            const res = await request.get(
              buildUrl(`/authorizations/${userAuthorizationKey}`),
              {
                headers: {
                    'Content-Type': 'application/json',
                },
              },
            );
            await assertUnauthorizedRequest(res);
        }).toPass(defaultAssertionOptions);
    });
  });

  test('Get existing Authorization - forbidden', async ({request}) => {
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
    let originalUserAuthorization: Authorization = {} as Authorization;

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
      originalUserAuthorization = authorizationBody;
    });

    await test.step('Get Authorization and assert results', async () => {
        const token = encode(
        `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
      );
      
    await expect(async () => {
        const authRes = await request.get(
            buildUrl(`/authorizations/${userAuthorizationKey}`),
            {
            headers: jsonHeaders(token), // overrides default demo:demo
            data: {},
            },
        );
        await assertForbiddenRequest(
            authRes,
            "Unauthorized to perform operation 'READ' on resource 'AUTHORIZATION'",
        );
    }).toPass(defaultAssertionOptions);
    });
  });
});