/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2025-09-22T18:40:25.704Z
 * Spec Commit: f2fd6a1393ca4c7feae1efd10c7c863c0f146187
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../utils/http';

test.describe('System Validation API Tests', () => {
  test('getUsageMetrics - Query param tenantId pattern violation', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', undefined),
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
      buildUrl('/system/usage-metrics', {
        startTime: 'x',
        tenantId: 'x',
        withTenants: 'true',
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
      buildUrl('/system/usage-metrics', {
        endTime: 'x',
        tenantId: 'x',
        withTenants: 'true',
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
      buildUrl('/system/usage-metrics', {
        startTime: 'x',
        endTime: '__INVALID_STRING__',
        tenantId: 'x',
        withTenants: 'true',
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
      buildUrl('/system/usage-metrics', {
        startTime: '__INVALID_STRING__',
        endTime: 'x',
        tenantId: 'x',
        withTenants: 'true',
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
  test('getUsageMetrics - Param query.tenantId wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/system/usage-metrics', {
        startTime: 'x',
        endTime: 'x',
        tenantId: '__INVALID_STRING__',
        withTenants: 'true',
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
      buildUrl('/system/usage-metrics', {
        startTime: 'x',
        endTime: 'x',
        tenantId: 'x',
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
