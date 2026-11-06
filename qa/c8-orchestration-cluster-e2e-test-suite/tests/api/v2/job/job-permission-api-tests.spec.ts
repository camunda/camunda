/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {randomUUID} from 'crypto';
import {
  cancelProcessInstance,
  createInstances,
  deployWithSubstitutions,
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
  activateJobToObtainAValidJobKey,
  createComponentAuthorization,
  createUser,
  grantUserResourceAuthorization,
} from '@requestHelpers';
import {searchJobKey} from '../../../../utils/requestHelpers/job-requestHelpers';

const runSuffix = randomUUID().slice(0, 8);
// The restricted users hold no permission at all on this process.
const restrictedProcessId = `jobPermissionRestricted-${runSuffix}`;
const restrictedTaskType = `jobPermissionRestrictedTask-${runSuffix}`;
// The partially authorized user may read instances of this process, but not update them.
const readableProcessId = `jobPermissionReadable-${runSuffix}`;
const readableTaskType = `jobPermissionReadableTask-${runSuffix}`;

const JOB_SEARCH_ENDPOINT = '/jobs/search';
const UPDATE_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'";

type TestUser = {
  username: string;
  name: string;
  email: string;
  password: string;
};

test.describe('Job Permission API - Forbidden', () => {
  let createdProcessInstanceKeys: string[] = [];

  let userWithoutPermissions: TestUser;
  let userWithoutPermissionsToken: string;
  let userWithReadPermission: TestUser;
  let userWithReadPermissionToken: string;

  async function startInstance(processId: string): Promise<string> {
    const [instance] = await createInstances(processId, 1, 1);
    createdProcessInstanceKeys.push(instance.processInstanceKey);
    return instance.processInstanceKey;
  }

  test.beforeAll(async ({request}) => {
    await deployWithSubstitutions('./resources/job_api_process.bpmn', {
      jobApiProcess: restrictedProcessId,
      jobApiTaskType: restrictedTaskType,
    });
    await deployWithSubstitutions('./resources/job_api_process.bpmn', {
      jobApiProcess: readableProcessId,
      jobApiTaskType: readableTaskType,
    });

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
      permissionTypes: ['READ_PROCESS_INSTANCE'],
    });
    userWithReadPermissionToken = encode(
      `${userWithReadPermission.username}:${userWithReadPermission.password}`,
    );
  });

  test.afterEach(async () => {
    for (const processInstanceKey of createdProcessInstanceKeys) {
      await cancelProcessInstance(processInstanceKey);
    }
    createdProcessInstanceKeys = [];
  });

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, [
      userWithoutPermissions.username,
      userWithReadPermission.username,
    ]);
  });

  test('Activate jobs - filtered out without UPDATE_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    const processInstanceKey = await startInstance(restrictedProcessId);
    await searchJobKey(request, processInstanceKey);

    // when
    const res = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(userWithoutPermissionsToken),
      data: {
        type: restrictedTaskType,
        timeout: 10_000,
        maxJobsToActivate: 1,
        requestTimeout: 1_000,
      },
    });

    // then
    // Activation requires UPDATE_PROCESS_INSTANCE on PROCESS_DEFINITION; jobs
    // the caller may not update are left out of the batch instead of rejected.
    await assertStatusCode(res, 200);
    const body = await res.json();
    expect(body.jobs).toHaveLength(0);
    // The job was not consumed by the unauthorized request.
    await activateJobToObtainAValidJobKey(request, restrictedTaskType);
  });

  test('Search jobs - filtered out without READ_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    const processInstanceKey = await startInstance(restrictedProcessId);
    await searchJobKey(request, processInstanceKey);

    await expect(async () => {
      // when
      const res = await request.post(buildUrl(JOB_SEARCH_ENDPOINT), {
        headers: jsonHeaders(userWithoutPermissionsToken),
        data: {filter: {processInstanceKey}},
      });

      // then
      // Search requires READ_PROCESS_INSTANCE on PROCESS_DEFINITION; without it
      // the job is filtered from the result instead of rejected.
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.items).toHaveLength(0);
      expect(body.page.totalItems).toBe(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search jobs - only jobs of readable process definitions returned', async ({
    request,
  }) => {
    // given
    const restrictedInstanceKey = await startInstance(restrictedProcessId);
    const readableInstanceKey = await startInstance(readableProcessId);
    const restrictedJobKey = await searchJobKey(request, restrictedInstanceKey);
    const readableJobKey = await searchJobKey(request, readableInstanceKey);

    await expect(async () => {
      // when
      const res = await request.post(buildUrl(JOB_SEARCH_ENDPOINT), {
        headers: jsonHeaders(userWithReadPermissionToken),
        data: {
          filter: {
            processInstanceKey: {
              $in: [restrictedInstanceKey, readableInstanceKey],
            },
          },
        },
      });

      // then
      // READ_PROCESS_INSTANCE is granted on the readable process only, so the
      // job of the restricted process is filtered out.
      await assertStatusCode(res, 200);
      const body = await res.json();
      const jobKeys = body.items.map((item: {jobKey: string}) => item.jobKey);
      expect(jobKeys).toContain(readableJobKey);
      expect(jobKeys).not.toContain(restrictedJobKey);
    }).toPass(defaultAssertionOptions);
  });

  test('Complete job - forbidden without UPDATE_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    await startInstance(restrictedProcessId);
    const jobKey = await activateJobToObtainAValidJobKey(
      request,
      restrictedTaskType,
    );

    await expect(async () => {
      // when
      const res = await request.post(
        buildUrl('/jobs/{jobKey}/completion', {jobKey}),
        {headers: jsonHeaders(userWithoutPermissionsToken), data: {}},
      );

      // then
      // Completion requires UPDATE_PROCESS_INSTANCE on PROCESS_DEFINITION and
      // rejects with 403 when it is missing.
      await assertForbiddenRequest(res, UPDATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Fail job - forbidden without UPDATE_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    await startInstance(restrictedProcessId);
    const jobKey = await activateJobToObtainAValidJobKey(
      request,
      restrictedTaskType,
    );

    await expect(async () => {
      // when
      const res = await request.post(
        buildUrl('/jobs/{jobKey}/failure', {jobKey}),
        {
          headers: jsonHeaders(userWithoutPermissionsToken),
          data: {retries: 0, errorMessage: 'Simulated failure'},
        },
      );

      // then
      // Failing a job requires UPDATE_PROCESS_INSTANCE on PROCESS_DEFINITION
      // and rejects with 403 when it is missing.
      await assertForbiddenRequest(res, UPDATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Throw error for job - forbidden without UPDATE_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    await startInstance(restrictedProcessId);
    const jobKey = await activateJobToObtainAValidJobKey(
      request,
      restrictedTaskType,
    );

    await expect(async () => {
      // when
      const res = await request.post(
        buildUrl('/jobs/{jobKey}/error', {jobKey}),
        {
          headers: jsonHeaders(userWithoutPermissionsToken),
          data: {errorCode: 'ERROR_CODE_1', errorMessage: 'Simulated error'},
        },
      );

      // then
      // Throwing an error requires UPDATE_PROCESS_INSTANCE on
      // PROCESS_DEFINITION and rejects with 403 when it is missing.
      await assertForbiddenRequest(res, UPDATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Update job - forbidden without UPDATE_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given
    await startInstance(restrictedProcessId);
    const jobKey = await activateJobToObtainAValidJobKey(
      request,
      restrictedTaskType,
    );

    await expect(async () => {
      // when
      const res = await request.patch(buildUrl('/jobs/{jobKey}', {jobKey}), {
        headers: jsonHeaders(userWithoutPermissionsToken),
        data: {changeset: {retries: 3}},
      });

      // then
      // Updating a job requires UPDATE_PROCESS_INSTANCE on PROCESS_DEFINITION
      // and rejects with 403 when it is missing.
      await assertForbiddenRequest(res, UPDATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Complete job - forbidden with READ_PROCESS_INSTANCE permission only', async ({
    request,
  }) => {
    // given
    await startInstance(readableProcessId);
    const jobKey = await activateJobToObtainAValidJobKey(
      request,
      readableTaskType,
    );

    await expect(async () => {
      // when
      const res = await request.post(
        buildUrl('/jobs/{jobKey}/completion', {jobKey}),
        {headers: jsonHeaders(userWithReadPermissionToken), data: {}},
      );

      // then
      // READ_PROCESS_INSTANCE does not imply UPDATE_PROCESS_INSTANCE, so the
      // user who may read the job still cannot complete it.
      await assertForbiddenRequest(res, UPDATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Complete job - unauthorized without credentials', async ({request}) => {
    // given
    await startInstance(restrictedProcessId);
    const jobKey = await activateJobToObtainAValidJobKey(
      request,
      restrictedTaskType,
    );

    // when
    const res = await request.post(
      buildUrl('/jobs/{jobKey}/completion', {jobKey}),
      {data: {}},
    );

    // then
    await assertUnauthorizedRequest(res);
  });
});
