/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2026-08-04T11:55:54.253Z
 * Spec Commit: 7ad6907f6d9cf772438213329bf52fa21d343ed2
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../utils/http';

test.describe('System Validation API Tests', () => {
  // Known failing (see known-failing-tests.json): #59378 - getUsageMetrics binds tenantId as a plain String and passes it straight to UsageMetricsFilter, so the TenantId pattern (^(<default>|[\w\.\-]{1,31})$) is never checked and the request succeeds with a 200. Previously masked by #58948: the scenario sent no query string at all, so its 400 came from the missing required startTime/endTime
  test.skip('getUsageMetrics - Query param tenantId pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', undefined, {
        startTime: '2020-01-01T00:00:00.000Z',
        endTime: '2030-01-01T00:00:00.000Z',
        tenantId: '\n',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getUsageMetrics - Missing param query.endTime', async ({request}) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', undefined, {
        startTime: '2020-01-01T00:00:00.000Z',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getUsageMetrics - Missing param query.startTime', async ({request}) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', undefined, {
        endTime: '2030-01-01T00:00:00.000Z',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getUsageMetrics - Param query.endTime wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', undefined, {
        startTime: '2020-01-01T00:00:00.000Z',
        endTime: '__INVALID_STRING__',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getUsageMetrics - Param query.startTime wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', undefined, {
        startTime: '__INVALID_STRING__',
        endTime: '2030-01-01T00:00:00.000Z',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getUsageMetrics - Param query.withTenants wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', undefined, {
        startTime: '2020-01-01T00:00:00.000Z',
        endTime: '2030-01-01T00:00:00.000Z',
        withTenants: 'notBoolean',
      }),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
