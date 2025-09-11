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

test.describe('Documents Validation API Tests', () => {
  test('createDocument - Param query.documentId wrong type', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/documents', {storeId: 'x', documentId: '12345'}),
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
  test('createDocument - Param query.storeId wrong type', async ({request}) => {
    const res = await request.post(
      buildUrl('/documents', {storeId: '12345', documentId: 'x'}),
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
  test('createDocumentLink - Additional prop __extraField', async ({
    request,
  }) => {
    const requestBody = {
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
  test('createDocumentLink - Missing body', async ({request}) => {
    const res = await request.post(
      buildUrl('/documents/{documentId}/links', {documentId: 'x'}),
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
  test('createDocumentLink - Param query.contentHash wrong type', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/documents/{documentId}/links', {
        storeId: 'x',
        contentHash: '12345',
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
  test('createDocumentLink - Param query.storeId wrong type', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/documents/{documentId}/links', {
        storeId: '12345',
        contentHash: 'x',
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
  test('createDocuments - Param query.storeId wrong type', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/documents/batch', {storeId: '12345'}),
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
  test('deleteDocument - Param query.storeId wrong type', async ({request}) => {
    const res = await request.delete(
      buildUrl('/documents/{documentId}', {storeId: '12345'}),
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
  test('getDocument - Param query.contentHash wrong type', async ({
    request,
  }) => {
    const res = await request.get(
      buildUrl('/documents/{documentId}', {storeId: 'x', contentHash: '12345'}),
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
  test('getDocument - Param query.storeId wrong type', async ({request}) => {
    const res = await request.get(
      buildUrl('/documents/{documentId}', {storeId: '12345', contentHash: 'x'}),
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
