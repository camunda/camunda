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

test.describe.parallel('Job Error API Tests', () => {
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

  test('Throw Error for Job - success', async ({request}) => {
    const activateRes = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(),
      data: {
        type: 'someTask',
        timeout: 10000,
        maxJobsToActivate: 1,
      },
    });
    await assertStatusCode(activateRes, 200);
    const activateJson = await activateRes.json();
    const jobKey = activateJson.jobs[0].jobKey;

    // WHEN
    const errorRes = await request.post(buildUrl(`/jobs/${jobKey}/error`), {
      headers: jsonHeaders(),
      data: {
        errorCode: 'ERROR_CODE_1',
        errorMessage: 'Simulated Error',
      },
    });

    // THEN
    await assertStatusCode(errorRes, 204);
  });

  test('Throw Error for Job - not found', async ({request}) => {
    const jobKey = 2251799813738612; // Assuming this job key does not exist

    // Now throw error for the job (use error API)
    const errorRes = await request.post(buildUrl(`/jobs/${jobKey}/error`), {
      headers: jsonHeaders(),
      data: {
        errorCode: 'ERROR_CODE_NOT_FOUND',
        errorMessage: 'Simulated error',
      },
    });
    console.log(errorRes);
    await assertNotFoundRequest(
      errorRes,
      `Command 'THROW_ERROR' rejected with code 'NOT_FOUND': Expected to throw an error for job with key '2251799813738612', but no such job was found`,
    );
  });

  test('Throw Error for Job - invalid request', async ({request}) => {
    const jobKey = 2251799813738612; // Assuming this job key does not exist

    // Now throw error for the job with invalid payload (wrong types)
    const errorRes = await request.post(buildUrl(`/jobs/${jobKey}/error`), {
      headers: jsonHeaders(),
      data: {
        errorCode: 2, // Wrong type
        errorMessage: 2, // wrong type
      },
    });
    console.log(errorRes);
    await assertBadRequest(errorRes, '');
  });

  test('Throw Error for Job - conflict 409', async ({request}) => {
    const activateRes = await request.post(buildUrl('/jobs/activation'), {
      headers: jsonHeaders(),
      data: {
        type: 'finalTask',
        timeout: 1000,
        maxJobsToActivate: 1,
      },
    });
    expect(activateRes.status()).toBe(200);
    const activateJson = await activateRes.json();
    const jobKey = activateJson.jobs[0].jobKey;

    // Now throw error for the job (first time)
    const errorRes = await request.post(buildUrl(`/jobs/${jobKey}/error`), {
      headers: jsonHeaders(),
      data: {
        errorCode: 'SIMULATED',
        errorMessage: 'Simulated failure',
      },
    });
    expect(errorRes.status()).toBe(204);

    // Now throw error again for the same job (should conflict)
    const errorAgainRes = await request.post(
      buildUrl(`/jobs/${jobKey}/error`),
      {
        headers: jsonHeaders(),
        data: {
          errorCode: 'SIMULATED',
          errorMessage: 'Simulated failure',
        },
      },
    );
    expect(errorAgainRes.status()).toBe(409);
  });
});
