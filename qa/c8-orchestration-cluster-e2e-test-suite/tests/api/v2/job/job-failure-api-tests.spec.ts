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
  createInstanceOnceDeployed,
  setupProcessInstanceForTests,
} from '@requestHelpers';
import {
  cancelProcessInstance,
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {uniquePrefixedId} from '../../../../utils/constants';

/* eslint-disable playwright/expect-expect */
test.describe('Job Fail API Tests', () => {
  const {beforeAll, beforeEach, afterEach} = setupProcessInstanceForTests(
    'job_api_process',
    'jobApiProcess',
  );
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
});

// 'Fail Job - 409' owns everything it touches: its own process definition,
// job type and instance. It is deliberately outside the describe above so the
// shared hooks do not also start a 'jobApiProcess' instance for it — the
// parallel completion spec can finish that shared instance, and the shared
// teardown then cancels an instance that no longer exists.
test.describe('Job Fail API Tests', () => {
  const isolatedInstances: string[] = [];

  test.afterEach(async () => {
    for (const processInstanceKey of isolatedInstances.splice(0)) {
      await cancelProcessInstance(processInstanceKey, {ignoreNotFound: true});
    }
  });

  test('Fail Job - 409', async ({request}) => {
    const localState: Record<string, unknown> = {};
    // The shared 'jobApiProcess'/'jobApiTaskType' pair is deployed by four
    // sibling job-* specs that run on parallel workers. Activating from that
    // shared pool hands back whichever job is activatable, so another test's
    // afterEach could cancel the instance and delete the job mid-test (404
    // instead of the expected 204/409), and this test's own job could be
    // activated and completed by the parallel completion spec. A per-test
    // process id and job type remove the shared ownership entirely.
    const processDefinitionId = uniquePrefixedId('jobFail409Process');
    const jobType = uniquePrefixedId('jobFail409TaskType');

    await test.step('Deploy an isolated process and activate its job', async () => {
      await deployWithSubstitutions('./resources/job_api_process.bpmn', {
        jobApiProcess: processDefinitionId,
        'type="jobApiTaskType"': `type="${jobType}"`,
      });
      const instance = await createInstanceOnceDeployed(processDefinitionId, 1);
      isolatedInstances.push(instance.processInstanceKey);
      localState['jobKey'] = await activateSingleJob(
        request,
        jobType,
        instance.processInstanceKey,
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
