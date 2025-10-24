/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIResponse, expect, test} from '@playwright/test';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Get Process Instance Statistics Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/process_with_task_listener.bpmn']);
  });

  test('Get Process Instance Statistics - Success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await test.step('First, create a process instance to get the statistics for', async () => {
      const res = await request.post(buildUrl('/process-instances'), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: 'process_with_task_listener',
        },
      });

      await assertStatusCode(res, 200);
      const json = await res.json();
      localState['processInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Get Process Instance Statistics', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl(
            `/process-instances/${localState['processInstanceKey']}/statistics/element-instances`,
          ),
          {
            headers: jsonHeaders(),
          },
        );

        await validateResponse(
          {
            path: '/process-instances/{processInstanceKey}/statistics/element-instances',
            method: 'GET',
            status: '200',
          },
          res,
        );
        localState['response'] = res;
        const json = await (localState['response'] as APIResponse).json();
        expect(json.items).toHaveLength(2);
        expect(json.items[0].elementId).toEqual('Activity_1xci2nh');
        expect(json.items[0].active).toBe(1);
        expect(json.items[0].canceled).toBe(0);
        expect(json.items[0].completed).toBe(0);
        expect(json.items[0].incidents).toBe(0);
        expect(json.items[1].elementId).toEqual('StartEvent_1');
        expect(json.items[1].active).toBe(0);
        expect(json.items[1].canceled).toBe(0);
        expect(json.items[1].completed).toBe(1);
        expect(json.items[1].incidents).toBe(0);
      }).toPass(defaultAssertionOptions);
      await cancelProcessInstance(localState.processInstanceKey as string);
    });
  });

  test('Get Process Instance Statistics - Unauthorized', async ({request}) => {
    const res = await request.get(
      buildUrl(
        `/process-instances/2251799813685249/statistics/element-instances`,
      ),
      {
        // No auth headers
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Process Instance Statistics - No Items Found', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl(
        `/process-instances/2251799813685249/statistics/element-instances`,
      ),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.items).toHaveLength(0);
  });

  test('Get Process Instance Statistics - Bad Request', async ({request}) => {
    const res = await request.get(
      buildUrl(`/process-instances/invalid-key/statistics/element-instances`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertBadRequest(
      res,
      `Failed to convert 'processInstanceKey' with value: 'invalid-key'`,
    );
  });
});
