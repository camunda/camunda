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
import {authHeaders, jsonHeaders, buildUrl} from '../../../utils/http';

test.describe('Documents Validation API Tests', () => {
  // Known failing (see known-failing-tests.json): multipart request bodies aren't run through the same additional-property validation as JSON bodies
  test.skip('createDocument - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      file: 'x',
      __unexpectedField: 'x',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents', undefined), {
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
  test.skip('createDocument - Body wrong top-level type', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents', undefined), {
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
  test.skip('createDocument - Param file wrong type', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      file: '123',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents', undefined), {
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
  test.skip('createDocument - Missing body', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents', undefined), {
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
  test.skip('createDocument - Missing file', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDocumentLink - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
      timeToLive: 1,
      __extraField: 'unexpected',
    };
    const res = await request.post(
      buildUrl('/documents/{documentId}/links', {documentId: 'x'}),
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
  test('createDocumentLink - Body wrong top-level type', async ({request}) => {
    const requestBody: string[] = [];
    const res = await request.post(
      buildUrl('/documents/{documentId}/links', {documentId: 'x'}),
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
  test('createDocumentLink - Param timeToLive wrong type (#1)', async ({
    request,
  }) => {
    const requestBody = {
      timeToLive: 'not-a-number',
    };
    const res = await request.post(
      buildUrl('/documents/{documentId}/links', {documentId: 'x'}),
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
  test('createDocumentLink - Param timeToLive wrong type (#2)', async ({
    request,
  }) => {
    const requestBody = {
      timeToLive: true,
    };
    const res = await request.post(
      buildUrl('/documents/{documentId}/links', {documentId: 'x'}),
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
  // Known failing (see known-failing-tests.json): multipart request bodies aren't run through the same additional-property validation as JSON bodies
  test.skip('createDocuments - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      files: '["x"]',
      __unexpectedField: 'x',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
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
  test.skip('createDocuments - Body wrong top-level type', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
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
  test.skip('createDocuments - Param files.0 wrong type', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      files: '[123]',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
      headers: authHeaders(),
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  // Known failing (see known-failing-tests.json): multipart files constraint not enforced
  test.skip('createDocuments - Constraint violation files', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      files: '[]',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
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
  test.skip('createDocuments - Missing body', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
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
  test.skip('createDocuments - Missing files', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
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
