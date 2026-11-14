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
  startIsolatedEnvironmentWithoutExporter,
  stopIsolatedEnvironment,
  getIsolatedServiceLogs,
} from '../../../../../utils/dockerComposeControl';
import {
  queryLoki,
  toLokiNanos,
} from '../../../../../utils/analyticsQueryHelpers';

// With no analytics exporter config, no analytics records should reach Loki.
// Runs on its own broker (config/docker-compose.analytics-isolated.yml) because
// proving the default needs a broker that never configured the exporter.

const ISOLATED_BASE_URL =
  process.env.ANALYTICS_ISOLATED_BASE_URL ?? 'http://localhost:28080';
const ISOLATED_LOKI_URL =
  process.env.ANALYTICS_ISOLATED_LOKI_URL ?? 'http://localhost:23100';
const authHeader = `Basic ${encode('demo:demo')}`;

test.describe.serial('Analytics Exporter Default-Off', () => {
  test.setTimeout(5 * 60 * 1000);

  test.beforeAll(async () => {
    await startIsolatedEnvironmentWithoutExporter();

    const context = await playwrightRequest.newContext();
    try {
      await expect(async () => {
        const res = await context.get(`${ISOLATED_BASE_URL}/v2/topology`, {
          headers: {Authorization: authHeader},
        });
        expect(res.status()).toBe(200);
      }).toPass({intervals: [2_000, 5_000, 10_000], timeout: 120_000});
    } catch (error) {
      console.error(
        'camunda-analytics-isolated logs:\n',
        await getIsolatedServiceLogs('camunda-analytics-isolated'),
      );
      throw error;
    } finally {
      await context.dispose();
    }
  });

  test.afterAll(async () => {
    await stopIsolatedEnvironment();
  });

  test('no analytics records reach Loki when the exporter is not configured', async ({
    request,
  }) => {
    const startNs = toLokiNanos();

    await test.step('Deploy a process and create instances', async () => {
      const bpmn = readFileSync(
        './resources/process_instance_api_test.bpmn',
        'utf-8',
      );
      const deployRes = await request.post(
        `${ISOLATED_BASE_URL}/v2/deployments`,
        {
          headers: {Authorization: authHeader},
          multipart: {
            resources: {
              name: 'process_instance_api_test.bpmn',
              mimeType: 'application/xml',
              buffer: Buffer.from(bpmn),
            },
          },
        },
      );
      expect(deployRes.status()).toBe(200);

      for (let i = 0; i < 5; i++) {
        const createRes = await request.post(
          `${ISOLATED_BASE_URL}/v2/process-instances`,
          {
            headers: {
              Authorization: authHeader,
              'Content-Type': 'application/json',
            },
            data: {processDefinitionId: 'process_instance_api_test'},
          },
        );
        expect(createRes.status()).toBe(200);
      }
    });

    await test.step('Confirm zero analytics records reached Loki', async () => {
      // Give the exporter every opportunity to have pushed something if it
      // were somehow active — a fixed wait past its PT30S push-interval, not
      // a retry-until-found loop, since we're asserting an absence, not
      // waiting for eventual presence.
      await new Promise((resolve) => setTimeout(resolve, 40_000));

      const loki = await queryLoki(
        request,
        '{service_name="camunda-zeebe"}',
        startNs,
        toLokiNanos(),
        1000,
        ISOLATED_LOKI_URL,
      );
      expect(loki).toHaveLength(0);
    });
  });
});
