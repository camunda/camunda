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

test.describe('Secrets Validation API Tests', () => {
  test('resolveSecrets - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      references: ['x'],
      __extraField: 'unexpected',
    };
    const res = await request.post(buildUrl('/secrets/resolve', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('resolveSecrets - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(buildUrl('/secrets/resolve', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('resolveSecrets - Param references.0 wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      references: [123],
    };
    const res = await request.post(buildUrl('/secrets/resolve', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('resolveSecrets - Param references.0 wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      references: [true],
    };
    const res = await request.post(buildUrl('/secrets/resolve', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('resolveSecrets - Constraint violation references (#1)', async ({
    request,
  }) => {
    const requestBody = {
      references: [
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
      ],
    };
    const res = await request.post(buildUrl('/secrets/resolve', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('resolveSecrets - Constraint violation references (#2)', async ({
    request,
  }) => {
    const requestBody = {
      references: [
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1,
      ],
    };
    const res = await request.post(buildUrl('/secrets/resolve', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): over-long individual reference is deliberately reported as a per-reference 200 error (SecretErrorCode.INVALID_REFERENCE, see SecretServices.MAX_REFERENCE_LENGTH), not a 400 - the generator assumes every constraint violation is a 400, which doesn't hold for this alpha (Phase 1, #56567) endpoint's per-item-error design
  test.skip('resolveSecrets - Constraint violation references.0 (#1)', async ({
    request,
  }) => {
    const requestBody = {
      references: [
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      ],
    };
    const res = await request.post(buildUrl('/secrets/resolve', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): over-long individual reference is deliberately reported as a per-reference 200 error (SecretErrorCode.INVALID_REFERENCE, see SecretServices.MAX_REFERENCE_LENGTH), not a 400 - the generator assumes every constraint violation is a 400, which doesn't hold for this alpha (Phase 1, #56567) endpoint's per-item-error design
  test.skip('resolveSecrets - Constraint violation references.0 (#2)', async ({
    request,
  }) => {
    const requestBody = {
      references: [
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      ],
    };
    const res = await request.post(buildUrl('/secrets/resolve', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('resolveSecrets - Missing references', async ({request}) => {
    const requestBody = {};
    const res = await request.post(buildUrl('/secrets/resolve', undefined), {
      headers: jsonHeaders(),
      data: requestBody,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('resolveSecrets - Missing body', async ({request}) => {
    const res = await request.post(buildUrl('/secrets/resolve', undefined), {
      headers: jsonHeaders(),
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
