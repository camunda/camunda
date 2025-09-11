/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2025-09-08T04:28:13.914Z
 * Spec Commit: 177fb9193d6c4d0ab558734d76c501bbac1f2454
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../../utils/http';

test.describe('System Validation API Tests', () => {
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
        endTime: '12345',
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
        startTime: '12345',
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
