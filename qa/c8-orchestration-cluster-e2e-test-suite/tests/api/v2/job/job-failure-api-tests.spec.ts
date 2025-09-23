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

test.describe('Job Fail API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async () => {
    await deploy(['./resources/processWithThreeParallelTasks.bpmn']);
  });

  test.beforeEach(async () => {
    const processInstance = await createInstances(
      'processWithThreeParallelTasks',
      1,
      1,
    );
    state['processInstanceKey'] = processInstance[0].processInstanceKey;
  });

  test.afterEach(async () => {
    await cancelProcessInstance(state['processInstanceKey'] as string);
  });

  test('Fail Job - success', async ({request}) => {
    // First activate a job to get a valid job key
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
    const jobKey = 2251799813738612; // Assuming this job key does not exist

    // Now fail the job
    const failRes = await request.post(buildUrl(`/jobs/${jobKey}/failure`), {
      headers: jsonHeaders(),
      data: {
        retries: 2,
        errorMessage: 'Simulated failure',
      },
    });
    console.log(failRes);
    await assertNotFoundRequest(
      failRes,
      `Command 'FAIL' rejected with code 'NOT_FOUND': Expected to fail job with key '${jobKey}', but no such job was found`,
    );
  });

  test('Fail Job - invalid request', async ({request}) => {
    const jobKey = 2251799813738612; // Assuming this job key does not exist

    // Now fail the job
    const failRes = await request.post(buildUrl(`/jobs/${jobKey}/failure`), {
      headers: jsonHeaders(),
      data: {
        retries: '2', // Wrong type
        errorMessage: 2, // wrong type
      },
    });
    console.log(failRes);
    await assertBadRequest(failRes, '');
  });

  test('Fail Job - 409', async ({request}) => {
    // First activate a job to get a valid job key
    const activateRes = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(),
      data: {
        type: 'finalTask',
        timeout: 1_000,
        maxJobsToActivate: 1,
      },
    });
    await assertStatusCode(activateRes, 200);
    const json = await activateRes.json();
    const jobKey = json.jobs[0].jobKey;

    // Now throw error for the job (first time)
    const failRes = await request.post(buildUrl(`/jobs/${jobKey}/failure`), {
      headers: jsonHeaders(),
      data: {
        retries: 0,
        errorMessage: 'Simulated failure',
      },
    });
    await assertStatusCode(failRes, 204);

    // Now fail again the job
    const failAgainRes = await request.post(
      buildUrl(`/jobs/${jobKey}/failure`),
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
