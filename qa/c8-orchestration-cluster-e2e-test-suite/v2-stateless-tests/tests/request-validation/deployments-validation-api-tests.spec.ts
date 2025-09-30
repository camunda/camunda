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

test.describe('Deployments Validation API Tests', () => {
  test('createDeployment - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      resources: '["x"]',
      __unexpectedField: 'x',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDeployment - Body wrong top-level type', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDeployment - Param resources.0 wrong type', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      resources: '[123]',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDeployment - Missing body', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDeployment - Missing resources', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/deployments', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
});
