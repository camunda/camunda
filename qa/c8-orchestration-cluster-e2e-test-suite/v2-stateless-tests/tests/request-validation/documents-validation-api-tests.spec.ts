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

test.describe('Documents Validation API Tests', () => {
  test('createDocument - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      file: 'x',
      __unexpectedField: 'x',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDocument - Body wrong top-level type', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDocument - Param file wrong type', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      file: '123',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDocument - Missing body', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDocument - Missing file', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents', undefined), {
      headers: {},
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
  test('createDocumentLink - Missing param query.contentHash', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/documents/{documentId}/links', {storeId: 'x'}),
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
  test('createDocuments - Additional prop __unexpectedField', async ({
    request,
  }) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      files: '["x"]',
      __unexpectedField: 'x',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDocuments - Body wrong top-level type', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDocuments - Param files.0 wrong type', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      files: '[123]',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDocuments - Constraint violation files', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {
      files: '[]',
    };
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDocuments - Missing body', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('createDocuments - Missing files', async ({request}) => {
    const formData = new FormData();
    const multipartFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(multipartFields)) formData.append(k, v);
    const res = await request.post(buildUrl('/documents/batch', undefined), {
      headers: {},
      multipart: formData,
    });
    // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes.
    //   if (res.status() !== 400) {
    //     try { console.error(await res.text()); } catch {}
    //   }
    expect(res.status()).toBe(400);
  });
  test('getDocument - Missing param query.contentHash', async ({request}) => {
    const res = await request.get(
      buildUrl('/documents/{documentId}', {storeId: 'x'}),
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
