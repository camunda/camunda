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

test.describe('Clock Validation API Tests', () => {
  test('pinClock - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      timestamp: 1,
      __extraField: 'unexpected',
    };
    const res = await request.put(buildUrl('/clock', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('pinClock - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.put(buildUrl('/clock', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('pinClock - Param timestamp wrong type (#1)', async ({request}) => {
    const requestBody = {
      timestamp: 'not-a-number',
    };
    const res = await request.put(buildUrl('/clock', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('pinClock - Param timestamp wrong type (#2)', async ({request}) => {
    const requestBody = {
      timestamp: true,
    };
    const res = await request.put(buildUrl('/clock', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('pinClock - Missing timestamp', async ({request}) => {
    const requestBody = {};
    const res = await request.put(buildUrl('/clock', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('pinClock - Missing body', async ({request}) => {
    const res = await request.put(buildUrl('/clock', undefined), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
