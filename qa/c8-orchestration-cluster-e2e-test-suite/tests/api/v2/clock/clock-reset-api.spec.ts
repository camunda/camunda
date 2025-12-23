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
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {
  createSingleInstance,
  deploy,
} from '../../../../utils/zeebeClient';
import { defaultAssertionOptions } from 'utils/constants';


test.describe('Reset Clock API Tests', () => {
  const timestamp = Date.parse('2025-01-01T00:00:00Z');
  test.beforeAll(async ({ request }) => {
    await deploy(['./resources/clock_api_test_process.bpmn']);

    await test.step('Pin clock to fixed instant and verify', async () => {
        const pin = await request.put(
            buildUrl('/clock'),
            {
                data: { timestamp },
                headers: jsonHeaders(),
            },
        );

        await assertStatusCode(pin, 204);
    });

  });

  test.afterAll(async ({ request }) => {
    const res = await request.post(
        buildUrl(
            '/clock/reset',
        ),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res, 204);
  });

test('Reset clock', async ({ request }) => {
    await test.step('Create process instances and verify pinned start and end dates', async () => {
        const instance = await createSingleInstance('clockApiTestProcess', 1);
        const processInstanceKeyToGet = instance.processInstanceKey;

        await expect(async () => {
            const res = await request.get(
            buildUrl(`/process-instances/${processInstanceKeyToGet}`),
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
            
            const body = await res.json();
            expect(body.processInstanceKey).toBe(processInstanceKeyToGet);
            expect(body.state).toBe('COMPLETED');
            const startDate = body.startDate;
            const endDate = body.endDate;
            expect(new Date(startDate).getTime()).toBe(timestamp);
            expect(new Date(endDate).getTime()).toBe(timestamp);
        }).toPass(defaultAssertionOptions);   
    });

    await test.step('Reset clock', async () => {
      const res = await request.post(
        buildUrl(
            '/clock/reset',
        ),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res, 204);
    });

    await test.step('Create process instances and verify updated start and end dates', async () => {
        const instance = await createSingleInstance('clockApiTestProcess', 1);
        const processInstanceKeyToGet = instance.processInstanceKey;

        await expect(async () => {
            const res = await request.get(
            buildUrl(`/process-instances/${processInstanceKeyToGet}`),
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
            
            const body = await res.json();
            expect(body.processInstanceKey).toBe(processInstanceKeyToGet);
            expect(body.state).toBe('COMPLETED');

            const MAX_DRIFT = 60_000;
            const serverTimeMs = new Date(body.startDate).getTime();
            const nowMs = Date.now();
            const diff = Math.abs(serverTimeMs - nowMs);
            console.log('Diff: ', diff);
            expect(diff).toBeLessThanOrEqual(MAX_DRIFT);
        }).toPass(defaultAssertionOptions);   
    });
  });
});