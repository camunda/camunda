/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: 2025-09-15T03:11:51.640Z
 * Spec Commit: 0fe50d88d8253bb5367efab5a2c911758c95e7ea
 */
import {test, expect} from '@playwright/test';
import {jsonHeaders, buildUrl} from '../../../../utils/http';

test.describe('Resources Validation API Tests', () => {
  test('deleteResource - Additional prop __extraField', async ({request}) => {
    const requestBody = {
      operationReference: 1,
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteResource - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteResource - Param operationReference wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteResource - Param operationReference wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: true,
    };
    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteResource - Constraint violation operationReference (#1)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0.99999,
    };
    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteResource - Constraint violation operationReference (#2)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: 0,
    };
    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteResource - Constraint violation operationReference (#3)', async ({
    request,
  }) => {
    const requestBody = {
      operationReference: -99,
    };
    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey: 'x'}),
      {
        headers: jsonHeaders(),
        data: requestBody,
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('deleteResource - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey: 'x'}),
      {
        headers: jsonHeaders(),
      },
    );
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 200) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(200);
  });
});
