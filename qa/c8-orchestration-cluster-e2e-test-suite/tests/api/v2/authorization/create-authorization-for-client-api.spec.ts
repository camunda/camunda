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
import {
  cleanupAuthorizations,
  createUser,
  grantUserResourceAuthorization,
} from '@requestHelpers';
import {validateResponse} from '../../../../json-body-assertions';
import {
  CREATE_CUSTOM_AUTHORIZATION_BODY,
  authorizedComponentRequiredFields,
} from '../../../../utils/beans/requestBeans';

const CREATE_AUTHORIZATION_ENDPOINT = '/authorizations';

test.describe.serial('Create Authorization API for client - Success and Conflict', () => {
  const clientId = 'client' + generateUniqueId();
  let createdAuthorizationKeys: string[] = [];

  test.afterAll(async ({request}) => {
    await test.step('Teardown - Delete authorizations created during Authorization tests', async () => {
      await cleanupAuthorizations(request, createdAuthorizationKeys);
    });
  });

  test('Create Authorization for client - Success', async ({request}) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      clientId,
      'CLIENT',
      '*',
      'AUTHORIZATION',
      ['READ'],
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
      createdAuthorizationKeys.push(authBody.authorizationKey);
    }).toPass(defaultAssertionOptions);
  });

  test('Create Authorization for client - Multiple permissionTypes - Success', async ({
    request,
  }) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      clientId,
      'CLIENT',
      '*',
      'BATCH',
      [
        'CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE',
        'CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE',
      ],
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
      createdAuthorizationKeys.push(authBody.authorizationKey);
    }).toPass(defaultAssertionOptions);
  });

  test('Create Authorization for client - 409 Conflict', async ({request}) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      clientId,
      'CLIENT',
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
        `Command 'CREATE' rejected with code 'ALREADY_EXISTS': Expected to create authorization for owner '${clientId}' for resource identifier '*', but an authorization for this resource identifier already exists.`,
      );
    }).toPass(defaultAssertionOptions);
  });
});

test.describe.parallel('Create Authorization API for client - Unhappy paths', () => {
  const clientId = 'client' + generateUniqueId();

  test('Create Authorization for client - 400 Bad Request - wrong value for ownerType', async ({
    request,
  }) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      clientId,
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

  test('Create Authorization for client - 400 Bad Request - wrong value for resourceType', async ({
    request,
  }) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      clientId,
      'CLIENT',
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
        "Unexpected value 'WRONG_VALUE_FOR_TEST' for enum field 'resourceType'. Use any of the following values: [AUDIT_LOG, AUTHORIZATION, BATCH, CLUSTER_VARIABLE, COMPONENT, DECISION_DEFINITION, DECISION_REQUIREMENTS_DEFINITION, DOCUMENT, EXPRESSION, GROUP, MAPPING_RULE, MESSAGE, PROCESS_DEFINITION, RESOURCE, ROLE, SYSTEM, TENANT, USER, USER_TASK]",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Create Authorization for client - 400 Invalid Argument - invalid resourceId', async ({
    request,
  }) => {
    const invalidResourceId = ';;;;';
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      clientId,
      'CLIENT',
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

  test('Create Authorization for client - 401 Unauthorized', async ({
    request,
  }) => {
    const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
      clientId,
      'CLIENT',
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
});

test.describe('Create Authorization for Client - Forbidden', () => {
  let clientIdToGrantAuthorization = 'client' + generateUniqueId();

  let userWithResourcesAuthorizationToSendRequest: {
    username: string;
    name: string;
    email: string;
    password: string;
  };

  test.beforeAll(
    'Setup - Create test user with Resource Authorization and user for granting Authorization',
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
  test('Create Authorization for client - 403 Forbidden', async ({request}) => {
    await test.step('Test - Create Authorization with user credentials', async () => {
      const token = encode(
        `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
      );
      const authorizationBody = CREATE_CUSTOM_AUTHORIZATION_BODY(
        clientIdToGrantAuthorization,
        'CLIENT',
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
});