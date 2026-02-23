/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertStatusCode,
  buildUrl,
  jsonHeaders,
  assertBadRequest,
} from '../../../../utils/http';
import {deploy} from '../../../../utils/zeebeClient';
import {createProcessInstanceAndRetrieveTimeStamp} from '@requestHelpers';

test.describe('Pin Clock API Tests', () => {
  let processDefinitionId: string;
  test.beforeAll(async ({request}) => {
    await test.step('Deploy process definition', async () => {
      const deployment = await deploy([
        './resources/clock_api_test_process.bpmn',
      ]);
      processDefinitionId =
        deployment.processes?.[0]?.processDefinitionId.toString() || '';
    });

    await test.step('Reset clock to ensure clean state', async () => {
      const res = await request.post(buildUrl('/clock/reset'), {
        headers: jsonHeaders(),
      });

      await assertStatusCode(res, 204);
    });
  });

  test.afterAll(async ({request}) => {
    const res = await request.post(buildUrl('/clock/reset'), {
      headers: jsonHeaders(),
    });
    await assertStatusCode(res, 204);
  });

  test('Pin clock to a fixed instant', async ({request}) => {
    const timestamp = Date.parse('2025-01-01T00:00:00Z');

    await test.step('Pin clock to fixed instant and verify', async () => {
      const pin = await request.put(buildUrl('/clock'), {
        data: {timestamp},
        headers: jsonHeaders(),
      });

      await assertStatusCode(pin, 204);
    });

    await test.step('Create process instances and verify pinned start and end dates', async () => {
      const res = await createProcessInstanceAndRetrieveTimeStamp(
        request,
        processDefinitionId,
      );
      expect(new Date(res.startDate).getTime()).toBe(timestamp);
      expect(new Date(res.endDate).getTime()).toBe(timestamp);
    });

    await test.step('Reset clock', async () => {
      const res = await request.post(buildUrl('/clock/reset'), {
        headers: jsonHeaders(),
      });
      await assertStatusCode(res, 204);
    });
  });

  test('Pin clock - bad request', async ({request}) => {
    const someInvalidData = 'meow';
    const pin = await request.put(buildUrl('/clock'), {
      data: {timestamp: someInvalidData},
      headers: jsonHeaders(),
    });

    await assertBadRequest(
      pin,
      'Request property [timestamp] cannot be parsed',
    );
  });
});
