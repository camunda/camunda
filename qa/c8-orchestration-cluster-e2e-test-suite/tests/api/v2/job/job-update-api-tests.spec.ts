/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';
import {randomUUID} from 'crypto';
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

const runSuffix = randomUUID().slice(0, 8);
const processId = `jobApiProcess-update-${runSuffix}`;
const taskType = `jobApiTaskType-update-${runSuffix}`;

/* eslint-disable playwright/expect-expect */
test.describe('Job Update API Tests', () => {
  const {beforeAll, beforeEach, afterEach} = setupProcessInstanceForTests(
    'job_api_process',
    {
      processName: processId,
      substitutions: {jobApiProcess: processId, jobApiTaskType: taskType},
    },
  );

  test.beforeAll(beforeAll);

  test.beforeEach(beforeEach);

  test.afterEach(afterEach);

  test('Update Job - success', async ({request}) => {
    const jobKey = await activateJobToObtainAValidJobKey(request, taskType);

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

  test('Update Job - invalid operationReference', async ({request}) => {
    const jobKey = 2251799813738612; // non-existing

    await test.step('Send update with invalid operationReference', async () => {
      const updateRes = await request.patch(buildUrl(`/jobs/${jobKey}`), {
        headers: jsonHeaders(),
        data: {
          changeset: {retries: 0, timeout: 0},
          operationReference: 0,
        },
      });

      await assertBadRequest(
        updateRes,
        "The value for operationReference is '0' but must be > 0.",
        'INVALID_ARGUMENT',
      );
    });
  });

  test('Update Job - not found', async ({request}) => {
    const jobKey = 2251799813738612; // non-existing

    await test.step('Send update for non-existing job', async () => {
      const updateRes = await request.patch(buildUrl(`/jobs/${jobKey}`), {
        headers: jsonHeaders(),
        data: {
          changeset: {retries: 0, timeout: 0},
          operationReference: 1,
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
