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

test.describe('Signals Validation API Tests', () => {
  test('broadcastSignal - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      signalName: 'x',
      __extraField: 'unexpected',
    };
    const res = await request.post(buildUrl('/signals/broadcast', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('broadcastSignal - Body wrong top-level type', async ({ request }) => {
    const requestBody = [];
    const res = await request.post(buildUrl('/signals/broadcast', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('broadcastSignal - Param signalName wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      signalName: 123,
    };
    const res = await request.post(buildUrl('/signals/broadcast', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('broadcastSignal - Param signalName wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      signalName: true,
    };
    const res = await request.post(buildUrl('/signals/broadcast', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('broadcastSignal - Missing signalName', async ({ request }) => {
    const requestBody = {};
    const res = await request.post(buildUrl('/signals/broadcast', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
  test('broadcastSignal - Missing body', async ({ request }) => {
    const res = await request.post(buildUrl('/signals/broadcast', undefined), {
      headers: jsonHeaders(),
    });
    if (res.status() !== 400) {
      try {
        console.error(await res.text());
      } catch {}
    }
    expect(res.status()).toBe(400);
  });
});
