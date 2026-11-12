/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {
  activateJobToObtainAValidJobKey,
  activateSingleJob,
  setupProcessInstanceForTests,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe('Job Fail API Tests', () => {
  const {state, beforeAll, beforeEach, afterEach} =
    setupProcessInstanceForTests('job_api_process', 'jobApiProcess');
  test.beforeAll(beforeAll);
  test.beforeEach(beforeEach);
  test.afterEach(afterEach);

  test('Fail Job - success', async ({request}) => {
    const jobKey = await activateJobToObtainAValidJobKey(
      request,
      'jobApiTaskType',
    );

    // Now fail the job
    const failRes = await request.post(buildUrl(`/jobs/${jobKey}/failure`), {
      headers: jsonHeaders(),
      data: {
        retries: 0,
        errorMessage: 'Simulated failure',
      },
    });

    await assertStatusCode(failRes, 204);
  });

  test('Fail Job - Job not found', async ({request}) => {
    const jobKey = 2251799813738612;

    const failRes = await request.post(buildUrl(`/jobs/${jobKey}/failure`), {
      headers: jsonHeaders(),
      data: {
        retries: 2,
        errorMessage: 'Simulated failure',
      },
    });
    await assertNotFoundRequest(
      failRes,
      `Command 'FAIL' rejected with code 'NOT_FOUND': Expected to fail job with key '${jobKey}', but no such job was found`,
    );
  });

  test('Fail Job - invalid request', async ({request}) => {
    const jobKey = 2251799813738612;

    const failRes = await request.post(buildUrl(`/jobs/${jobKey}/failure`), {
      headers: jsonHeaders(),
      data: {
        retries: '2', // Wrong type
        errorMessage: 2, // wrong type
      },
    });
    await assertBadRequest(failRes, '');
  });

  test('Fail Job - 409', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await test.step('First activate a job', async () => {
      // Scope activation to this test's own process instance. 'jobApiTaskType'
      // is shared by the sibling job-* specs that run on parallel workers, so
      // activating "any" job of that type can hand back a job owned by another
      // test, whose afterEach then cancels the instance and deletes the job
      // mid-test — turning the /failure calls below into 404s instead of the
      // expected 204/409. activateSingleJob filters by processInstanceKey and
      // retries until this instance's own job is activatable.
      localState['jobKey'] = await activateSingleJob(
        request,
        'jobApiTaskType',
        state['processInstanceKey'] as string,
      );
    });

    await test.step('fail the job for the first time', async () => {
      const failRes = await request.post(
        buildUrl(`/jobs/${localState['jobKey']}/failure`),
        {
          headers: jsonHeaders(),
          data: {
            retries: 0,
            errorMessage: 'Simulated failure',
          },
        },
      );
      await assertStatusCode(failRes, 204);
    });

    await test.step('fail the job for the second time', async () => {
      const failAgainRes = await request.post(
        buildUrl(`/jobs/${localState['jobKey']}/failure`),
        {
          headers: jsonHeaders(),
          data: {
            retries: 0,
            errorMessage: 'Simulated failure',
          },
        },
      );
      await assertStatusCode(failAgainRes, 409);
    });
  });
});
