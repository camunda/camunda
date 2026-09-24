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
  createUser,
  grantUserResourceAuthorization,
  setupProcessInstanceForTests,
} from '@requestHelpers';
import {searchJobKey} from '../../../../utils/requestHelpers/job-requestHelpers';

// Job command endpoints (complete/fail/throw-error/update) require
// permission UPDATE_PROCESS_INSTANCE on resource PROCESS_DEFINITION and are
// hard-rejected with 403 FORBIDDEN when the caller lacks it.
// See engine authorization tests JobComplete/Fail/UpdateAuthorizationTest.
const JOB_UPDATE_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'";

const runSuffix = randomUUID().slice(0, 8);
const processId = `jobApiProcess-permission-${runSuffix}`;
const taskType = `jobApiTaskType-permission-${runSuffix}`;

/* eslint-disable playwright/expect-expect */
test.describe.serial('Job Permission API', () => {
  const {state, beforeAll, beforeEach, afterEach} =
    setupProcessInstanceForTests('job_api_process', {
      processName: processId,
      substitutions: {jobApiProcess: processId, jobApiTaskType: taskType},
    });

  let restrictedUser: {
    username: string;
    name: string;
    email: string;
    password: string;
  };
  let restrictedToken: string;

  test.beforeAll(async ({request}) => {
    // given: a deployed job process and an authenticated user that only holds
    // READ on RESOURCE (no PROCESS_DEFINITION permissions) — i.e. can call the
    // API but is not authorized for job operations.
    await beforeAll();

    restrictedUser = await createUser(request);
    await grantUserResourceAuthorization(request, restrictedUser);
    restrictedToken = encode(
      `${restrictedUser.username}:${restrictedUser.password}`,
    );
  });

  test.beforeEach(beforeEach);

  test.afterEach(afterEach);

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, [restrictedUser.username]);
  });

  test('Complete job - forbidden without UPDATE_PROCESS_INSTANCE', async ({
    request,
  }) => {
    // given: a valid, activated job (activated by the admin worker)
    const jobKey = await activateJobToObtainAValidJobKey(request, taskType);

    // when/then: the restricted user attempts to complete it -> 403
    await expect(async () => {
      const res = await request.post(buildUrl(`/jobs/${jobKey}/completion`), {
        headers: jsonHeaders(restrictedToken),
        data: {},
      });
      await assertForbiddenRequest(res, JOB_UPDATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Fail job - forbidden without UPDATE_PROCESS_INSTANCE', async ({
    request,
  }) => {
    const jobKey = await activateJobToObtainAValidJobKey(request, taskType);

    await expect(async () => {
      const res = await request.post(buildUrl(`/jobs/${jobKey}/failure`), {
        headers: jsonHeaders(restrictedToken),
        data: {retries: 0, errorMessage: 'Simulated failure'},
      });
      await assertForbiddenRequest(res, JOB_UPDATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Throw job error - forbidden without UPDATE_PROCESS_INSTANCE', async ({
    request,
  }) => {
    const jobKey = await activateJobToObtainAValidJobKey(request, taskType);

    await expect(async () => {
      const res = await request.post(buildUrl(`/jobs/${jobKey}/error`), {
        headers: jsonHeaders(restrictedToken),
        data: {errorCode: 'ERROR_CODE_1'},
      });
      await assertForbiddenRequest(res, JOB_UPDATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Update job - forbidden without UPDATE_PROCESS_INSTANCE', async ({
    request,
  }) => {
    const jobKey = await activateJobToObtainAValidJobKey(request, taskType);

    await expect(async () => {
      const res = await request.patch(buildUrl(`/jobs/${jobKey}`), {
        headers: jsonHeaders(restrictedToken),
        data: {changeset: {retries: 1, timeout: 9000}},
      });
      await assertForbiddenRequest(res, JOB_UPDATE_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Search jobs - filters out unauthorized results', async ({request}) => {
    // Job search requires READ_PROCESS_INSTANCE on PROCESS_DEFINITION, but per
    // the REST API docs unauthorized results are *filtered* (not rejected).
    // given: the admin can see the instance's job (i.e. it is indexed)
    const processInstanceKey = state['processInstanceKey'] as string;
    await searchJobKey(request, processInstanceKey);

    // when: the restricted user searches with the same filter
    const res = await request.post(buildUrl('/jobs/search'), {
      headers: jsonHeaders(restrictedToken),
      data: {filter: {processInstanceKey}},
    });

    // then: the request succeeds but the unauthorized job is filtered out
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.items).toHaveLength(0);
  });

  test('Complete job - unauthorized without authentication', async ({
    request,
  }) => {
    const jobKey = await activateJobToObtainAValidJobKey(request, taskType);

    const res = await request.post(buildUrl(`/jobs/${jobKey}/completion`), {
      headers: jsonHeaders(''),
      data: {},
    });
    await assertUnauthorizedRequest(res);
  });

  test('Search jobs - unauthorized without authentication', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/jobs/search'), {
      headers: jsonHeaders(''),
      data: {},
    });
    await assertUnauthorizedRequest(res);
  });
});
