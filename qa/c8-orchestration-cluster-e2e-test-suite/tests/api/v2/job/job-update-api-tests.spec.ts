/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
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
import {defaultAssertionOptions} from '../../../../utils/constants';

/* eslint-disable playwright/expect-expect */
test.describe('Job Update API Tests', () => {
  // Use a dedicated process definition and job type unique to this file. The
  // job API specs run in parallel (one worker per file) and activation grabs
  // any available job of a given type across the cluster, so sharing a single
  // 'jobApiTaskType' across job specs let another spec consume or cancel the
  // job this test just activated — surfacing as a persistent 404 on the update.
  // A per-file type keeps this file's job pool isolated.
  const {beforeAll, beforeEach, afterEach} = setupProcessInstanceForTests(
    'job_update_api_process',
    'jobUpdateApiProcess',
  );

  test.beforeAll(beforeAll);

  test.beforeEach(beforeEach);

  test.afterEach(afterEach);

  test('Update Job - success', async ({request}) => {
    test.skip(
      process.env.FORWARD_COMPAT_MODE === 'true',
      'Skipped in forward-compat mode - job update endpoint behavior changed on main',
    );
    const jobKey = await activateJobToObtainAValidJobKey(
      request,
      'jobUpdateApiTaskType',
    );

    await test.step('PATCH update the job', async () => {
      // Job activation is observed before the job is fully visible to the
      // /jobs/{jobKey} endpoint. Retry on 404 until visibility catches up.
      await expect(async () => {
        const updateRes = await request.patch(buildUrl(`/jobs/${jobKey}`), {
          headers: jsonHeaders(),
          data: {
            changeset: {retries: 1, timeout: 9000},
          },
        });
        await assertStatusCode(updateRes, 204);
      }).toPass(defaultAssertionOptions);
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
