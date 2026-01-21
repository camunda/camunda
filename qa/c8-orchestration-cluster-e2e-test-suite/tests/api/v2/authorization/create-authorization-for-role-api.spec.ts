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
} from '../../../../utils/http';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {cleanupRoles} from '../../../../utils/rolesCleanup';
import {
  createUser,
  grantUserResourceAuthorization,
  createRole,
} from '@requestHelpers';
import {validateResponse} from '../../../../json-body-assertions';
import {
  CREATE_CUSTOM_AUTHORIZATION_BODY,
  authorizedComponentRequiredFields,
} from '../../../../utils/beans/requestBeans';

const CREATE_AUTHORIZATION_ENDPOINT = '/authorizations';

test.describe
  .serial('Create Authorization API for role - Success and Conflict', () => {
  const uid = generateUniqueId();
  let successRole = {
    roleId: `role-create-authorization-${uid}`,
    name: `authorization Role`,
    description: 'Create Authorization Success API test role',
  };
  test.beforeAll(async ({request}) => {
    await test.step('Setup - Create role for Authorization tests', async () => {
      successRole = await createRole(request);
      console.log('Created role with roleId:', successRole.roleId);
    });
  });

  test.afterAll(async ({request}) => {
    await test.step(
      'Teardown - Delete role with roleId ' +
        successRole.roleId +
        ' created for Authorization tests',
      async () => {
        await cleanupRoles(request, [successRole.roleId]);
      },
    );
  });

  test('Create Authorization for role - Success', async ({request}) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      successRole.roleId,
      'ROLE',
      '*',
      'GROUP',
      ['DELETE'],
    );

    await expect(async () => {
      const authRes = await request.post(
        buildUrl(CREATE_AUTHORIZATION_ENDPOINT),
        {
          headers: jsonHeaders(),
          data: authorizationBody,
        },
      );
      await assertStatusCode(authRes, 201);

      await validateResponse(
        {
          path: CREATE_AUTHORIZATION_ENDPOINT,
          method: 'POST',
          status: '201',
        },
        authRes,
      );

      const authBody = await authRes.json();
      assertRequiredFields(authBody, authorizedComponentRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Create Authorization for role - Multiple permissionTypes - Success', async ({
    request,
  }) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      successRole.roleId,
      'ROLE',
      '*',
      'MAPPING_RULE',
      ['CREATE', 'READ'],
    );

    await expect(async () => {
      const authRes = await request.post(
        buildUrl(CREATE_AUTHORIZATION_ENDPOINT),
        {
          headers: jsonHeaders(),
          data: authorizationBody,
        },
      );
      await assertStatusCode(authRes, 201);

      await validateResponse(
        {
          path: CREATE_AUTHORIZATION_ENDPOINT,
          method: 'POST',
          status: '201',
        },
        authRes,
      );

      const authBody = await authRes.json();
      assertRequiredFields(authBody, authorizedComponentRequiredFields);
    }).toPass(defaultAssertionOptions);
  });

  test('Create Authorization for role - 409 Conflict', async ({request}) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      successRole.roleId,
      'ROLE',
      '*',
      'PROCESS_DEFINITION',
      ['CREATE_PROCESS_INSTANCE'],
    );

    await expect(async () => {
      const authRes = await request.post(
        buildUrl(CREATE_AUTHORIZATION_ENDPOINT),
        {
          headers: jsonHeaders(),
          data: authorizationBody,
        },
      );
      await assertConflictRequest(
        authRes,
        `Command 'CREATE' rejected with code 'ALREADY_EXISTS': Expected to create authorization for owner '${successRole.roleId}' for resource identifier '*', but an authorization for this resource identifier already exists.`,
      );
    }).toPass(defaultAssertionOptions);
  });
});

