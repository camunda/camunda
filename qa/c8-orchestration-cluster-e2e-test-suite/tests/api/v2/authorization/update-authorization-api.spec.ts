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
import {defaultAssertionOptions, generateUniqueId} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {createUser, grantUserResourceAuthorization, createComponentAuthorization, createRole, createGroup} from '@requestHelpers';
import {validateResponse} from '../../../../json-body-assertions';
import {
  CREATE_CUSTOM_AUTHORIZATION_BODY,
  authorizationRequiredFields,
  authorizedComponentRequiredFields,
} from '../../../../utils/beans/requestBeans';
import { create } from 'domain';
import { waitForAssertion } from 'utils/waitForAssertion';
import { sleep } from 'utils/sleep';
import { cleanupRoles } from 'utils/rolesCleanup';
import { cleanupGroups } from 'utils/groupsCleanup';

type Authorization = {
  ownerId: string;
  ownerType: string;
  resourceId: string;
  resourceType: string;
  permissionTypes: string[];
  authorizationKey?: string;
};

test.describe.parallel('Update Authorization API', () => {
    let user: {
        username: string;
        name: string;
        email: string;
        password: string;
    };
    let authorizationKeys: Map<string, string> = new Map();
    let originalUserAuthorization: Authorization;
// const uid = generateUniqueId();
    let originalRole: {
        roleId: string,
        name: string,
        description: string,
    };
    let originalRoleAuthorization: Authorization;
    let originalGroup: {
        groupId: string;
        name: string;
        description: string;
    };
    let newGroupForAuthorization: {
        groupId: string;
        name: string;
        description: string;
    };
    let originalGroupAuthorization: Authorization;
    
        
  test.beforeAll(async ({request}) => {
    await test.step('Setup - Create user for authorization tests', async () => {
      user = await createUser(request);
      console.log('Created user with username:', user.username);
    });

    await test.step('Setup - Create role for Authorization tests', async () => {
        originalRole = await createRole(request);
        console.log('Created role with roleId:', originalRole.roleId);
    });

    await test.step('Setup - Create group for Authorization tests', async () => {
        originalGroup = await createGroup(request);
        console.log('Created group with groupId:', originalGroup.groupId);

        newGroupForAuthorization = await createGroup(request);
        console.log('Created new group with groupId:', newGroupForAuthorization.groupId);
    });

    await test.step('Setup - Grant user necessary authorizations', async () => {
        const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(user.username, 'USER', '*', 'ROLE', ['READ']);
        const authorizationKey = await createComponentAuthorization(request, authorizationBody);
        authorizationKeys.set('userAuthorization', authorizationKey);
        originalUserAuthorization = authorizationBody;
    });

    await test.step('Setup - Grant created role authorization', async () => {
        const roleAuthorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(originalRole.roleId, 'ROLE', '*', 'USER', ['DELETE']);
        const roleAuthorizationKey = await createComponentAuthorization(request, roleAuthorizationBody);
        authorizationKeys.set('roleAuthorization', roleAuthorizationKey);
        originalRoleAuthorization = roleAuthorizationBody;
    });

    await test.step('Setup - Grant created group authorization', async () => {
        const groupAuthorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(originalGroup.groupId, 'GROUP', '*', 'TENANT', ['UPDATE']);
        const groupAuthorizationKey = await createComponentAuthorization(request, groupAuthorizationBody);
        authorizationKeys.set('groupAuthorization', groupAuthorizationKey);
        originalGroupAuthorization = groupAuthorizationBody;
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

    await test.step(
        'Teardown - Delete role with roleId ' +
        originalRole.roleId +
        ' created for Authorization tests',
        async () => {
        await cleanupRoles(request, [originalRole.roleId]);
        },
    );

    await test.step(
        'Teardown - Delete group with groupId ' +
        originalGroup.groupId + ' and ' + newGroupForAuthorization.groupId +
        ' created for Authorization tests',
        async () => {
        await cleanupGroups(request, [originalGroup.groupId, newGroupForAuthorization.groupId]);
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

    await test.step('Verify updated authorization', async () => {
        const expectedUserAuthorization = {...updatedUserAuthorization, authorizationKey: authorizationKeys.get('userAuthorization')};
        let getAuthRes = await request.get(buildUrl(`/authorizations/${expectedUserAuthorization.authorizationKey}`), {
            headers: jsonHeaders(),
        });
        let authBody = await getAuthRes.json();
        

        await waitForAssertion({
            assertion: async () => {
                expect(getAuthRes.status()).toBe(200);
                assertRequiredFields(authBody, authorizationRequiredFields);
                assertEqualsForKeys(authBody, expectedUserAuthorization, authorizationRequiredFields);
            },
            onFailure: async () => {
                getAuthRes = await request.get(buildUrl(`/authorizations/${expectedUserAuthorization.authorizationKey}`), {
                    headers: jsonHeaders(),
                });
                authBody = await getAuthRes.json();
            },
            maxRetries: 100,
        });
    });
  });

  test('Update Role Authorization - change resourceId - bad request', async ({request}) => {
    const updatedRoleAuthorization = {
        ...originalRoleAuthorization,
        resourceId: `${user.username}`,
    };

    await test.step('Update role authorization with changed resourceId', async () => {
        const authRes = await request.put(buildUrl(`/authorizations/${authorizationKeys.get('roleAuthorization')}`), {
            headers: jsonHeaders(),
            data: {
                ...updatedRoleAuthorization   
            },
        });
        expect(authRes.status()).toBe(204);
    });

    await test.step('Verify updated authorization', async () => {
        const expectedRoleAuthorization = {...updatedRoleAuthorization, authorizationKey: authorizationKeys.get('roleAuthorization')};
        let getAuthRes = await request.get(buildUrl(`/authorizations/${expectedRoleAuthorization.authorizationKey}`), {
            headers: jsonHeaders(),
        });
        let authBody = await getAuthRes.json();
        
        await waitForAssertion({
            assertion: async () => {
                expect(getAuthRes.status()).toBe(200);
                assertRequiredFields(authBody, authorizationRequiredFields);
                assertEqualsForKeys(authBody, expectedRoleAuthorization, authorizationRequiredFields);
            },
            onFailure: async () => {
                getAuthRes = await request.get(buildUrl(`/authorizations/${expectedRoleAuthorization.authorizationKey}`), {
                    headers: jsonHeaders(),
                });
                authBody = await getAuthRes.json();
            },
            maxRetries: 100,
        });
    });
  });

  test('Update Group Authorization - change ownerId to another group - success', async ({request}) => {
    const updatedGroupAuthorization = {
        ...originalGroupAuthorization,
        ownerId: newGroupForAuthorization.groupId,
    };

    await test.step('Update group authorization to new group ownerId', async () => {
        const authRes = await request.put(buildUrl(`/authorizations/${authorizationKeys.get('groupAuthorization')}`), {
            headers: jsonHeaders(),
            data: {
                ...updatedGroupAuthorization   
            },
        });
        expect(authRes.status()).toBe(204);
    });

    await test.step('Verify updated authorization', async () => {
        const expectedGroupAuthorization = {...updatedGroupAuthorization, authorizationKey: authorizationKeys.get('groupAuthorization')};
        let getAuthRes = await request.get(buildUrl(`/authorizations/${expectedGroupAuthorization.authorizationKey}`), {
            headers: jsonHeaders(),
        });
        let authBody = await getAuthRes.json();
        
        await waitForAssertion({
            assertion: async () => {
                expect(getAuthRes.status()).toBe(200);
                assertRequiredFields(authBody, authorizationRequiredFields);
                assertEqualsForKeys(authBody, expectedGroupAuthorization, authorizationRequiredFields);
            },
            onFailure: async () => {
                getAuthRes = await request.get(buildUrl(`/authorizations/${expectedGroupAuthorization.authorizationKey}`), {
                    headers: jsonHeaders(),
                });
                authBody = await getAuthRes.json();
            },
            maxRetries: 100,
        });
    });
  });
});