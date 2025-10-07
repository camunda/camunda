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
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Cancel Process instance Tests', () => {
  test('Cancel Process Instance - Success', async ({request}) => {
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

    await test.step('Cancel Process Instance', async () => {
      const processInstanceKey = localState['processInstanceKey'];
      const res = await request.post(
        buildUrl(`/process-instances/${processInstanceKey}/cancellation`),
        {
          headers: jsonHeaders(),
        },
      );

      await assertStatusCode(res, 204);
    });
  });

  test('Cancel Process Instance - Not Found', async ({request}) => {
    const fakeProcessInstanceKey = '2251799813704885';
    const res = await request.post(
      buildUrl(`/process-instances/${fakeProcessInstanceKey}/cancellation`),
      {
        headers: jsonHeaders(),
      },
    );

    await assertNotFoundRequest(
      res,
      "Command 'CANCEL' rejected with code 'NOT_FOUND': Expected to cancel a process instance with key '2251799813704885', but no such process was found",
    );
  });

  test('Cancel Process Instance - Bad Request - Invalid Key', async ({
    request,
  }) => {
    const invalidProcessInstanceKey = 'invalidKey';
    const res = await request.post(
      buildUrl(`/process-instances/${invalidProcessInstanceKey}/cancellation`),
      {
        headers: jsonHeaders(),
      },
    );

    await assertBadRequest(
      res,
      "Failed to convert 'processInstanceKey' with value: 'invalidKey'",
    );
  });

  test('Cancel Process Instance - Unauthorized', async ({request}) => {
    const res = await request.post(
      buildUrl(`/process-instances/2251799813704885/cancellation`),
      {
        // No auth headers
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );

    await assertUnauthorizedRequest(res);
  });

  test('Double Cancel Process Instance - Not Found', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await test.step('First create a process instance', async () => {
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

    await test.step('Then cancel it', async () => {
      const processInstanceKey = localState['processInstanceKey'];
      const res = await request.post(
        buildUrl(`/process-instances/${processInstanceKey}/cancellation`),
        {
          headers: jsonHeaders(),
        },
      );

      await assertStatusCode(res, 204);
    });

    await test.step('Then try to cancel it again', async () => {
      const processInstanceKey = localState['processInstanceKey'];
      const res = await request.post(
        buildUrl(`/process-instances/${processInstanceKey}/cancellation`),
        {
          headers: jsonHeaders(),
        },
      );

      await assertNotFoundRequest(
        res,
        `Command 'CANCEL' rejected with code 'NOT_FOUND': Expected to cancel a process instance with key '${localState['processInstanceKey']}', but no such process was found`,
      );
    });
  });
});
