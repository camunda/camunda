/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {randomUUID} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {test, expect, request as playwrightRequest} from '@playwright/test';
import {encode} from '../../../../../utils/http';
import {extendedAssertionOptions} from '../../../../../utils/constants';
import {
  startIsolatedEnvironmentWithExporter,
  stopIsolatedEnvironment,
} from '../../../../../utils/dockerComposeControl';
import {
  queryLoki,
  queryPrometheus,
  countLokiEntries,
  firstPrometheusValue,
  toLokiNanos,
} from '../../../../../utils/analyticsQueryHelpers';

// P7 — the analytics exporter's pre-aggregated Prometheus counter
// (camunda_process_instance_created_total) must agree with the raw
// process_instance_created event count in Loki for the same instances.
//
// This runs against a standalone, throwaway camunda broker (see
// config/docker-compose.analytics-isolated.yml, camunda-analytics-isolated-exporter)
// rather than the shared stack every other test in this suite depends on —
// a dedicated broker means no other test's process instances can ever land
// in the same count, so there's nothing to contaminate the parity check.

const ISOLATED_BASE_URL =
  process.env.ANALYTICS_ISOLATED_BASE_URL ?? 'http://localhost:28080';
const ISOLATED_LOKI_URL =
  process.env.ANALYTICS_ISOLATED_LOKI_URL ?? 'http://localhost:23100';
const ISOLATED_PROMETHEUS_URL =
  process.env.ANALYTICS_ISOLATED_PROMETHEUS_URL ?? 'http://localhost:29090';
const authHeader = `Basic ${encode('demo:demo')}`;

test.describe.serial('Analytics Exporter Counter Parity', () => {
  test.setTimeout(5 * 60 * 1000);

  test.beforeAll(async () => {
    await startIsolatedEnvironmentWithExporter();

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

  test('Prometheus counter matches raw Loki event count for a known batch of process instances', async ({
    request,
  }) => {
    const processId = `analytics-parity-${randomUUID().slice(0, 8)}`;
    const instanceCount = 20;

    await test.step('Deploy a dedicated, uniquely-named process', async () => {
      const bpmn = readFileSync(
        './resources/analytics_parity_test.bpmn',
        'utf-8',
      ).replace(/analytics_parity_test/g, processId);
      const deployRes = await request.post(
        `${ISOLATED_BASE_URL}/v2/deployments`,
        {
          headers: {Authorization: authHeader},
          multipart: {
            resources: {
              name: `${processId}.bpmn`,
              mimeType: 'application/xml',
              buffer: Buffer.from(bpmn),
            },
          },
        },
      );
      expect(deployRes.status()).toBe(200);
    });

    await test.step(`Create ${instanceCount} process instances`, async () => {
      for (let i = 0; i < instanceCount; i++) {
        const createRes = await request.post(
          `${ISOLATED_BASE_URL}/v2/process-instances`,
          {
            headers: {
              Authorization: authHeader,
              'Content-Type': 'application/json',
            },
            data: {processDefinitionId: processId},
          },
        );
        expect(createRes.status()).toBe(200);
      }
    });

    await test.step('Verify Loki and Prometheus agree on the count', async () => {
      await expect(async () => {
        const loki = await queryLoki(
          request,
          `{service_name="camunda-zeebe"} | event_name="process_instance_created" | camunda_process_id="${processId}"`,
          toLokiNanos(new Date(Date.now() - 10 * 60 * 1000)),
          toLokiNanos(),
          1000,
          ISOLATED_LOKI_URL,
        );
        const lokiCount = countLokiEntries(loki);
        expect(lokiCount).toBe(instanceCount);

        const prometheus = await queryPrometheus(
          request,
          `sum(camunda_process_instance_created_total{camunda_process_id="${processId}"})`,
          ISOLATED_PROMETHEUS_URL,
        );
        const prometheusCount = firstPrometheusValue(prometheus);
        expect(prometheusCount).toBe(instanceCount);
        // Retry budget must comfortably exceed the exporter's push-interval
        // (PT30S) plus Prometheus scrape/remote-write lag — defaultAssertionOptions'
        // 30s window races the 30s push-interval itself and flakes.
      }).toPass(extendedAssertionOptions);
    });
  });
});
