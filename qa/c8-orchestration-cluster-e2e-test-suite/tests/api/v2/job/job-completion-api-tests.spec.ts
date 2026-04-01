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
import {activateJobToObtainAValidJobKey} from '@requestHelpers';
import {defaultAssertionOptions} from '../../../../utils/constants';

// Running the job tests on the same process instance leads to conflicts
/* eslint-disable playwright/expect-expect */
test.describe('Job Completion API Tests', () => {
  const state: Record<string, unknown> = {};

  test.beforeAll(async () => {
    await deploy(['./resources/job_api_process.bpmn']);
    await deploy(['./resources/job_api_task_conflict.bpmn']);
  });

  test.beforeEach(async () => {
    const processInstance1 = await createInstances('jobApiProcess', 1, 1);
    const processInstance2 = await createInstances(
      'job_api_task_conflict',
      1,
      1,
    );

    state['processInstanceKey1'] = processInstance1[0].processInstanceKey;
    state['processInstanceKey2'] = processInstance2[0].processInstanceKey;
  });

  test.afterEach(async () => {
    if (!state['completed1']) {
      await cancelProcessInstance(state['processInstanceKey1'] as string);
    }
    if (!state['completed2']) {
      await cancelProcessInstance(state['processInstanceKey2'] as string);
    }
  });

  test('Complete Job - success', async ({request}) => {
    await expect(async () => {
      const jobKey = await activateJobToObtainAValidJobKey(
        request,
        'jobApiTaskType',
      );

      const completeRes = await request.post(
        buildUrl(`/jobs/${jobKey}/completion`),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );
      await assertStatusCode(completeRes, 204);
      state['completed1'] = true;
    }).toPass(defaultAssertionOptions);
  });

  test('Complete Job - not found', async ({request}) => {
    const jobKey = 2251799813738612; // Assuming this job key does not exist

    await test.step('Send complete request for non-existing job', async () => {
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
  });

  test('Complete Job - invalid request', async ({request}) => {
    await expect(async () => {
      const jobKey = 2251799813738612;

      await test.step('Send invalid payload to provoke a bad request', async () => {
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
    }).toPass(defaultAssertionOptions);
  });

  test('Complete Job - conflict 409', async ({request}) => {
    const localState: Record<string, unknown> = {};

    await test.step('Activate a job to obtain a valid job key', async () => {
      localState['jobKey'] = await activateJobToObtainAValidJobKey(
        request,
        'jobApiTaskTypeConflict',
      );
    });

    await test.step('First completion (should succeed)', async () => {
      await expect(async () => {
        const completeRes = await request.post(
          buildUrl(`/jobs/${localState['jobKey']}/failure`),
          {
            headers: jsonHeaders(),
            data: {
              retries: 0,
              errorMessage: 'Simulated failure',
            },
          },
        );
        await assertStatusCode(completeRes, 204);
        state['completed2'] = true;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Second completion (should conflict)', async () => {
      const completeAgainRes = await request.post(
        buildUrl(`/jobs/${localState['jobKey']}/completion`),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );
      await assertStatusCode(completeAgainRes, 409);
    });
  });
});
