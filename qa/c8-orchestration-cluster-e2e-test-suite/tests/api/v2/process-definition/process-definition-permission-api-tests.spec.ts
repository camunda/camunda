/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {randomUUID} from 'node:crypto';
import {APIRequestContext, expect, test} from '@playwright/test';
import {deploy, deployWithSubstitutions} from '../../../../utils/zeebeClient';
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
  createInstanceOnceDeployed,
  createUser,
  grantUserResourceAuthorization,
} from '@requestHelpers';

const PROCESS_DEFINITION_SEARCH_ENDPOINT = '/process-definitions/search';
const PROCESS_DEFINITION_ENDPOINT =
  '/process-definitions/{processDefinitionKey}';
const PROCESS_DEFINITION_XML_ENDPOINT =
  '/process-definitions/{processDefinitionKey}/xml';
const PROCESS_DEFINITION_FORM_ENDPOINT =
  '/process-definitions/{processDefinitionKey}/form';
const PROCESS_DEFINITION_STATISTICS_ENDPOINT =
  '/process-definitions/{processDefinitionKey}/statistics/element-instances';
const READ_FORBIDDEN_DETAIL =
  "Unauthorized to perform operation 'READ_PROCESS_DEFINITION' on resource 'PROCESS_DEFINITION'";
const FORM_READ_FORBIDDEN_DETAIL =
  "Unauthorized to perform operation 'READ' on resource 'RESOURCE'";
const LINKED_START_FORM_ID = 'sign_up_form';

type TestUser = {
  username: string;
  name: string;
  email: string;
  password: string;
};

type DeployedProcess = {
  processDefinitionId: string;
  processDefinitionKey: string;
};

async function deployUniqueProcess(
  processDefinitionId: string,
): Promise<DeployedProcess> {
  const response = await deployWithSubstitutions(
    './resources/process_with_linked_start_form.bpmn',
    {process_with_linked_start_form: processDefinitionId},
  );
  expect(response.processes).toHaveLength(1);
  const {processDefinitionKey} = response.processes[0];
  await createInstanceOnceDeployed(processDefinitionId, 1);
  return {processDefinitionId, processDefinitionKey};
}

async function searchByProcessDefinitionId(
  request: APIRequestContext,
  processDefinitionId: string,
  token?: string,
) {
  const res = await request.post(buildUrl(PROCESS_DEFINITION_SEARCH_ENDPOINT), {
    headers: jsonHeaders(token),
    data: {filter: {processDefinitionId}},
  });
  await assertStatusCode(res, 200);
  return res.json();
}

async function getElementStatistics(
  request: APIRequestContext,
  processDefinitionKey: string,
  token?: string,
) {
  const res = await request.post(
    buildUrl(PROCESS_DEFINITION_STATISTICS_ENDPOINT, {processDefinitionKey}),
    {
      headers: jsonHeaders(token),
      data: {},
    },
  );
  await assertStatusCode(res, 200);
  return res.json();
}

