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
  assertUnauthorizedRequest,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {
  createComponentAuthorization,
  createUser,
  grantUserResourceAuthorization,
  searchIncidentByPIK,
} from '@requestHelpers';

const INCIDENT_SEARCH_ENDPOINT = '/incidents/search';
const READ_FORBIDDEN_DETAIL =
  "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'";
const UPDATE_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'";

type TestUser = {
  username: string;
  name: string;
  email: string;
  password: string;
};

test.describe.serial('Incident Permission API - Forbidden', () => {
  // Incident of a process the restricted users have no access to at all.
  let unauthorizedProcessInstanceKey: string;
  let unauthorizedIncidentKey: string;
  // Incident of the process the partially authorized user may read but not update.
  let readableProcessInstanceKey: string;
  let readableIncidentKey: string;

  let userWithoutPermissions: TestUser;
  let userWithoutPermissionsToken: string;
  let userWithReadPermission: TestUser;
  let userWithReadPermissionToken: string;

  test.beforeAll(async ({request}) => {
    await deploy([
      './resources/singleIncidentProcess.bpmn',
      './resources/processWithAnError.bpmn',
    ]);

    unauthorizedProcessInstanceKey = (
      await createSingleInstance('singleIncidentProcess', 1)
    ).processInstanceKey;
    unauthorizedIncidentKey = (
      await searchIncidentByPIK(request, {
        processInstanceKey: unauthorizedProcessInstanceKey,
      })
    )[0].incidentKey;

    readableProcessInstanceKey = (
      await createSingleInstance('processWithAnError', 1)
    ).processInstanceKey;
    readableIncidentKey = (
      await searchIncidentByPIK(request, {
        processInstanceKey: readableProcessInstanceKey,
      })
    )[0].incidentKey;

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
      resourceId: 'processWithAnError',
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

  test('Search incidents - filtered out without READ_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    const filter = {processInstanceKey: unauthorizedProcessInstanceKey};

    await expect(async () => {
      // when
      const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: jsonHeaders(userWithoutPermissionsToken),
        data: {filter},
      });

      // then
      // Search requires READ_PROCESS_INSTANCE on PROCESS_DEFINITION; without it
      // the incident is filtered from the result instead of rejected.
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.items).toHaveLength(0);
      expect(body.page.totalItems).toBe(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search incidents - only incidents of readable process definitions returned', async ({
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
      const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: jsonHeaders(userWithReadPermissionToken),
        data: {filter},
      });

      // then
      // READ_PROCESS_INSTANCE is granted on 'processWithAnError' only, so the
      // incident of 'singleIncidentProcess' is filtered out.
      await assertStatusCode(res, 200);
      const body = await res.json();
      const incidentKeys = body.items.map(
        (item: {incidentKey: string}) => item.incidentKey,
      );
      expect(incidentKeys).toContain(readableIncidentKey);
      expect(incidentKeys).not.toContain(unauthorizedIncidentKey);
      expect(
        body.items.every(
          (item: {processDefinitionId: string}) =>
            item.processDefinitionId === 'processWithAnError',
        ),
      ).toBe(true);
    }).toPass(defaultAssertionOptions);
  });

  test('Get incident - forbidden without READ_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    const incidentKey = unauthorizedIncidentKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl('/incidents/{incidentKey}', {incidentKey}),
        {headers: jsonHeaders(userWithoutPermissionsToken)},
      );

      // then
      // Get by key requires READ_PROCESS_INSTANCE on PROCESS_DEFINITION and
      // rejects with 403 when it is missing.
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Resolve incident - forbidden without UPDATE_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    const incidentKey = readableIncidentKey;

    // when
    const res = await request.post(
      buildUrl('/incidents/{incidentKey}/resolution', {incidentKey}),
      {headers: jsonHeaders(userWithReadPermissionToken)},
    );

    // then
    // Resolution requires UPDATE_PROCESS_INSTANCE on PROCESS_DEFINITION; read
    // access alone is rejected with 403 and the incident stays active.
    await assertForbiddenRequest(res, UPDATE_FORBIDDEN_DETAIL);
    await expect(async () => {
      const searchRes = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {filter: {incidentKey}},
      });
      await assertStatusCode(searchRes, 200);
      const body = await searchRes.json();
      expect(body.items).toHaveLength(1);
      expect(body.items[0].state).toBe('ACTIVE');
    }).toPass(defaultAssertionOptions);
  });

  test('Get incident - unauthenticated', async ({request}) => {
    // given
    const incidentKey = unauthorizedIncidentKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl('/incidents/{incidentKey}', {incidentKey}),
        {headers: {'Content-Type': 'application/json'}},
      );

      // then
      // Requests without credentials are rejected before any authorization check.
      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });
});
