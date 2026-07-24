/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {readFileSync} from 'node:fs';
import {test, expect, request as playwrightRequest} from '@playwright/test';
import {encode} from '../../../../../utils/http';
import {
  startIsolatedEnvironmentWaitStatesOff,
  stopIsolatedEnvironment,
} from '../../../../../utils/dockerComposeControl';

const ISOLATED_BASE_URL =
  process.env.WAITSTATES_ISOLATED_BASE_URL ?? 'http://localhost:29080';
const authHeader = `Basic ${encode('demo:demo')}`;

test.describe.serial('Wait States Flag Off', () => {
  test.setTimeout(5 * 60 * 1000);

  test.beforeAll(async () => {
    await startIsolatedEnvironmentWaitStatesOff();

    const context = await playwrightRequest.newContext();
    try {
      await expect(async () => {
        const res = await context.get(`${ISOLATED_BASE_URL}/v2/topology`, {
          headers: {Authorization: authHeader},
        });
        expect(res.status()).toBe(200);
      }).toPass({intervals: [2_000, 5_000, 10_000], timeout: 120_000});
    } finally {
      await context.dispose();
    }
  });

  test.afterAll(async () => {
    await stopIsolatedEnvironment();
  });

  test('wait-states/search returns no rows for an instance that would otherwise be waiting on a job', async ({
    request,
  }) => {
    let processInstanceKey = '';

    await test.step('Deploy a process and start an instance', async () => {
      const bpmn = readFileSync(
        './resources/simpleServiceTaskProcess.bpmn',
        'utf-8',
      );
      const deployRes = await request.post(
        `${ISOLATED_BASE_URL}/v2/deployments`,
        {
          headers: {Authorization: authHeader},
          multipart: {
            resources: {
              name: 'simpleServiceTaskProcess.bpmn',
              mimeType: 'application/xml',
              buffer: Buffer.from(bpmn),
            },
          },
        },
      );
      expect(deployRes.status()).toBe(200);

      const createRes = await request.post(
        `${ISOLATED_BASE_URL}/v2/process-instances`,
        {
          headers: {
            Authorization: authHeader,
            'Content-Type': 'application/json',
          },
          data: {processDefinitionId: 'simpleServiceTaskProcess'},
        },
      );
      expect(createRes.status()).toBe(200);
      processInstanceKey = (await createRes.json()).processInstanceKey;
    });

    await test.step('Confirm the wait state was never indexed', async () => {
      // Fixed wait, not a retry loop — asserting an absence, not eventual presence.
      await new Promise((resolve) => setTimeout(resolve, 15_000));

      const res = await request.post(
        `${ISOLATED_BASE_URL}/v2/element-instances/wait-states/search`,
        {
          headers: {
            Authorization: authHeader,
            'Content-Type': 'application/json',
          },
          data: {filter: {processInstanceKey}},
        },
      );
      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.page.totalItems).toBe(0);
      expect(body.items).toHaveLength(0);
    });
  });
});
