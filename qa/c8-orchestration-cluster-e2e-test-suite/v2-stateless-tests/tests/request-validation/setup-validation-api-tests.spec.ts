/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2026-07-28T14:59:54.260Z
 * Spec Commit: a85af569edb1e8502a52942193a277eed43e9508
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../utils/http';

test.describe('Setup Validation API Tests', () => {
  test('createAdminUser - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const requestBody = {
      username: null,
      password: 'x',
      __unexpectedField: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Param password wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      username: null,
      password: 123,
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Param password wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      username: null,
      password: true,
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): createAdminUser body validation reorder (28d20717880f) is incomplete
  test.skip('createAdminUser - Missing password (#1)', async ({request}) => {
    const requestBody = {
      username: null,
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): createAdminUser body validation reorder (28d20717880f) is incomplete
  test.skip('createAdminUser - Missing password (#2)', async ({request}) => {
    const requestBody = {
      username: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): createAdminUser body validation reorder (28d20717880f) is incomplete
  test.skip('createAdminUser - Missing username', async ({request}) => {
    const requestBody = {
      password: 'x',
    };
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createAdminUser - Missing body', async ({request}) => {
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): createAdminUser body validation reorder (28d20717880f) is incomplete
  test.skip('createAdminUser - Missing combo username,password', async ({
    request,
  }) => {
    const requestBody = {};
    const res = await request.post(buildUrl('/setup/user', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
