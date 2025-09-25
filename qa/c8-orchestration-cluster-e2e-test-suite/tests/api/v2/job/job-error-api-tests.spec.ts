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
import {activateJobToObtainAValidJobKey} from '../../../../utils/requestHelpers';

test.describe('Job Error API Tests', () => {
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

  test('Throw Error for Job - success', async ({request}) => {
    const jobKey = await activateJobToObtainAValidJobKey(request, 'someTask');

    const errorRes = await request.post(buildUrl(`/jobs/${jobKey}/error`), {
      headers: jsonHeaders(),
      data: {
        errorCode: 'ERROR_CODE_1',
        errorMessage: 'Simulated Error',
      },
    });

    await assertStatusCode(errorRes, 204);
  });

  test('Throw Error for Job - not found', async ({request}) => {
    const jobKey = 2251799813738612;

    const errorRes = await request.post(buildUrl(`/jobs/${jobKey}/error`), {
      headers: jsonHeaders(),
      data: {
        errorCode: 'ERROR_CODE_NOT_FOUND',
        errorMessage: 'Simulated error',
      },
    });
    await assertNotFoundRequest(
      errorRes,
      `Command 'THROW_ERROR' rejected with code 'NOT_FOUND': Expected to throw an error for job with key '2251799813738612', but no such job was found`,
    );
  });

  test('Throw Error for Job - invalid request', async ({request}) => {
    const jobKey = 2251799813738612;

    const errorRes = await request.post(buildUrl(`/jobs/${jobKey}/error`), {
      headers: jsonHeaders(),
      data: {
        errorCode: 2, // Wrong type
        errorMessage: 2, // wrong type
      },
    });
    await assertBadRequest(errorRes, '');
  });

  test('Throw Error for Job - conflict 409', async ({request}) => {
    const localState: Record<string, unknown> = {};

    await test.step('Activate job to obtain a valid job key', async () => {
      localState['jobKey'] = await activateJobToObtainAValidJobKey(
        request,
        'finalTask',
      );
    });

    await test.step('Throw error for the job (first time) and expect 204', async () => {
      const jobKey = localState['jobKey'] as number;
      const errorRes = await request.post(buildUrl(`/jobs/${jobKey}/error`), {
        headers: jsonHeaders(),
        data: {
          errorCode: 'SIMULATED',
          errorMessage: 'Simulated failure',
        },
      });
      await assertStatusCode(errorRes, 204);
    });

    await test.step('Throw error again for the same job (should conflict 409)', async () => {
      const jobKey = localState['jobKey'] as number;
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
});
