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
  verifyAuthorizationFields,
  type Authorization,
  grantUserResourceAuthorization,
} from '@requestHelpers';
import {CREATE_CUSTOM_AUTHORIZATION_BODY} from '../../../../utils/beans/requestBeans';
import {validateResponse} from 'json-body-assertions';

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
        await validateResponse(
          {
            path: '/authorizations/{authorizationKey}',
            method: 'GET',
            status: '200',
          },
          res,
        );
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

  test('Get existing Authorization - 403 Forbidden', async ({request}) => {
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

    await test.step('Get Authorization and assert results', async () => {
      const token = encode(
        `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
      );

      await expect(async () => {
        const authRes = await request.get(
          buildUrl(`/authorizations/${userAuthorizationKey}`),
          {
            headers: jsonHeaders(token), // overrides default demo:demo
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
