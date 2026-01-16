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
  assertBadRequest,
  assertInvalidArgument,
  assertUnauthorizedRequest,
  assertNotFoundRequest,
  assertConflictRequest,
  encode,
  assertStatusCode,
  assertRequiredFields,
  assertForbiddenRequest,
  assertEqualsForKeys,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {createUser, grantUserResourceAuthorization, createComponentAuthorization} from '@requestHelpers';
import {validateResponse} from '../../../../json-body-assertions';
import {
  CREATE_CUSTOM_AUTHORIZATION_BODY,
  authorizationRequiredFields,
  authorizedComponentRequiredFields,
} from '../../../../utils/beans/requestBeans';
import { create } from 'domain';
import { waitForAssertion } from 'utils/waitForAssertion';
import { sleep } from 'utils/sleep';

test.describe.parallel('Update Authorization API', () => {
    let user: {
        username: string;
        name: string;
        email: string;
        password: string;
    };
    let authorizationKeys: Map<string, string> = new Map();
    let originalUserAuthorization: {
        ownerId: string;
        ownerType: string;
        resourceId: string;
        resourceType: string;
        permissionTypes: string[];
    };
    
        
  test.beforeAll(async ({request}) => {
    await test.step('Setup - Create user for Authorization tests', async () => {
      user = await createUser(request);
      console.log('Created user with username:', user.username);
    });

    await test.step('Setup - Grant user necessary authorizations', async () => {
        const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(user.username, 'USER', '*', 'ROLE', ['READ']);
        const authorizationKey = await createComponentAuthorization(request, authorizationBody);
        authorizationKeys.set('userAuthorization', authorizationKey);
        originalUserAuthorization = authorizationBody;
    });
  });

  test.afterAll(async ({request}) => {
    await test.step(
      'Teardown - Delete user with username ' +
        user.username +
        ' created for Authorization tests',
      async () => {
        await cleanupUsers(request, [user.username]);
      },
    );
  });

  test('Update User Authorization - additional permissionType - success', async ({request}) => { 
    const updatedUserAuthorization = {
        ...originalUserAuthorization,
        permissionTypes: ['READ', 'UPDATE'],
    };

    await test.step('Update user authorization to add UPDATE permission', async () => {
        const authRes = await request.put(buildUrl(`/authorizations/${authorizationKeys.get('userAuthorization')}`), {
            headers: jsonHeaders(),
            data: {
                ...updatedUserAuthorization   
            },
        });
        expect(authRes.status()).toBe(204);
    });

    sleep(5000);
    await test.step('Verify updated authorization', async () => {
        let getAuthRes = await request.get(buildUrl(`/authorizations/${authorizationKeys.get('userAuthorization')}`), {
            headers: jsonHeaders(),
        });
        let authBody = await getAuthRes.json();
        const expectedAuthorization = {...updatedUserAuthorization, authorizationKey: authorizationKeys.get('userAuthorization')};

        await waitForAssertion({
            assertion: async () => {
                expect(getAuthRes.status()).toBe(200);
                assertEqualsForKeys(authBody, expectedAuthorization, authorizationRequiredFields);
            },
            onFailure: async () => {
                getAuthRes = await request.get(buildUrl(`/authorizations/${authorizationKeys.get('userAuthorization')}`), {
                    headers: jsonHeaders(),
                });
                authBody = await getAuthRes.json();
            },
            maxRetries: 100,
        });
            
        
        assertRequiredFields(authBody, authorizationRequiredFields);
        
        
    });
  });

});