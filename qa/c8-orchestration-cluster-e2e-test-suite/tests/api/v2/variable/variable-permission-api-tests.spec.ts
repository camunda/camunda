/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  cancelProcessInstance,
  createSingleInstance,
  deploy,
} from '../../../../utils/zeebeClient';
import {
  assertForbiddenRequest,
  assertStatusCode,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {setupVariableTest} from '../../../../utils/requestHelpers/variable-requestHelpers';
import {
  createComponentAuthorization,
  createUser,
  grantUserResourceAuthorization,
  searchVariableByNameAndProcessInstanceKey,
} from '@requestHelpers';

const VARIABLE_SEARCH_ENDPOINT = '/variables/search';
const READABLE_PROCESS_ID = 'Variable_Process';
const READABLE_VARIABLE_NAME = 'permissionTestVariable';
const READ_FORBIDDEN_DETAIL =
  "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'";

type TestUser = {
  username: string;
  name: string;
  email: string;
  password: string;
};

test.describe.serial('Variable Permission API - Forbidden', () => {
  // Variable of a process the restricted users have no access to at all.
  let unauthorizedProcessInstanceKey: string;
  let unauthorizedVariableKey: string;
  // Variable of the process the partially authorized user may read.
  let readableProcessInstanceKey: string;
  let readableVariableKey: string;

  let userWithoutPermissions: TestUser;
  let userWithoutPermissionsToken: string;
  let userWithReadPermission: TestUser;
  let userWithReadPermissionToken: string;

  test.beforeAll(async ({request}) => {
    await deploy([
      './resources/process_with_variables.bpmn',
      './resources/Variable_Process.bpmn',
    ]);

    const unauthorizedState: Record<string, unknown> = {};
    await setupVariableTest(unauthorizedState, request);
    unauthorizedProcessInstanceKey = unauthorizedState[
      'processInstanceKey'
    ] as string;
    unauthorizedVariableKey = unauthorizedState['variableKey'] as string;

    readableProcessInstanceKey = (
      await createSingleInstance(READABLE_PROCESS_ID, 1, {
        [READABLE_VARIABLE_NAME]: `value-${Date.now()}`,
      })
    ).processInstanceKey;
    readableVariableKey = (
      await searchVariableByNameAndProcessInstanceKey(request, {
        processInstanceKey: readableProcessInstanceKey,
        name: READABLE_VARIABLE_NAME,
      })
    ).variableKey;

    userWithoutPermissions = await createUser(request);
    await grantUserResourceAuthorization(request, userWithoutPermissions);
    userWithoutPermissionsToken = encode(
      `${userWithoutPermissions.username}:${userWithoutPermissions.password}`,
    );

    userWithReadPermission = await createUser(request);
    await grantUserResourceAuthorization(request, userWithReadPermission);
    await createComponentAuthorization(request, {
      ownerId: userWithReadPermission.username,
      ownerType: 'USER',
      resourceId: READABLE_PROCESS_ID,
      resourceType: 'PROCESS_DEFINITION',
      permissionTypes: ['READ_PROCESS_INSTANCE'],
    });
    userWithReadPermissionToken = encode(
      `${userWithReadPermission.username}:${userWithReadPermission.password}`,
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, [
      userWithoutPermissions.username,
      userWithReadPermission.username,
    ]);
    await cancelProcessInstance(unauthorizedProcessInstanceKey);
    await cancelProcessInstance(readableProcessInstanceKey);
  });

  test('Search variables - filtered out without READ_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    const filter = {processInstanceKey: unauthorizedProcessInstanceKey};

    await expect(async () => {
      // when
      const res = await request.post(buildUrl(VARIABLE_SEARCH_ENDPOINT), {
        headers: jsonHeaders(userWithoutPermissionsToken),
        data: {filter},
      });

      // then
      // Search requires READ_PROCESS_INSTANCE on PROCESS_DEFINITION; without it
      // the variables are filtered from the result instead of rejected.
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.items).toHaveLength(0);
      expect(body.page.totalItems).toBe(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search variables - only variables of readable process definitions returned', async ({
    request,
  }) => {
    // given
    const filter = {
      processInstanceKey: {
        $in: [unauthorizedProcessInstanceKey, readableProcessInstanceKey],
      },
    };

    await expect(async () => {
      // when
      const res = await request.post(buildUrl(VARIABLE_SEARCH_ENDPOINT), {
        headers: jsonHeaders(userWithReadPermissionToken),
        data: {filter},
      });

      // then
      // READ_PROCESS_INSTANCE is granted on 'Variable_Process' only, so the
      // variables of 'process_with_variables' are filtered out.
      await assertStatusCode(res, 200);
      const body = await res.json();
      const variableKeys = body.items.map(
        (item: {variableKey: string}) => item.variableKey,
      );
      expect(variableKeys).toContain(readableVariableKey);
      expect(variableKeys).not.toContain(unauthorizedVariableKey);
      expect(
        body.items.every(
          (item: {processInstanceKey: string}) =>
            item.processInstanceKey === readableProcessInstanceKey,
        ),
      ).toBe(true);
    }).toPass(defaultAssertionOptions);
  });

  test('Get variable - forbidden without READ_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    const variableKey = unauthorizedVariableKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl('/variables/{variableKey}', {variableKey}),
        {headers: jsonHeaders(userWithoutPermissionsToken)},
      );

      // then
      // Get by key requires READ_PROCESS_INSTANCE on PROCESS_DEFINITION and
      // rejects with 403 when it is missing.
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Get variable - forbidden for variable of non-readable process definition', async ({
    request,
  }) => {
    // given
    const variableKey = unauthorizedVariableKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl('/variables/{variableKey}', {variableKey}),
        {headers: jsonHeaders(userWithReadPermissionToken)},
      );

      // then
      // READ_PROCESS_INSTANCE on 'Variable_Process' does not extend to
      // 'process_with_variables', so the request is rejected with 403.
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Get variable - allowed for variable of readable process definition', async ({
    request,
  }) => {
    // given
    const variableKey = readableVariableKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl('/variables/{variableKey}', {variableKey}),
        {headers: jsonHeaders(userWithReadPermissionToken)},
      );

      // then
      // The same user reads the variable of 'Variable_Process', so the 403 for
      // 'process_with_variables' comes from per-definition scoping, not a
      // broken grant.
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.variableKey).toBe(readableVariableKey);
      expect(body.processInstanceKey).toBe(readableProcessInstanceKey);
      expect(body.name).toBe(READABLE_VARIABLE_NAME);
    }).toPass(defaultAssertionOptions);
  });
});
