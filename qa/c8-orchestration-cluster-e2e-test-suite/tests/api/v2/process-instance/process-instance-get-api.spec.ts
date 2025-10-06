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
test.describe.parallel('Get Process instance Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/process_with_task_listener.bpmn']);
  });

  test('Get Process Instance - Success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await test.step('First, create a process instance', async () => {
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

    await test.step('Get Process Instance', async () => {
      await expect(async () => {
        const processInstanceKey = localState['processInstanceKey'];
        const res = await request.get(
          buildUrl(`/process-instances/${processInstanceKey}`),
          {
            headers: jsonHeaders(),
          },
        );

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/process-instances/{processInstanceKey}',
            method: 'GET',
            status: '200',
          },
          res,
        );
        localState['responseJson'] = await res.json();
      }).toPass(defaultAssertionOptions);
      const json = localState['responseJson'] as {[key: string]: unknown};
      expect(json.processInstanceKey).toBe(localState.processInstanceKey);
      expect(json.processDefinitionId).toBe('process_with_task_listener');
      expect(json.tenantId).toBe('<default>');
      expect(json.state).toBe('ACTIVE');
      expect(json.hasIncident).toBeFalsy();
      expect(json.processDefinitionName).toBe('Process with Task listener');
      expect(json.processDefinitionVersion).toBeGreaterThan(0);
      expect(json.processDefinitionKey).toBeDefined();

      await cancelProcessInstance(localState.processInstanceKey as string);
    });
  });

  test('Get Process Instance - Not Found', async ({request}) => {
    const unknownProcessInstanceKey = '2251799813694876';
    const res = await request.get(
      buildUrl(`/process-instances/${unknownProcessInstanceKey}`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertNotFoundRequest(
      res,
      `Process Instance with key '${unknownProcessInstanceKey}' not found`,
    );
  });

  test('Get Process Instance - Invalid Key', async ({request}) => {
    const invalidProcessInstanceKey = 'invalidKey123';
    const res = await request.get(
      buildUrl(`/process-instances/${invalidProcessInstanceKey}`),
      {
        headers: jsonHeaders(),
      },
    );
    await assertBadRequest(res, invalidProcessInstanceKey);
  });

  test('Get Process Instance - Unauthorized', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await test.step('Create Process Instance', async () => {
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

    await test.step('Get Process Instance without auth', async () => {
      const authRes = await request.get(
        buildUrl(`/process-instances/${localState['processInstanceKey']}`),
        {
          // No auth headers
          headers: {
            'Content-Type': 'application/json',
          },
        },
      );
      await assertUnauthorizedRequest(authRes);
    });

    await cancelProcessInstance(localState['processInstanceKey'] as string);
  });
});
