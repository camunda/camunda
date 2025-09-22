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
  createInstances,
  deploy,
} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';

test.describe.parallel('Job Update API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async () => {
    await deploy(['./resources/processWithThreeParallelTasks.bpmn']);
    const processInstance = await createInstances(
      'processWithThreeParallelTasks',
      1,
      1,
    );
    state['processInstanceKey'] = processInstance[0].processInstanceKey;
  });

  test.afterAll(async () => {
    await cancelProcessInstance(state['processInstanceKey'] as string);
  });

  test('Update Job - success', async ({request}) => {
    const activateRes = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(),
      data: {
        type: 'someTask',
        timeout: 10000,
        maxJobsToActivate: 1,
      },
    });
    expect(activateRes.status()).toBe(200);
    const activateJson = await activateRes.json();
    const jobKey = activateJson.jobs[0].jobKey;

    // PATCH update the job
    const updateRes = await request.patch(buildUrl(`/jobs/${jobKey}`), {
      headers: jsonHeaders(),
      data: {
        changeset: {retries: 1, timeout: 9000},
      },
    });
    await assertStatusCode(updateRes, 204);
  });

  test('Update Job - not found', async ({request}) => {
    const jobKey = 2251799813738612; // Assuming this job key does not exist

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

  test('Update Job - invalid request', async ({request}) => {
    const jobKey = 2251799813738612; // Assuming this job key does not exist

    // Send invalid payload (wrong types)
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
