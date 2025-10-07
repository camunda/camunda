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

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Process Instance Modify Process API', () => {
  test.beforeAll(async () => {
    await deploy([
      './resources/process_with_two_user_tasks.bpmn',
      './resources/process_with_task_listener.bpmn',
    ]);
  });

  test('Modify process instance - success', async ({request}) => {
    const localStorage: Record<string, unknown> = {};
    await test.step('Create process instance', async () => {
      const res = await request.post(buildUrl('/process-instances'), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: 'process_with_two_user_tasks',
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body).toHaveProperty('processInstanceKey');
      localStorage['processInstanceKey'] = body.processInstanceKey;
    });

    await test.step('Verify first task is active', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/user-tasks/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: localStorage['processInstanceKey'],
            },
          },
        });
        await assertStatusCode(res, 200);
        const body = await res.json();
        expect(body).toHaveProperty('items');
        expect(body.items.length).toBe(1);
        expect(body.items[0].elementId).toBe('first_task');
        localStorage['elementInstanceKey'] = body.items[0].elementInstanceKey;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Modify process instance', async () => {
      const res = await request.post(
        buildUrl(
          `/process-instances/${localStorage['processInstanceKey']}/modification`,
        ),
        {
          headers: jsonHeaders(),
          data: {
            activateInstructions: [
              {
                elementId: 'second_task',
              },
            ],
            terminateInstructions: [
              {
                elementInstanceKey: localStorage['elementInstanceKey'],
              },
            ],
          },
        },
      );
      await assertStatusCode(res, 204);
    });
    await test.step('Verify second task is active and first task canceled', async () => {
      await expect(async () => {
        const res = await request.post(buildUrl('/user-tasks/search'), {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey: localStorage['processInstanceKey'],
            },
          },
        });
        await assertStatusCode(res, 200);
        const body = await res.json();
        expect(body).toHaveProperty('items');
        expect(body.items.length).toBe(2);
        expect(body.items[0].elementId).toBe('first_task');
        expect(body.items[0].state).toBe('CANCELED');
        expect(body.items[1].elementId).toBe('second_task');
        expect(body.items[1].state).toBe('CREATED');
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localStorage['processInstanceKey'] as string);
  });

  test('Modify process instance - bad request - invalid payload', async ({
    request,
  }) => {
    const localStorage: Record<string, unknown> = {};

    await test.step('Create process instance to modify with invalid payload', async () => {
      const res = await request.post(buildUrl('/process-instances'), {
        headers: jsonHeaders(),
        data: {
          processDefinitionId: 'process_with_task_listener',
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body).toHaveProperty('processInstanceKey');
      localStorage['processInstanceKey'] = body.processInstanceKey;
    });

    const res = await request.post(
      buildUrl(
        `/process-instances/${localStorage['processInstanceKey']}/modification`,
      ),
      {
        headers: jsonHeaders(),
        data: {
          activateInstructions: {
            foo: 'bar',
          },
        },
      },
    );
    await assertBadRequest(
      res,
      'Request property [activateInstructions] cannot be parsed',
    );

    await cancelProcessInstance(localStorage['processInstanceKey'] as string);
  });

  test('Modify process instance - bad request - path parameter', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl(`/process-instances/invalidKey/modification`),
      {
        headers: jsonHeaders(),
        data: {
          activateInstructions: [
            {
              elementId: 'second_task',
            },
          ],
        },
      },
    );

    await assertBadRequest(
      res,
      "Failed to convert 'processInstanceKey' with value: 'invalidKey'",
    );
  });

  test('Modify process instance - Unauthorized', async ({request}) => {
    const res = await request.post(
      buildUrl(`/process-instances/2251799813704885/modification`),
      {
        // No auth headers
        headers: {
          'Content-Type': 'application/json',
        },
        data: {
          activateInstructions: [
            {
              elementId: 'second_task',
            },
          ],
        },
      },
    );

    await assertUnauthorizedRequest(res);
  });

  test('Modify process instance - Not Found', async ({request}) => {
    const res = await request.post(
      buildUrl(`/process-instances/2251799813704885/modification`),
      {
        headers: jsonHeaders(),
        data: {
          activateInstructions: [
            {
              elementId: 'second_task',
            },
          ],
        },
      },
    );

    await assertNotFoundRequest(
      res,
      "Command 'MODIFY' rejected with code 'NOT_FOUND': Expected to modify process instance but no process instance found with key '2251799813704885'",
    );
  });
});
