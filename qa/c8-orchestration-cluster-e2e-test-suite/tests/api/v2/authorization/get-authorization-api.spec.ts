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

    await test.step('Get Authorization - verify authorization can be found', async () => {
        await expect(async () => {
            const statusRes = await request.get(
              buildUrl(`/authorizations/${userAuthorizationKey}`),
              {
                headers: jsonHeaders(),
              },
            );
            await assertStatusCode(statusRes, 200);
            const authBody = await statusRes.json();
            verifyAuthorizationFields(authBody, expectedUserAuthorization);
        }).toPass(defaultAssertionOptions);
    });
  });
});