test.describe
  .parallel('Create Authorization API for role - Unhappy paths', () => {
  const uid = generateUniqueId();
  let failRole = {
    roleId: `role-create-authorization-${uid}`,
    name: `authorization fail role`,
    description: 'Create Authorization Fail API test role',
  };
  test.beforeAll(async ({request}) => {
    await test.step('Setup - Create role for Authorization tests', async () => {
      failRole = await createRole(request);
      console.log('Created role with roleId:', failRole.roleId);
    });
  });

  test.afterAll(async ({request}) => {
    await test.step(
      'Teardown - Delete role with roleId ' +
        failRole.roleId +
        ' created for Authorization tests',
      async () => {
        await cleanupRoles(request, [failRole.roleId]);
      },
    );
  });

  test('Create Authorization for role - 400 Bad Request - wrong value for ownerType', async ({
    request,
  }) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      failRole.roleId,
      'WRONG_VALUE_FOR_TEST',
      '*',
      'PROCESS_DEFINITION',
      ['CREATE_PROCESS_INSTANCE'],
    );

    await expect(async () => {
      const authRes = await request.post(
        buildUrl(CREATE_AUTHORIZATION_ENDPOINT),
        {
          headers: jsonHeaders(),
          data: authorizationBody,
        },
      );
      await assertBadRequest(
        authRes,
        "Unexpected value 'WRONG_VALUE_FOR_TEST' for enum field 'ownerType'. Use any of the following values: [USER, CLIENT, ROLE, GROUP, MAPPING_RULE, UNSPECIFIED]",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Create Authorization for role - 400 Bad Request - wrong value for resourceType', async ({
    request,
  }) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      failRole.roleId,
      'ROLE',
      '*',
      'WRONG_VALUE_FOR_TEST',
      ['CREATE_PROCESS_INSTANCE'],
    );

    await expect(async () => {
      const authRes = await request.post(
        buildUrl(CREATE_AUTHORIZATION_ENDPOINT),
        {
          headers: jsonHeaders(),
          data: authorizationBody,
        },
      );
      await assertBadRequest(
        authRes,
        "Unexpected value 'WRONG_VALUE_FOR_TEST' for enum field 'resourceType'. Use any of the following values: [AUTHORIZATION, MAPPING_RULE, MESSAGE, BATCH, COMPONENT, SYSTEM, TENANT, RESOURCE, PROCESS_DEFINITION, DECISION_REQUIREMENTS_DEFINITION, DECISION_DEFINITION, GROUP, USER, ROLE, DOCUMENT]",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Create Authorization for role - 400 Invalid Argument - invalid resourceId', async ({
    request,
  }) => {
    const invalidResourceId = ';;;;';
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      failRole.roleId,
      'ROLE',
      invalidResourceId,
      'PROCESS_DEFINITION',
      ['CREATE_PROCESS_INSTANCE'],
    );

    await expect(async () => {
      const authRes = await request.post(
        buildUrl(CREATE_AUTHORIZATION_ENDPOINT),
        {
          headers: jsonHeaders(),
          data: authorizationBody,
        },
      );
      await assertInvalidArgument(
        authRes,
        400,
        `The provided resourceId contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'.`,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Create Authorization for role - 401 Unauthorized', async ({
    request,
  }) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      failRole.roleId,
      'ROLE',
      '*',
      'PROCESS_DEFINITION',
      ['CREATE_PROCESS_INSTANCE'],
    );

    await expect(async () => {
      const authRes = await request.post(
        buildUrl(CREATE_AUTHORIZATION_ENDPOINT),
        {
          headers: {
            'Content-Type': 'application/json',
          },
          data: authorizationBody,
        },
      );
      await assertUnauthorizedRequest(authRes);
    }).toPass(defaultAssertionOptions);
  });

  test('Create Authorization for role - 404 Not Found - not existing ownerId', async ({
    request,
  }) => {
    const notExistingOwnerId = 'nonExistingOwnerId12345';
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      notExistingOwnerId,
      'ROLE',
      '*',
      'PROCESS_DEFINITION',
      ['CREATE_PROCESS_INSTANCE'],
    );

    await expect(async () => {
      const authRes = await request.post(
        buildUrl(CREATE_AUTHORIZATION_ENDPOINT),
        {
          headers: jsonHeaders(),
          data: authorizationBody,
        },
      );
      await assertNotFoundRequest(
        authRes,
        `Command 'CREATE' rejected with code 'NOT_FOUND': Expected to create or update authorization with ownerId or resourceId '${notExistingOwnerId}', but a role with this ID does not exist.`,
      );
    }).toPass(defaultAssertionOptions);
  });
});

test.describe('Create Authorization for role - Forbidden', () => {
  const uid = generateUniqueId();
  let forbiddenRole = {
    roleId: `role-create-authorization-${uid}`,
    name: `authorization forbidden role`,
    description: 'Create Authorization forbidden API test role',
  };
  let userWithResourcesAuthorizationToSendRequest: {
    username: string;
    name: string;
    email: string;
    password: string;
  };

  test.beforeAll(
    'Setup - Create test user with Resource Authorization',
    async ({request}) => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
    },
  );

  test.afterAll(
    'Teardown - Delete test users created for Authorization Forbidden tests',
    async ({request}) => {
      await cleanupUsers(request, [
        userWithResourcesAuthorizationToSendRequest.username,
      ]);
    },
  );
  test('Create Authorization for role - 403 Forbidden', async ({request}) => {
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );

    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      forbiddenRole.roleId,
      'ROLE',
      '*',
      'PROCESS_DEFINITION',
      ['CREATE_PROCESS_INSTANCE'],
    );

    await expect(async () => {
      const authRes = await request.post(
        buildUrl(CREATE_AUTHORIZATION_ENDPOINT),
        {
          headers: jsonHeaders(token), // overrides default demo:demo
          data: authorizationBody,
        },
      );
      await assertForbiddenRequest(
        authRes,
        "Command 'CREATE' rejected with code 'FORBIDDEN': Insufficient permissions to perform operation 'CREATE' on resource 'AUTHORIZATION'",
      );
    }).toPass(defaultAssertionOptions);
  });
});