test.describe('Process Definition Permission API - Forbidden', () => {
  const suffix = randomUUID().slice(0, 8);
  // The partially authorized user may read this process definition only.
  const readableProcessId = `pd-perm-readable-${suffix}`;
  // No restricted user has any access to this process definition.
  const restrictedProcessId = `pd-perm-restricted-${suffix}`;

  let readableProcess: DeployedProcess;
  let restrictedProcess: DeployedProcess;

  let userWithoutPermissions: TestUser;
  let userWithoutPermissionsToken: string;
  let userWithReadPermission: TestUser;
  let userWithReadPermissionToken: string;
  // Holds READ_PROCESS_DEFINITION only, without the wildcard RESOURCE READ
  // that would also grant form reads.
  let userWithProcessReadOnly: TestUser;
  let userWithProcessReadOnlyToken: string;

  test.beforeAll(async ({request}) => {
    await deploy([`./resources/${LINKED_START_FORM_ID}.form`]);
    readableProcess = await deployUniqueProcess(readableProcessId);
    restrictedProcess = await deployUniqueProcess(restrictedProcessId);

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
      resourceId: readableProcessId,
      resourceType: 'PROCESS_DEFINITION',
      permissionTypes: ['READ_PROCESS_DEFINITION'],
    });
    userWithReadPermissionToken = encode(
      `${userWithReadPermission.username}:${userWithReadPermission.password}`,
    );

    userWithProcessReadOnly = await createUser(request);
    await createComponentAuthorization(request, {
      ownerId: userWithProcessReadOnly.username,
      ownerType: 'USER',
      resourceId: readableProcessId,
      resourceType: 'PROCESS_DEFINITION',
      permissionTypes: ['READ_PROCESS_DEFINITION'],
    });
    userWithProcessReadOnlyToken = encode(
      `${userWithProcessReadOnly.username}:${userWithProcessReadOnly.password}`,
    );
  });

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, [
      userWithoutPermissions.username,
      userWithReadPermission.username,
      userWithProcessReadOnly.username,
    ]);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Search process definitions - unauthorized without credentials', async ({
    request,
  }) => {
    // when
    const res = await request.post(
      buildUrl(PROCESS_DEFINITION_SEARCH_ENDPOINT),
      {headers: {}, data: {}},
    );

    // then
    await assertUnauthorizedRequest(res);
  });

  test('Search process definitions - filtered out without READ_PROCESS_DEFINITION permission', async ({
    request,
  }) => {
    await expect(async () => {
      // given
      const adminBody = await searchByProcessDefinitionId(
        request,
        readableProcessId,
      );
      expect(adminBody.page.totalItems).toBe(1);

      // when
      const body = await searchByProcessDefinitionId(
        request,
        readableProcessId,
        userWithoutPermissionsToken,
      );

      // then
      // Search requires READ_PROCESS_DEFINITION on PROCESS_DEFINITION; without
      // it the process definition is filtered from the result instead of rejected.
      expect(body.items).toHaveLength(0);
      expect(body.page.totalItems).toBe(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search process definitions - only readable process definitions returned', async ({
    request,
  }) => {
    await expect(async () => {
      // given
      const adminBody = await searchByProcessDefinitionId(
        request,
        restrictedProcessId,
      );
      expect(adminBody.page.totalItems).toBe(1);

      // when
      const readableBody = await searchByProcessDefinitionId(
        request,
        readableProcessId,
        userWithReadPermissionToken,
      );
      const restrictedBody = await searchByProcessDefinitionId(
        request,
        restrictedProcessId,
        userWithReadPermissionToken,
      );

      // then
      // READ_PROCESS_DEFINITION is granted on the readable process only, so the
      // restricted process is filtered out.
      expect(readableBody.page.totalItems).toBe(1);
      expect(readableBody.items[0].processDefinitionKey).toBe(
        readableProcess.processDefinitionKey,
      );
      expect(restrictedBody.items).toHaveLength(0);
      expect(restrictedBody.page.totalItems).toBe(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Get process definition - forbidden without READ_PROCESS_DEFINITION permission', async ({
    request,
  }) => {
    // given
    const processDefinitionKey = readableProcess.processDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl(PROCESS_DEFINITION_ENDPOINT, {processDefinitionKey}),
        {headers: jsonHeaders(userWithoutPermissionsToken)},
      );

      // then
      // Get by key requires READ_PROCESS_DEFINITION on PROCESS_DEFINITION and
      // rejects with 403 when it is missing.
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Get process definition - allowed with READ_PROCESS_DEFINITION on that process', async ({
    request,
  }) => {
    // given
    const processDefinitionKey = readableProcess.processDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl(PROCESS_DEFINITION_ENDPOINT, {processDefinitionKey}),
        {headers: jsonHeaders(userWithReadPermissionToken)},
      );

      // then
      // READ_PROCESS_DEFINITION granted on this process allows reading it.
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.processDefinitionId).toBe(readableProcessId);
    }).toPass(defaultAssertionOptions);
  });

  test('Get process definition - forbidden when READ_PROCESS_DEFINITION is granted on another process only', async ({
    request,
  }) => {
    // given
    const processDefinitionKey = restrictedProcess.processDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl(PROCESS_DEFINITION_ENDPOINT, {processDefinitionKey}),
        {headers: jsonHeaders(userWithReadPermissionToken)},
      );

      // then
      // The permission is checked against the process definition id, so read
      // access to the readable process does not extend to the restricted one.
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Get process definition XML - forbidden without READ_PROCESS_DEFINITION permission', async ({
    request,
  }) => {
    // given
    const processDefinitionKey = readableProcess.processDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl(PROCESS_DEFINITION_XML_ENDPOINT, {processDefinitionKey}),
        {headers: jsonHeaders(userWithoutPermissionsToken)},
      );

      // then
      // The XML is resolved through the process definition, which requires
      // READ_PROCESS_DEFINITION on PROCESS_DEFINITION and rejects with 403.
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Get process definition XML - forbidden when READ_PROCESS_DEFINITION is granted on another process only', async ({
    request,
  }) => {
    // given
    const processDefinitionKey = restrictedProcess.processDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl(PROCESS_DEFINITION_XML_ENDPOINT, {processDefinitionKey}),
        {headers: jsonHeaders(userWithReadPermissionToken)},
      );

      // then
      // Read access to the readable process does not extend to the XML of the
      // restricted one.
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Get process definition start form - forbidden without READ_PROCESS_DEFINITION permission', async ({
    request,
  }) => {
    // given
    const processDefinitionKey = readableProcess.processDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl(PROCESS_DEFINITION_FORM_ENDPOINT, {processDefinitionKey}),
        {headers: jsonHeaders(userWithoutPermissionsToken)},
      );

      // then
      // The start form is resolved through the process definition, which
      // requires READ_PROCESS_DEFINITION on PROCESS_DEFINITION and rejects with 403.
      await assertForbiddenRequest(res, READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Get process definition start form - allowed with READ_PROCESS_DEFINITION and no form permission', async ({
    request,
  }) => {
    // given
    const processDefinitionKey = readableProcess.processDefinitionKey;

    await expect(async () => {
      // when
      const res = await request.get(
        buildUrl(PROCESS_DEFINITION_FORM_ENDPOINT, {processDefinitionKey}),
        {headers: jsonHeaders(userWithProcessReadOnlyToken)},
      );

      // then
      // Only READ_PROCESS_DEFINITION is checked; the linked form is returned
      // to a user holding no RESOURCE permission at all.
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.formId).toBe(LINKED_START_FORM_ID);

      // The same user is rejected when reading the form directly, proving
      // it has no form read permission.
      const formRes = await request.get(
        buildUrl('/forms/{formKey}', {formKey: body.formKey}),
        {headers: jsonHeaders(userWithProcessReadOnlyToken)},
      );
      await assertForbiddenRequest(formRes, FORM_READ_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Get process definition element statistics - empty without READ_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    const processDefinitionKey = readableProcess.processDefinitionKey;

    await expect(async () => {
      const adminBody = await getElementStatistics(
        request,
        processDefinitionKey,
      );
      expect(adminBody.items.length).toBeGreaterThan(0);

      // when
      const body = await getElementStatistics(
        request,
        processDefinitionKey,
        userWithoutPermissionsToken,
      );

      // then
      // Statistics require READ_PROCESS_INSTANCE on PROCESS_DEFINITION; without
      // it the element statistics are filtered out instead of rejected.
      expect(body.items).toHaveLength(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Get process definition element statistics - empty with READ_PROCESS_DEFINITION only', async ({
    request,
  }) => {
    // given
    const processDefinitionKey = readableProcess.processDefinitionKey;

    await expect(async () => {
      const adminBody = await getElementStatistics(
        request,
        processDefinitionKey,
      );
      expect(adminBody.items.length).toBeGreaterThan(0);

      // when
      const body = await getElementStatistics(
        request,
        processDefinitionKey,
        userWithReadPermissionToken,
      );

      // then
      // Reading the process definition does not grant READ_PROCESS_INSTANCE,
      // so the instance-based statistics stay empty.
      expect(body.items).toHaveLength(0);
    }).toPass(defaultAssertionOptions);
  });
});
