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
  failJob,
  searchJobKeysForProcessInstance,
  throwErrorForJob,
} from '../../../../utils/requestHelpers';
import {
  assertNotFoundRequest,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Process Instance Search Incidents Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/processWithThreeParallelTasks.bpmn']);
  });

  test('Search Process Instances with Multiple Incidents - Success', async ({
    request,
  }) => {
    const localState: Record<string, unknown> = {};
    await test.step('Create process instances that will generate incidents', async () => {
      const processInstances = await createInstances(
        'processWithThreeParallelTasks',
        1,
        1,
      );
      localState['processInstanceKey'] = processInstances[0].processInstanceKey;
    });

    await test.step('Create incidents by cancelling and throwing errors on jobs', async () => {
      const processInstanceKey = localState['processInstanceKey'] as string;
      const foundJobKeys = await searchJobKeysForProcessInstance(
        request,
        processInstanceKey,
      );
      await failJob(request, foundJobKeys[0]);
      await failJob(request, foundJobKeys[1]);
      await throwErrorForJob(request, foundJobKeys[2], 'TEST_ERROR');
    });

    await test.step('Search Process Instances with Incidents', async () => {
      const processInstanceKey = localState['processInstanceKey'] as string;
      await test
        .expect(async () => {
          const searchResult = await request.post(
            buildUrl(
              `/process-instances/${processInstanceKey}/incidents/search`,
            ),
            {
              headers: jsonHeaders(),
              data: {},
            },
          );
          await validateResponse(
            {
              path: '/process-instances/{processInstanceKey}/incidents/search',
              method: 'POST',
              status: '200',
            },
            searchResult,
          );
          const json = await searchResult.json();
          expect(json.page.totalItems).toBe(3);
          expect(json.items[0].processInstanceKey).toBe(processInstanceKey);
          expect(json.items[1].processInstanceKey).toBe(processInstanceKey);
          expect(json.items[2].processInstanceKey).toBe(processInstanceKey);
          expect(json.items[0].errorType).toBe('JOB_NO_RETRIES');
          expect(json.items[1].errorType).toBe('JOB_NO_RETRIES');
          expect(json.items[2].errorType).toBe('UNHANDLED_ERROR_EVENT');
        })
        .toPass(defaultAssertionOptions);
    });
    await cancelProcessInstance(localState['processInstanceKey'] as string);
  });

  test('Search Process Instances with Incidents - Unauthorized', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(`/process-instances/2251799813685249/incidents/search`),
      {
        data: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Process Instances with Incidents - Not Found', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(`/process-instances/9999999999999/incidents/search`),
      {
        headers: jsonHeaders(),
        data: {},
      },
    );
    await assertNotFoundRequest(
      res,
      "Process Instance with key '9999999999999' not found",
    );
  });
});
