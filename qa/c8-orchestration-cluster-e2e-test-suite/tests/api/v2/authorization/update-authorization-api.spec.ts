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

test.describe.parallel('Update Authorization API', () => {
  const cleanups: ((request: APIRequestContext) => Promise<void>)[] = [];

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
    let userAuthorizationKey: string;
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

    const updatedUserAuthorization = {
      ...originalUserAuthorization,
      permissionTypes: ['READ', 'UPDATE'],
    };

    await test.step('Update user authorization to add UPDATE permission', async () => {
      const authRes = await request.put(
        buildUrl(`/authorizations/${userAuthorizationKey}`),
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
      await expectAuthorizationCanBeFound(request, userAuthorizationKey);
    });

    await test.step('Verify updated authorization', async () => {
      const expectedUserAuthorization = {
        ...updatedUserAuthorization,
        authorizationKey: userAuthorizationKey,
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
    let roleAuthorizationKey: string;

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
      originalRoleAuthorization = roleAuthorizationBody;
    });

    const updatedRoleAuthorization = {
      ...originalRoleAuthorization,
      resourceId: `${user.username}`,
    };

    await test.step('Update role authorization with changed resourceId', async () => {
      const authRes = await request.put(
        buildUrl(`/authorizations/${roleAuthorizationKey}`),
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
      await expectAuthorizationCanBeFound(request, roleAuthorizationKey);
    });

    await test.step('Verify updated authorization', async () => {
      const expectedRoleAuthorization = {
        ...updatedRoleAuthorization,
        authorizationKey: roleAuthorizationKey,
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
    let groupAuthorizationKey: string;

    await test.step('Setup - Create group for Authorization tests', async () => {
      originalGroup = await createGroup(request);
      cleanups.push(async (request) => {
        await cleanupGroups(request, [originalGroup.groupId]);
      });

      newGroupForAuthorization = await createGroup(request);
      cleanups.push(async (request) => {
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
      groupAuthorizationKey = await createComponentAuthorization(
        request,
        groupAuthorizationBody,
      );
      originalGroupAuthorization = groupAuthorizationBody;
    });

    const updatedGroupAuthorization = {
      ...originalGroupAuthorization,
      ownerId: newGroupForAuthorization.groupId,
    };

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(request, groupAuthorizationKey);
    });

    await test.step('Update group authorization to new group ownerId', async () => {
      const authRes = await request.put(
        buildUrl(`/authorizations/${groupAuthorizationKey}`),
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
      await expectAuthorizationCanBeFound(request, groupAuthorizationKey);
    });

    await test.step('Verify updated authorization', async () => {
      const expectedGroupAuthorization = {
        ...updatedGroupAuthorization,
        authorizationKey: groupAuthorizationKey,
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
          verifyAuthorizationFields(authBody, expectedGroupAuthorization);
        },
        onFailure: async () => {
          await sleep(1000);
        },
        maxRetries: 100,
      });
    });
  });

  test('Update Mapping Rule Authorization - change ownerId, resourceId and permissionType - success', async ({
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

      newMappingRuleForAuthorization = await createMappingRule(request);
      cleanups.push(async (request) => {
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
      mappingRuleAuthorizationKey = await createComponentAuthorization(
        request,
        mappingRuleAuthorizationBody,
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
        buildUrl(`/authorizations/${mappingRuleAuthorizationKey}`),
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
      await expectAuthorizationCanBeFound(request, mappingRuleAuthorizationKey);
    });

    await test.step('Verify updated authorization', async () => {
      const expectedMappingRuleAuthorization = {
        ...updatedMappingRuleAuthorization,
        authorizationKey: mappingRuleAuthorizationKey,
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

  test('Update Role Authorization - same authorization - success', async ({
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
    let roleAuthorizationKey: string;

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
      originalRoleAuthorization = roleAuthorizationBody;
    });

    await test.step('Update role authorization with the same authorization', async () => {
      const authRes = await request.put(
        buildUrl(`/authorizations/${roleAuthorizationKey}`),
        {
          headers: jsonHeaders(),
          data: {
            ...originalRoleAuthorization,
          },
        },
      );
      expect(authRes.status()).toBe(204);
    });

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(request, roleAuthorizationKey);
    });

    await test.step('Verify updated authorization', async () => {
      const expectedRoleAuthorization = {
        ...originalRoleAuthorization,
        authorizationKey: roleAuthorizationKey,
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

  test('Update User Authorization - empty requestBody - 400 invalid argument', async ({
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
      cleanups.push(async (request) => {
        await cleanupUsers(request, [user.username]);
      });
    });
    let userAuthorizationKey: string;

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

    await test.step('Update user authorization with empty request body', async () => {
      const authRes = await request.put(
        buildUrl(`/authorizations/${userAuthorizationKey}`),
        {
          headers: jsonHeaders(),
          data: {
            // Intentionally left empty to test behavior
          },
        },
      );
      await assertBadRequest(
        authRes,
        'At least one of [resourceId, resourcePropertyName] is required',
      );
    });

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(request, userAuthorizationKey);
    });

    await test.step('Verify updated authorization', async () => {
      const expectedUserAuthorization = {
        ...originalUserAuthorization,
        authorizationKey: userAuthorizationKey,
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

  test('Update User Authorization - Unauthorized', async ({request}) => {
    let user: {
      username: string;
      name: string;
      email: string;
      password: string;
    };
    let userAuthorizationKey: string;
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

    const updatedUserAuthorization = {
      ...originalUserAuthorization,
      permissionTypes: ['READ', 'UPDATE'],
    };

    await test.step('Update user authorization to add UPDATE permission', async () => {
      await expect(async () => {
        const authRes = await request.put(
          buildUrl(`/authorizations/${userAuthorizationKey}`),
          {
            headers: {
              'Content-Type': 'application/json',
            },
            data: {
              ...updatedUserAuthorization,
            },
          },
        );
        await assertUnauthorizedRequest(authRes);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Update User Authorization - authorizationKey was not found - 404 Not Found', async ({
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
    let notExistingUserAuthorizationKey = '9999999999999999';
    let originalUserAuthorization: Authorization = {} as Authorization;
    await test.step('Setup - Create user for authorization tests', async () => {
      user = await createUser(request);
      cleanups.push(async (request) => {
        await cleanupUsers(request, [user.username]);
      });
    });

    originalUserAuthorization = CREATE_CUSTOM_AUTHORIZATION_BODY(
      user.username,
      'USER',
      '*',
      'ROLE',
      ['READ'],
    );

    const updatedUserAuthorization = {
      ...originalUserAuthorization,
    };

    await test.step('Attempt to update non-existent authorization', async () => {
      await expect(async () => {
        const authRes = await request.put(
          buildUrl(`/authorizations/${notExistingUserAuthorizationKey}`),
          {
            headers: jsonHeaders(),
            data: {
              ...updatedUserAuthorization,
            },
          },
        );
        await assertNotFoundRequest(
          authRes,
          "Command 'UPDATE' rejected with code 'NOT_FOUND': Expected to update authorization with key 9999999999999999, but an authorization with this key does not exist",
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Update Role Authorization - wrong resourceId value - 404 Not Found', async ({
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
    let roleAuthorizationKey: string;

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
      originalRoleAuthorization = roleAuthorizationBody;
    });

    const updatedRoleAuthorization = {
      ...originalRoleAuthorization,
      resourceId: 'meow',
    };

    await test.step('Update role authorization with changed resourceId', async () => {
      const authRes = await request.put(
        buildUrl(`/authorizations/${roleAuthorizationKey}`),
        {
          headers: jsonHeaders(),
          data: {
            ...updatedRoleAuthorization,
          },
        },
      );
      await assertNotFoundRequest(
        authRes,
        "Command 'UPDATE' rejected with code 'NOT_FOUND': Expected to create or update authorization with ownerId or resourceId 'meow', but a user with this ID does not exist.",
      );
    });

    await test.step('Poll authorization', async () => {
      await expectAuthorizationCanBeFound(request, roleAuthorizationKey);
    });

    await test.step('Verify updated authorization', async () => {
      const expectedRoleAuthorization = {
        ...originalRoleAuthorization,
        authorizationKey: roleAuthorizationKey,
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

  test('Update User Authorization - wrong resourceType - 400 Bad Request', async ({
    request,
  }) => {
    let user: {
      username: string;
      name: string;
      email: string;
      password: string;
    };
    let userAuthorizationKey: string;
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

    const updatedUserAuthorization = {
      ...originalUserAuthorization,
      resourceType: 'WRONG_VALUE_FOR_TEST',
    };

    await test.step('Update user authorization with invalid resourceType', async () => {
      const authRes = await request.put(
        buildUrl(`/authorizations/${userAuthorizationKey}`),
        {
          headers: jsonHeaders(),
          data: {
            ...updatedUserAuthorization,
          },
        },
      );
      await assertBadRequest(
        authRes,
        "Unexpected value 'WRONG_VALUE_FOR_TEST' for enum field 'resourceType'. Use any of the following values: [AUDIT_LOG, AUTHORIZATION, BATCH, CLUSTER_VARIABLE, COMPONENT, DECISION_DEFINITION, DECISION_REQUIREMENTS_DEFINITION, DOCUMENT, EXPRESSION, GLOBAL_LISTENER, GROUP, MAPPING_RULE, MESSAGE, PROCESS_DEFINITION, RESOURCE, ROLE, SYSTEM, TENANT, USER, USER_TASK]",
      );
    });
  });

  test('Update User Authorization - empty permissionType - 400 Bad Request', async ({
    request,
  }) => {
    let user: {
      username: string;
      name: string;
      email: string;
      password: string;
    };
    let userAuthorizationKey: string;
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

    const updatedUserAuthorization = {
      ...originalUserAuthorization,
      permissionTypes: [],
    };

    await test.step('Update user authorization with empty permissionTypes', async () => {
      const authRes = await request.put(
        buildUrl(`/authorizations/${userAuthorizationKey}`),
        {
          headers: jsonHeaders(),
          data: {
            ...updatedUserAuthorization,
          },
        },
      );
      await assertInvalidArgument(authRes, 400, 'No permissionTypes provided.');
    });
  });
});
