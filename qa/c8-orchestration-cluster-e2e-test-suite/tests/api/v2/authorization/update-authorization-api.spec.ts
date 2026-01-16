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
  assertConflictRequest,
  encode,
  assertStatusCode,
  assertRequiredFields,
  assertForbiddenRequest,
  assertEqualsForKeys,
} from '../../../../utils/http';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {
  createUser,
  grantUserResourceAuthorization,
  createComponentAuthorization,
  createRole,
  createGroup,
  createMappingRule,
  expectAuthorizationCanBeFound,
  verifyAuthorizationFields,
} from '@requestHelpers';
import {validateResponse} from '../../../../json-body-assertions';
import {CREATE_CUSTOM_AUTHORIZATION_BODY} from '../../../../utils/beans/requestBeans';
import {waitForAssertion} from 'utils/waitForAssertion';
import {cleanupRoles} from 'utils/rolesCleanup';
import {cleanupGroups} from 'utils/groupsCleanup';
import {cleanupMappingRules} from 'utils/mappingRuleCleanup';
import {sleep} from 'utils/sleep';

type Authorization = {
  ownerId: string;
  ownerType: string;
  resourceId: string;
  resourceType: string;
  permissionTypes: string[];
  authorizationKey?: string;
};

test.describe.parallel('Update Authorization API', () => {
  const cleanups: ((request: APIRequestContext) => Promise<void>)[] = [];
  let authorizationKeys: Map<string, string> = new Map();

  test.afterAll(async ({request}) => {
    for (const cleanup of cleanups) {
      await cleanup(request);
    }
  });

  test('Update User Authorization - additional permissionType - success', async ({
    request,
  }) => {
    let user: {
      username: string;
      name: string;
      email: string;
      password: string;
    };
    let originalUserAuthorization: Authorization = {} as Authorization;
    await test.step('Setup - Create user for authorization tests', async () => {
      user = await createUser(request);
      console.log('Created user with username:', user.username);
      cleanups.push(async (request) => {
        console.log('>>>>>>>> Deleting user with username:', user.username);
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
      const authorizationKey = await createComponentAuthorization(
        request,
        authorizationBody,
      );
      authorizationKeys.set('userAuthorization', authorizationKey);
      originalUserAuthorization = authorizationBody;
    });

    const updatedUserAuthorization = {
      ...originalUserAuthorization,
      permissionTypes: ['READ', 'UPDATE'],
    };

    await test.step('Update user authorization to add UPDATE permission', async () => {
      const authRes = await request.put(
        buildUrl(
          `/authorizations/${authorizationKeys.get('userAuthorization')}`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            ...updatedUserAuthorization,
          },
        },
      );
      expect(authRes.status()).toBe(204);
    });

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(
        request,
        authorizationKeys.get('userAuthorization') as string,
      );
    });

    await test.step('Verify updated authorization', async () => {
      const expectedUserAuthorization = {
        ...updatedUserAuthorization,
        authorizationKey: authorizationKeys.get('userAuthorization'),
      };

      await expect(async () => {
        const getAuthRes = await request.get(
          buildUrl(
            `/authorizations/${expectedUserAuthorization.authorizationKey}`,
          ),
          {
            headers: jsonHeaders(),
          },
        );
        expect(getAuthRes.status()).toBe(200);

        const authBody = await getAuthRes.json();
        verifyAuthorizationFields(authBody, expectedUserAuthorization);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Update Role Authorization - change resourceId - success', async ({
    request,
  }) => {
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
    let originalRole: {
      roleId: string;
      name: string;
      description: string;
    };
    let originalRoleAuthorization: Authorization = {} as Authorization;

    await test.step('Setup - Create role for Authorization tests', async () => {
      originalRole = await createRole(request);
      console.log('Created role with roleId:', originalRole.roleId);
      cleanups.push(async (request) => {
        console.log('>>>>>>>> Deleting role with roleId:', originalRole.roleId);
        await cleanupRoles(request, [originalRole.roleId]);
      });
    });

    await test.step('Setup - Create user for authorization tests', async () => {
      user = await createUser(request);
      console.log('Created user with username:', user.username);
      cleanups.push(async (request) => {
        console.log('>>>>>>>> Deleting user with username:', user.username);
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
      const roleAuthorizationKey = await createComponentAuthorization(
        request,
        roleAuthorizationBody,
      );
      authorizationKeys.set('roleAuthorization', roleAuthorizationKey);
      originalRoleAuthorization = roleAuthorizationBody;
    });

    const updatedRoleAuthorization = {
      ...originalRoleAuthorization,
      resourceId: `${user.username}`,
    };

    await test.step('Update role authorization with changed resourceId', async () => {
      const authRes = await request.put(
        buildUrl(
          `/authorizations/${authorizationKeys.get('roleAuthorization')}`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            ...updatedRoleAuthorization,
          },
        },
      );
      expect(authRes.status()).toBe(204);
    });

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(
        request,
        authorizationKeys.get('roleAuthorization') as string,
      );
    });

    await test.step('Verify updated authorization', async () => {
      const expectedRoleAuthorization = {
        ...updatedRoleAuthorization,
        authorizationKey: authorizationKeys.get('roleAuthorization'),
      };
      await expect(async () => {
        const getAuthRes = await request.get(
          buildUrl(
            `/authorizations/${expectedRoleAuthorization.authorizationKey}`,
          ),
          {
            headers: jsonHeaders(),
          },
        );
        expect(getAuthRes.status()).toBe(200);

        const authBody = await getAuthRes.json();
        verifyAuthorizationFields(authBody, expectedRoleAuthorization);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Update Group Authorization - change ownerId to another group - success', async ({
    request,
  }) => {
    let originalGroup: {
      groupId: string;
      name: string;
      description: string;
    };
    let newGroupForAuthorization: {
      groupId: string;
      name: string;
      description: string;
    } = {} as {
      groupId: string;
      name: string;
      description: string;
    };
    let originalGroupAuthorization: Authorization = {} as Authorization;

    await test.step('Setup - Create group for Authorization tests', async () => {
      originalGroup = await createGroup(request);
      console.log('Created group with groupId:', originalGroup.groupId);
      cleanups.push(async (request) => {
        console.log(
          '>>>>>>>> Deleting group with groupId:',
          originalGroup.groupId,
        );
        await cleanupGroups(request, [originalGroup.groupId]);
      });

      newGroupForAuthorization = await createGroup(request);
      console.log(
        'Created new group with groupId:',
        newGroupForAuthorization.groupId,
      );
      cleanups.push(async (request) => {
        console.log(
          '>>>>>>>> Deleting group with groupId:',
          newGroupForAuthorization.groupId,
        );
        await cleanupGroups(request, [newGroupForAuthorization.groupId]);
      });
    });

    await test.step('Setup - Grant created group authorization', async () => {
      const groupAuthorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
        originalGroup.groupId,
        'GROUP',
        '*',
        'TENANT',
        ['UPDATE'],
      );
      const groupAuthorizationKey = await createComponentAuthorization(
        request,
        groupAuthorizationBody,
      );
      authorizationKeys.set('groupAuthorization', groupAuthorizationKey);
      originalGroupAuthorization = groupAuthorizationBody;
    });

    const updatedGroupAuthorization = {
      ...originalGroupAuthorization,
      ownerId: newGroupForAuthorization.groupId,
    };

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(
        request,
        authorizationKeys.get('groupAuthorization') as string,
      );
    });

    await test.step('Update group authorization to new group ownerId', async () => {
      const authRes = await request.put(
        buildUrl(
          `/authorizations/${authorizationKeys.get('groupAuthorization')}`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            ...updatedGroupAuthorization,
          },
        },
      );
      expect(authRes.status()).toBe(204);
    });

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(
        request,
        authorizationKeys.get('groupAuthorization') as string,
      );
    });

    await test.step('Verify updated authorization', async () => {
      const expectedGroupAuthorization = {
        ...updatedGroupAuthorization,
        authorizationKey: authorizationKeys.get('groupAuthorization'),
      };
      await waitForAssertion({
        assertion: async () => {
          const getAuthRes = await request.get(
            buildUrl(
              `/authorizations/${expectedGroupAuthorization.authorizationKey}`,
            ),
            {
              headers: jsonHeaders(),
            },
          );
          expect(getAuthRes.status()).toBe(200);

          const authBody = await getAuthRes.json();
          await verifyAuthorizationFields(authBody, expectedGroupAuthorization);
        },
        onFailure: async () => {
          await sleep(1000);
        },
        maxRetries: 100,
      });
    });
  });

  test('Update Authorization - change ownerId, resourceId and permissionType - success', async ({
    request,
  }) => {
    let originalMappingRule: {
      mappingRuleId: string;
      claimName: string;
      claimValue: string;
      name: string;
    };
    let originalMappingRuleAuthorization: Authorization = {} as Authorization;
    let newMappingRuleForAuthorization: {
      mappingRuleId: string;
      claimName: string;
      claimValue: string;
      name: string;
    } = {} as {
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

    await test.step('Setup - Create group for Authorization tests', async () => {
      originalGroup = await createGroup(request);
      console.log('Created group with groupId:', originalGroup.groupId);
      cleanups.push(async (request) => {
        console.log(
          '>>>>>>>> Deleting group with groupId:',
          originalGroup.groupId,
        );
        await cleanupGroups(request, [originalGroup.groupId]);
      });
    });

    await test.step('Setup - Create Mapping Rule for Authorization tests', async () => {
      originalMappingRule = await createMappingRule(request);
      console.log(
        'Created Mapping Rule with mappingRuleId:',
        originalMappingRule.mappingRuleId,
      );
      cleanups.push(async (request) => {
        console.log(
          '>>>>>>>> Deleting Mapping Rule with mappingRuleId:',
          originalMappingRule.mappingRuleId,
        );
        await cleanupMappingRules(request, [originalMappingRule.mappingRuleId]);
      });

      newMappingRuleForAuthorization = await createMappingRule(request);
      console.log(
        'Created new Mapping Rule with mappingRuleId:',
        newMappingRuleForAuthorization.mappingRuleId,
      );
      cleanups.push(async (request) => {
        console.log(
          '>>>>>>>> Deleting Mapping Rule with mappingRuleId:',
          newMappingRuleForAuthorization.mappingRuleId,
        );
        await cleanupMappingRules(request, [
          newMappingRuleForAuthorization.mappingRuleId,
        ]);
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
      const mappingRuleAuthorizationKey = await createComponentAuthorization(
        request,
        mappingRuleAuthorizationBody,
      );
      authorizationKeys.set(
        'mappingRuleAuthorization',
        mappingRuleAuthorizationKey,
      );
      originalMappingRuleAuthorization = mappingRuleAuthorizationBody;
    });

    const updatedMappingRuleAuthorization = {
      ...originalMappingRuleAuthorization,
      ownerId: newMappingRuleForAuthorization.mappingRuleId,
      resourceId: `${originalGroup.groupId}`,
      permissionTypes: ['UPDATE', 'READ'],
    };
    await test.step('Update mapping rule authorization with new ownerId, resourceId and permissionTypes', async () => {
      const authRes = await request.put(
        buildUrl(
          `/authorizations/${authorizationKeys.get('mappingRuleAuthorization')}`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            ...updatedMappingRuleAuthorization,
          },
        },
      );
      expect(authRes.status()).toBe(204);
    });

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(
        request,
        authorizationKeys.get('mappingRuleAuthorization') as string,
      );
    });

    await test.step('Verify updated authorization', async () => {
      const expectedMappingRuleAuthorization = {
        ...updatedMappingRuleAuthorization,
        authorizationKey: authorizationKeys.get('mappingRuleAuthorization'),
      };
      await expect(async () => {
        const getAuthRes = await request.get(
          buildUrl(
            `/authorizations/${expectedMappingRuleAuthorization.authorizationKey}`,
          ),
          {
            headers: jsonHeaders(),
          },
        );
        expect(getAuthRes.status()).toBe(200);

        const authBody = await getAuthRes.json();
        verifyAuthorizationFields(authBody, expectedMappingRuleAuthorization);
      }).toPass(defaultAssertionOptions);
    });
  });
});
