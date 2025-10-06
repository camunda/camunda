/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Get Process instance Sequence Flows Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/process_with_task_listener.bpmn']);
  });

  test('Get Process Instance Sequence Flows - Success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await test.step('First, create a process instance for the sequence flow', async () => {
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

    await test.step('Get Process Instance Sequence Flows', async () => {
      await expect(async () => {
        const getResponse = await request.get(
          buildUrl(
            `/process-instances/${localState['processInstanceKey']}/sequence-flows`,
          ),
          {
            headers: jsonHeaders(),
          },
        );

        await assertStatusCode(getResponse, 200);
        await validateResponse(
          {
            path: '/process-instances/{processInstanceKey}/sequence-flows',
            method: 'GET',
            status: '200',
          },
          getResponse,
        );
        const json = await getResponse.json();
        expect(json).toBeDefined();
        expect(json.items.length).toBe(1);
        expect(json.items[0].processInstanceKey).toBe(
          localState['processInstanceKey'],
        );
        expect(json.items[0].elementId).toBe('Flow_1lswdef');
        expect(json.items[0].processDefinitionId).toBe(
          'process_with_task_listener',
        );
        expect(json.items[0].processDefinitionKey).toBeDefined();
      }).toPass(defaultAssertionOptions);
      await cancelProcessInstance(localState.processInstanceKey as string);
    });
  });

  test('Get Process Instance Sequence Flows - Unauthorized', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl(`/process-instances/2251799813685249/sequence-flows`),
      {
        // No auth headers
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Process Instance Sequence Flows - No Items', async ({request}) => {
    const res = await request.get(
      buildUrl(`/process-instances/1111799813685211/sequence-flows`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.items.length).toBe(0);
  });

  test('Get Process Instance Sequence Flows - Bad Request', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl(`/process-instances/invalid-key/sequence-flows`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertBadRequest(
      res,
      "Failed to convert 'processInstanceKey' with value: 'invalid-key'",
    );
  });
});
