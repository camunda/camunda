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
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Get Process Instance Call Hierarchy Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/call_user_task_process_process.bpmn']);
  });

  test('Get Process Instance Call Hierarchy - Success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await test.step('First, create a process instance', async () => {
      const res = await request.post(buildUrl('/process-instances'), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: 'call_user_task_process_process',
        },
      });

      await assertStatusCode(res, 200);
      const json = await res.json();
      localState['processInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Search Called Process with parent process instance key', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/process-instances/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              parentProcessInstanceKey: localState['processInstanceKey'],
            },
          },
        });
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.page.totalItems).toBe(1);
        localState['childProcessInstanceKey'] =
          json.items[0].processInstanceKey;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Get Process Instance Call Hierarchy', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl(
            `/process-instances/${localState['childProcessInstanceKey']}/call-hierarchy`,
          ),
          {
            headers: jsonHeaders(),
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/process-instances/{processInstanceKey}/call-hierarchy',
            method: 'GET',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        await validateResponse(
          {
            path: '/process-instances/{processInstanceKey}/call-hierarchy',
            method: 'GET',
            status: '200',
          },
          res,
        );
        expect(json.length).toBe(2);
        expect(json[0].processInstanceKey).toBe(
          localState['processInstanceKey'],
        );
        expect(json[0].processDefinitionName).toBe(
          'Call User-task-process Process',
        );
        expect(json[1].processInstanceKey).toBe(
          localState['childProcessInstanceKey'],
        );
        expect(json[1].processDefinitionName).toBe(
          'Process with Task listener',
        );
      }).toPass(defaultAssertionOptions);

      await cancelProcessInstance(localState.processInstanceKey as string);
    });
  });

  test('Get Process Instance Call Hierarchy - Unauthorized', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl(`/process-instances/2251799813685249/call-hierarchy`),
      {
        // No auth headers
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Process Instance Call Hierarchy - No Items Found', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl(`/process-instances/2251799813685249/call-hierarchy`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Process Instance with key '2251799813685249' not found`,
    );
  });

  test('Get Process Instance Call Hierarchy - Bad Request', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl(`/process-instances/invalid-key/call-hierarchy`),
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
