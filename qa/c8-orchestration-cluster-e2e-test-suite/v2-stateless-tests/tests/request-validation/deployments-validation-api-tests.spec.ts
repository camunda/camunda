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
import {authHeaders, buildUrl} from '../../../utils/http';

test.describe('Deployments Validation API Tests', () => {
  // Known failing (see known-failing-tests.json): multipart request bodies aren't run through the same additional-property validation as JSON bodies
  test.skip('createDeployment - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      resources: '["x"]',
      tenantId: 'x',
      __unexpectedField: 'x',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): multipart request bodies aren't run through the same top-level-type validation as JSON bodies
  test.skip('createDeployment - Body wrong top-level type', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): multipart part type mismatch not rejected
  test.skip('createDeployment - Param resources.0 wrong type', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      resources: '[123]',
      tenantId: 'x',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): multipart part type mismatch not rejected
  test.skip('createDeployment - Param tenantId wrong type', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      resources: '["x"]',
      tenantId: '123',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): multipart tenantId constraint not enforced
  test.skip('createDeployment - Constraint violation tenantId (#1)', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      resources: '["x"]',
      tenantId: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): multipart tenantId constraint not enforced
  test.skip('createDeployment - Constraint violation tenantId (#2)', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      resources: '["x"]',
      tenantId: '',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): multipart tenantId constraint not enforced
  test.skip('createDeployment - Constraint violation tenantId (#3)', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      resources: '["x"]',
      tenantId: '\n',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): multipart tenantId constraint not enforced
  test.skip('createDeployment - Constraint violation tenantId (#4)', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      resources: '["x"]',
      tenantId: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): multipart required-part omission not rejected
  test.skip('createDeployment - Missing resources (#1)', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      tenantId: 'x',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): empty multipart submission not rejected
  test.skip('createDeployment - Missing body', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): multipart required-part omission not rejected
  test.skip('createDeployment - Missing resources (#2)', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
