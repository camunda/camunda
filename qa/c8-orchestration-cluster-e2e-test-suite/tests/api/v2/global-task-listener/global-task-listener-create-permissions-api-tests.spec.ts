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
  assertForbiddenRequest,
  assertStatusCode,
  encode,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {
  createUser,
  grantUserResourceAuthorization,
  createComponentAuthorization,
  cleanupAuthorizations,
} from '@requestHelpers';
import {generateUniqueId} from '../../../../utils/constants';

function createUniqueGlobalTaskListenerBody() {
  const id = `test-gl-${generateUniqueId()}`;
  return {
    id,
    type: `io.camunda.test.listener.${id}`,
    eventTypes: ['creating', 'completing'],
  };
}

test.describe('Global Task Listener API - Create Permission Tests', () => {
  // User who has a valid session but no GLOBAL_LISTENER:CREATE permission
  let userWithoutPermission: {
    username: string;
    name: string;
    email: string;
    password: string;
  };
  // User who only has READ on GLOBAL_LISTENER, not CREATE
  let userWithReadOnlyPermission: {
    username: string;
    name: string;
    email: string;
    password: string;
  };

  const createdListenerIds: string[] = [];
  const authorizationKeys: string[] = [];

  test.beforeAll(async ({request}) => {
    // Create a user with no specific GLOBAL_LISTENER permissions but able to authenticate
    userWithoutPermission = await createUser(request);
    await grantUserResourceAuthorization(request, userWithoutPermission);

    // Create a user with only READ permission on GLOBAL_LISTENER
    userWithReadOnlyPermission = await createUser(request);
    await grantUserResourceAuthorization(request, userWithReadOnlyPermission);

    // Grant READ-only on GLOBAL_LISTENER
    const readOnlyAuthKey = await createComponentAuthorization(request, {
      ownerId: userWithReadOnlyPermission.username,
      ownerType: 'USER',
      resourceType: 'GLOBAL_LISTENER',
      resourceId: '*',
      permissionTypes: ['READ_TASK_LISTENER'],
    });
    authorizationKeys.push(readOnlyAuthKey);
  });

  test.afterAll(async ({request}) => {
    await cleanupAuthorizations(request, authorizationKeys);
    await cleanupUsers(request, [
      userWithoutPermission.username,
      userWithReadOnlyPermission.username,
    ]);
    for (const id of createdListenerIds) {
      try {
        await request.delete(buildUrl('/global-task-listeners/{id}', {id}), {
          headers: jsonHeaders(),
        });
      } catch {
        // Ignore cleanup errors
      }
    }
  });

  test('Create Global Task Listener - 403 Forbidden - user with no GLOBAL_LISTENER permissions', async ({
    request,
  }) => {
    const token = encode(
      `${userWithoutPermission.username}:${userWithoutPermission.password}`,
    );

    await expect(async () => {
      const res = await request.post(buildUrl('/global-task-listeners'), {
        headers: jsonHeaders(token),
        data: createUniqueGlobalTaskListenerBody(),
      });

      await assertForbiddenRequest(
        res,
        "Command 'CREATE' rejected with code 'FORBIDDEN': Insufficient permissions to perform operation 'CREATE' on resource 'GLOBAL_LISTENER'",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Create Global Task Listener - 403 Forbidden - user with READ-only GLOBAL_LISTENER permission', async ({
    request,
  }) => {
    const token = encode(
      `${userWithReadOnlyPermission.username}:${userWithReadOnlyPermission.password}`,
    );

    await expect(async () => {
      const res = await request.post(buildUrl('/global-task-listeners'), {
        headers: jsonHeaders(token),
        data: createUniqueGlobalTaskListenerBody(),
      });

      await assertForbiddenRequest(
        res,
        "Command 'CREATE' rejected with code 'FORBIDDEN': Insufficient permissions to perform operation 'CREATE' on resource 'GLOBAL_LISTENER'",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Create Global Task Listener - 201 Success - admin user with full permissions', async ({
    request,
  }) => {
    // Sanity check: the default demo:demo admin can still create
    await expect(async () => {
      const body = createUniqueGlobalTaskListenerBody();
      const res = await request.post(buildUrl('/global-task-listeners'), {
        headers: jsonHeaders(),
        data: body,
      });

      await assertStatusCode(res, 201);
      const json = await res.json();
      createdListenerIds.push(json.id);
    }).toPass(defaultAssertionOptions);
  });
});
