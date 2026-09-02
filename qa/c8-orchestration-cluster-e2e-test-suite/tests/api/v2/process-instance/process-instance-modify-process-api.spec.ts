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
import {validateResponse} from '../../../../json-body-assertions';
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
      await validateResponse(
        {
          path: '/process-instances',
          method: 'POST',
          status: '200',
        },
        res,
      );
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
        await validateResponse(
          {
            path: '/user-tasks/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const body = await res.json();
        expect(body).toHaveProperty('items');
        expect(body.items).toHaveLength(1);
        expect(body.items[0].elementId).toBe('first_task');
        localStorage['elementInstanceKey'] = body.items[0].elementInstanceKey;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Modify process instance', async () => {
      const res = await request.post(
        buildUrl('/process-instances/{processInstanceKey}/modification', {
          processInstanceKey: localStorage['processInstanceKey'] as string,
        }),
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
        await validateResponse(
          {
            path: '/user-tasks/search',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const body = await res.json();
        expect(body).toHaveProperty('items');
        expect(body.items).toHaveLength(2);
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
      await validateResponse(
        {
          path: '/process-instances',
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body).toHaveProperty('processInstanceKey');
      localStorage['processInstanceKey'] = body.processInstanceKey;
    });

    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: localStorage['processInstanceKey'] as string,
      }),
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
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: 'invalidKey',
      }),
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
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: '2251799813704885',
      }),
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
    // Use a key near the top of partition 1's range. Its counter is far beyond
    // anything a run allocates, so it can never collide with a real instance —
    // a low key like 2251799813704885 collided with a parallel test's instance,
    // so MODIFY resolved it and rejected the missing 'second_task' with
    // INVALID_ARGUMENT (400) instead of the NOT_FOUND (404) this test asserts.
    // It must stay on an existing partition (1 is always present, even in the
    // single-partition RDBMS setup) so the command routes and the engine can
    // answer NOT_FOUND rather than a 503 "request could not be delivered" — a
    // key that decodes to an unused partition (e.g. 9999999999999999) is
    // unroutable. Partition 1 is [2^51, 2^52); this value sits just below the
    // upper bound.
    const nonExistentProcessInstanceKey = '4503599627000000';
    const res = await request.post(
      buildUrl('/process-instances/{processInstanceKey}/modification', {
        processInstanceKey: nonExistentProcessInstanceKey,
      }),
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
      `Command 'MODIFY' rejected with code 'NOT_FOUND': Expected to modify process instance but no process instance found with key '${nonExistentProcessInstanceKey}'`,
    );
  });
});
