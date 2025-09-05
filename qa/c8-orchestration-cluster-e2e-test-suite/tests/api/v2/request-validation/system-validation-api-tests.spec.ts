/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2025-09-05T06:07:46.867Z
 * Spec Commit: 3445d1d86c2ad361858dc12e734eeb6197e426a5
 */
import { test, expect } from '@playwright/test';
import { jsonHeaders, buildUrl } from '../../../../utils/http';

test.describe('System Validation API Tests', () => {
  test('getUsageMetrics - Missing param query.endTime', async ({ request }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', undefined),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('getUsageMetrics - Missing param query.startTime', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', undefined),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('getUsageMetrics - Param endTime wrong type', async ({ request }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', undefined),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('getUsageMetrics - Param query.endTime wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', { endTime: '12345' }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('getUsageMetrics - Param query.startTime wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', { startTime: '12345' }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('getUsageMetrics - Param query.withTenants wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', { withTenants: 'notBoolean' }),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('getUsageMetrics - Param startTime wrong type', async ({ request }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', undefined),
      {
        headers: jsonHeaders(),
      },
    );
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
});
