/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';
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

test.describe.parallel('Job Completion API Tests', () => {
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

  test('Complete Job - success', async ({request}) => {
    const activateRes = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(),
      data: {
        type: 'anotherTask',
        timeout: 10000,
        maxJobsToActivate: 1,
      },
    });
    await assertStatusCode(activateRes, 200);
    const activateJson = await activateRes.json();
    const jobKey = activateJson.jobs[0].jobKey;

    // Complete the job
    const completeRes = await request.post(
      buildUrl(`/jobs/${jobKey}/completion`),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );
    await assertStatusCode(completeRes, 204);
  });

  test('Complete Job - not found', async ({request}) => {
    const jobKey = 2251799813738612; // Assuming this job key does not exist

    const completeRes = await request.post(
      buildUrl(`/jobs/${jobKey}/completion`),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );
    await assertNotFoundRequest(
      completeRes,
      `Command 'COMPLETE' rejected with code 'NOT_FOUND': Expected to complete job with key '${jobKey}', but no such job was found`,
    );
  });

  test('Complete Job - invalid request', async ({request}) => {
    const jobKey = 2251799813738612; // Assuming this job key does not exist

    // Send an invalid payload to provoke a bad request
    const completeRes = await request.post(
      buildUrl(`/jobs/${jobKey}/completion`),
      {
        headers: jsonHeaders(),
        data: {
          unexpectedField: 123,
        },
      },
    );
    await assertBadRequest(completeRes, '');
  });

  test('Complete Job - conflict 409', async ({request}) => {
    // Activate to obtain jobKey
    const activateRes = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(),
      data: {
        type: 'finalTask',
        timeout: 1000,
        maxJobsToActivate: 1,
      },
    });
    await assertStatusCode(activateRes, 200);
    const activateJson = await activateRes.json();
    const jobKey = activateJson.jobs[0].jobKey;

    // First completion (should succeed)
    const completeRes = await request.post(
      buildUrl(`/jobs/${jobKey}/failure`),
      {
        headers: jsonHeaders(),
        data: {
          retries: 0,
          errorMessage: 'Simulated failure',
        },
      },
    );
    await assertStatusCode(completeRes, 204);

    // Second completion for the same job should conflict
    const completeAgainRes = await request.post(
      buildUrl(`/jobs/${jobKey}/completion`),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );
    await assertStatusCode(completeAgainRes, 409);
  });
});
