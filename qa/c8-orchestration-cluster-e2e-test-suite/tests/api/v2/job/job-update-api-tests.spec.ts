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
  setupProcessInstanceForTests,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe('Job Update API Tests', () => {
  const {beforeAll, beforeEach, afterEach} = setupProcessInstanceForTests(
    'job_api_process',
    'jobApiProcess',
  );
  test.beforeAll(beforeAll);
  test.beforeEach(beforeEach);
  test.afterEach(afterEach);

  test('Update Job - success', async ({request}) => {
    const jobKey = await activateJobToObtainAValidJobKey(
      request,
      'jobApiTaskType',
    );

    await test.step('PATCH update the job', async () => {
      const updateRes = await request.patch(buildUrl(`/jobs/${jobKey}`), {
        headers: jsonHeaders(),
        data: {
          changeset: {retries: 1, timeout: 9000},
        },
      });
      await assertStatusCode(updateRes, 204);
    });
  });

  test('Update Job - not found', async ({request}) => {
    const jobKey = 2251799813738612; // non-existing

    await test.step('Send update for non-existing job', async () => {
      const updateRes = await request.patch(buildUrl(`/jobs/${jobKey}`), {
        headers: jsonHeaders(),
        data: {
          changeset: {retries: 0, timeout: 0},
          operationReference: 0,
        },
      });

      await assertNotFoundRequest(
        updateRes,
        `Command 'UPDATE' rejected with code 'NOT_FOUND': Expected to update job with key '${jobKey}', but no such job was found`,
      );
    });
  });

  test('Update Job - invalid request', async ({request}) => {
    const jobKey = 2251799813738612; // non-existing

    await test.step('Send invalid payload to provoke a bad request', async () => {
      const updateRes = await request.patch(buildUrl(`/jobs/${jobKey}`), {
        headers: jsonHeaders(),
        data: {
          changeset: {retries: 'zero', timeout: 'zero'},
          operationReference: 'invalid',
        },
      });
      await assertBadRequest(updateRes, '');
    });
  });
});
