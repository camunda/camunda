/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect, type APIRequestContext} from '@playwright/test';
import {
  jsonHeaders,
  buildUrl,
  defaultHeaders,
  assertEqualsForKeys,
  assertUnauthorizedRequest,
  assertUnsupportedMediaTypeRequest,
  assertBadRequest,
  assertNotFoundRequest,
  assertForbiddenRequest,
  assertStatusCode,
} from '../../../utils/http';
import {validateResponse} from '../../../json-body-assertions';
import {
  CREATE_DOC_INVALID_REQUEST,
  CREATE_DOCUMENT_LINK_REQUEST,
  CREATE_ON_FLY_DOCUMENT_REQUEST_BODY_WITH_METADATA,
  CREATE_ON_FLY_MULTIPLE_DOCUMENTS_REQUEST_BODY,
  CREATE_TXT_DOC_RESPONSE_BODY,
  CREATE_TXT_DOC_RESPONSE_WITH_METADATA,
  CREATE_TXT_DOCUMENT_REQUEST,
  CREATE_DOCUMENT_REQUEST_WITH_CONTENT_TYPE,
  CREATE_DOCUMENT_REQUEST_WITHOUT_METADATA,
  documentFileContent,
} from '../../../utils/beans/requestBeans';
import {
  defaultAssertionOptions,
  generateUniqueId,
  generateUniqueProcessDefinitionId,
} from '../../../utils/constants';
import {Serializable} from 'playwright-core/types/structs';

test.describe.parallel('Document API Tests', () => {
  const state: Record<string, unknown> = {};
  const nonexistentId = 'nonExistingDocumentId';
  const responseKeys: string[] = [
    'camunda.document.type',
    'storeId',
    'metadata',
  ];

  test.beforeAll(async ({request}) => {
    async function createDocumentAndStoreIds(nth: number) {
      const fileName = generateUniqueId();
      const processDefinitionId = generateUniqueProcessDefinitionId();
      const payload = CREATE_ON_FLY_DOCUMENT_REQUEST_BODY_WITH_METADATA(
        fileName,
        processDefinitionId,
      );
      const res = await request.post(buildUrl('/documents'), {
        headers: defaultHeaders(),
        multipart: payload,
      });

      await assertStatusCode(res, 201);
      await validateResponse(
        {
          path: '/documents',
          method: 'POST',
          status: '201',
        },
        res,
      );
      const json = await res.json();
      state[`documentId${nth}`] = json.documentId;
      state[`contentHash${nth}`] = json.contentHash;
      state[`storeId${nth}`] = json.storeId;
      state[`fileName${nth}`] = fileName;
    }

    await createDocumentAndStoreIds(1);
    await createDocumentAndStoreIds(2);
  });

  test('Create Document Unauthorized 401', async ({request}) => {
    const res = await request.post(buildUrl('/documents'), {
      headers: {},
      data: CREATE_TXT_DOCUMENT_REQUEST(),
    });

    await assertUnauthorizedRequest(res);
  });

  test('Create Document Invalid Header 415', async ({request}) => {
    const res = await request.post(buildUrl('/documents'), {
      headers: jsonHeaders(),
      multipart: CREATE_TXT_DOCUMENT_REQUEST(),
    });

    await assertUnsupportedMediaTypeRequest(res);
  });

  test('Create Document Invalid Body 400', async ({request}) => {
    const res = await request.post(buildUrl('/documents'), {
      headers: defaultHeaders(),
      multipart: CREATE_DOC_INVALID_REQUEST(),
    });

    await assertBadRequest(res, "Required part 'file' is not present.");
  });

  test('Create Document Invalid Store 400', async ({request}) => {
    const invalidStoreId = 'invalidStore';
    const res = await request.post(
      buildUrl('/documents', {}, {storeId: invalidStoreId}),
      {
        headers: defaultHeaders(),
        multipart: CREATE_TXT_DOCUMENT_REQUEST(),
      },
    );

    await assertBadRequest(
      res,
      `Document store with id '${invalidStoreId}' does not exist`,
      'INVALID_ARGUMENT',
    );
  });

  test('Create Document Invalid processDefinitionId 400', async ({request}) => {
    const fileName = generateUniqueId();
    const invalidProcessDefinitionId = '123xInvalidProcessDefinitionId'; // starts with a number, not a valid BPMN ID; regression guard for #46405 / #46407
    const payload = CREATE_ON_FLY_DOCUMENT_REQUEST_BODY_WITH_METADATA(
      fileName,
      invalidProcessDefinitionId,
    );

    const res = await request.post(buildUrl('/documents'), {
      headers: defaultHeaders(),
      multipart: payload,
    });

    await assertBadRequest(
      res,
      'The provided processDefinitionId contains illegal characters',
      'INVALID_ARGUMENT',
    );
  });

  test('Create Document', async ({request}) => {
    const payload = CREATE_TXT_DOCUMENT_REQUEST();
    const expectedPostBody = CREATE_TXT_DOC_RESPONSE_BODY('helloworld', 12);

    const res = await request.post(buildUrl('/documents'), {
      headers: defaultHeaders(),
      multipart: payload,
    });

    await assertStatusCode(res, 201);
    await validateResponse(
      {
        path: '/documents',
        method: 'POST',
        status: '201',
      },
      res,
    );
    const json = await res.json();
    assertEqualsForKeys(json, expectedPostBody, responseKeys);
  });

  test('Create Document With Query Parameters', async ({request}) => {
    const payload = CREATE_TXT_DOCUMENT_REQUEST();
    const uniqueId = generateUniqueId();
    const storeId = 'in-memory';
    const expectedPostBody = CREATE_TXT_DOC_RESPONSE_BODY('helloworld', 12);

    const res = await request.post(
      buildUrl('/documents', {}, {documentId: uniqueId, storeId: storeId}),
      {
        headers: defaultHeaders(),
        multipart: payload,
      },
    );

    await assertStatusCode(res, 201);
    await validateResponse(
      {
        path: '/documents',
        method: 'POST',
        status: '201',
      },
      res,
    );
    const json = await res.json();
    assertEqualsForKeys(json, expectedPostBody, responseKeys);
    expect(json.documentId).toBe(uniqueId);
    expect(json.storeId).toBe(storeId);
  });

  test('Create Document With Metadata', async ({request}) => {
    const fileName = generateUniqueId();
    const processDefinitionId = generateUniqueProcessDefinitionId();
    const fileContent = documentFileContent(fileName);
    const payload = CREATE_ON_FLY_DOCUMENT_REQUEST_BODY_WITH_METADATA(
      fileName,
      processDefinitionId,
    );
    const expectedPostBody = CREATE_TXT_DOC_RESPONSE_WITH_METADATA(
      fileName,
      processDefinitionId,
      fileContent.length,
    );

    const res = await request.post(buildUrl('/documents'), {
      headers: defaultHeaders(),
      multipart: payload,
    });

    await assertStatusCode(res, 201);
    await validateResponse(
      {
        path: '/documents',
        method: 'POST',
        status: '201',
      },
      res,
    );
    const json = await res.json();
    assertEqualsForKeys(json, expectedPostBody, responseKeys);
  });

  test('Get Document', async ({request}) => {
    await expect(async () => {
      const res = await request.get(
        buildUrl(
          '/documents/{documentId}',
          {
            documentId: state.documentId1 as string,
          },
          {contentHash: state.contentHash1 as string},
        ),
        {headers: defaultHeaders()},
      );

      await assertStatusCode(res, 200);
      const text = await res.text();
      expect(text).toBe(documentFileContent(state['fileName1'] as string));
    }).toPass(defaultAssertionOptions);
  });

  test('Get Document Without Hash 400', async ({request}) => {
    await expect(async () => {
      const res = await request.get(
        buildUrl('/documents/{documentId}', {
          documentId: state.documentId1 as string,
        }),
        {headers: defaultHeaders()},
      );
      await assertBadRequest(
        res,
        'No document hash provided for document',
        'INVALID_ARGUMENT',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get Document Not Found 404', async ({request}) => {
    const res = await request.get(
      buildUrl('/documents/{documentId}', {documentId: nonexistentId}),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Document with id '${nonexistentId}' not found`,
    );
  });

  test('Get Document Unauthorized 401', async ({request}) => {
    const res = await request.get(
      buildUrl('/documents/{documentId}', {
        documentId: state.documentId1 as string,
      }),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Delete Document Unauthorized 401', async ({request}) => {
    const res = await request.delete(
      buildUrl('/documents/{documentId}', {
        documentId: state.documentId2 as string,
      }),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Delete Document Not Found 404', async ({request}) => {
    const res = await request.delete(
      buildUrl('/documents/{documentId}', {
        documentId: nonexistentId,
      }),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Document with id '${nonexistentId}' not found`,
    );
  });

  test('Delete Document', async ({request}) => {
    await test.step('Delete Document 204', async () => {
      await expect(async () => {
        const res = await request.delete(
          buildUrl('/documents/{documentId}', {
            documentId: state.documentId2 as string,
          }),
          {headers: jsonHeaders()},
        );
        await assertStatusCode(res, 204);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Get Deleted Document 404', async () => {
      await expect(async () => {
        const res = await request.get(
          buildUrl('/documents/{documentId}', {
            documentId: state.documentId2 as string,
          }),
          {headers: jsonHeaders()},
        );
        await assertNotFoundRequest(
          res,
          `Document with id '${state.documentId2}' not found`,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Create Multiple Documents', async ({request}) => {
    const fileBaseName = generateUniqueId();
    const payload = CREATE_ON_FLY_MULTIPLE_DOCUMENTS_REQUEST_BODY(
      fileBaseName,
      2,
    );
    const file1Content = documentFileContent(`${fileBaseName}1`);
    const file2Content = documentFileContent(`${fileBaseName}2`);
    const expectedFile1 = CREATE_TXT_DOC_RESPONSE_BODY(
      `${fileBaseName}1`,
      file1Content.length,
    );
    const expectedFile2 = CREATE_TXT_DOC_RESPONSE_BODY(
      `${fileBaseName}2`,
      file2Content.length,
    );
    let json: Serializable = {};

    await test.step('Create Multiple Documents 201', async () => {
      const res = await request.post(buildUrl('/documents/batch'), {
        headers: defaultHeaders(),
        multipart: payload,
      });

      await assertStatusCode(res, 201);
      await validateResponse(
        {
          path: '/documents/batch',
          method: 'POST',
          status: '201',
        },
        res,
      );
      json = await res.json();
      expect(json['createdDocuments']).toHaveLength(2);
      expect(json['failedDocuments']).toHaveLength(0);
    });

    await test.step('Assert First File Fields', async () => {
      const actualFile1 = json.createdDocuments.find(
        (it: {metadata: {fileName: string}}) =>
          it.metadata.fileName === expectedFile1.metadata.fileName,
      );
      expect(actualFile1).toBeDefined();
      assertEqualsForKeys(actualFile1, expectedFile1, responseKeys);
    });

    await test.step('Assert Second File Fields', async () => {
      const actualFile2 = json.createdDocuments.find(
        (it: {metadata: {fileName: string}}) =>
          it.metadata.fileName === expectedFile2.metadata.fileName,
      );
      expect(actualFile2).toBeDefined();
      assertEqualsForKeys(actualFile2, expectedFile2, responseKeys);
    });
  });

  test('Create Multiple Documents Unauthorized 401', async ({request}) => {
    const fileBaseName = generateUniqueId();
    const payload = CREATE_ON_FLY_MULTIPLE_DOCUMENTS_REQUEST_BODY(
      fileBaseName,
      2,
    );

    const res = await request.post(buildUrl('/documents/batch'), {
      headers: {},
      data: payload,
    });

    await assertUnauthorizedRequest(res);
  });

  test('Create Multiple Documents Invalid Header 415', async ({request}) => {
    const fileBaseName = generateUniqueId();
    const payload = CREATE_ON_FLY_MULTIPLE_DOCUMENTS_REQUEST_BODY(
      fileBaseName,
      2,
    );

    const res = await request.post(buildUrl('/documents/batch'), {
      headers: jsonHeaders(),
      multipart: payload,
    });

    await assertUnsupportedMediaTypeRequest(res);
  });

  test('Create Multiple Documents Invalid Body 400', async ({request}) => {
    const res = await request.post(buildUrl('/documents/batch'), {
      headers: defaultHeaders(),
      multipart: CREATE_DOC_INVALID_REQUEST(),
    });

    await assertBadRequest(res, "Required part 'files' is not present.");
  });

  test('Create Document Link 403 For In-Memory Storage', async ({request}) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(
          '/documents/{documentId}/links',
          {
            documentId: state.documentId1 as string,
          },
          {contentHash: state.contentHash1 as string},
        ),
        {
          headers: jsonHeaders(),
          data: CREATE_DOCUMENT_LINK_REQUEST,
        },
      );
      await assertForbiddenRequest(
        res,
        'The in-memory document store does not support creating links',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Create Document Link Without Hash 400', async ({request}) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl('/documents/{documentId}/links', {
          documentId: state.documentId1 as string,
        }),
        {
          headers: jsonHeaders(),
          data: CREATE_DOCUMENT_LINK_REQUEST,
        },
      );
      await assertBadRequest(
        res,
        'No document hash provided for document',
        'INVALID_ARGUMENT',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Create Document Link Not Found 404', async ({request}) => {
    const res = await request.post(
      buildUrl('/documents/{documentId}/links', {documentId: nonexistentId}),
      {
        headers: jsonHeaders(),
        data: CREATE_DOCUMENT_LINK_REQUEST,
      },
    );
    await assertNotFoundRequest(
      res,
      `Document with id '${nonexistentId}' not found`,
    );
  });

  test('Create Document Link Unauthorized 401', async ({request}) => {
    const res = await request.post(
      buildUrl('/documents/{documentId}/links', {
        documentId: state.documentId1 as string,
      }),
      {
        headers: {},
        data: CREATE_DOCUMENT_LINK_REQUEST,
      },
    );
    await assertUnauthorizedRequest(res);
  });

  // camunda/camunda#63904: the content endpoint tells the browser to download
  // active content instead of rendering it in the application origin, and
  // sandboxes whatever it does serve. The headers are the contract; the browser
  // behaviour they produce is covered in tests/operate/documentBrowserRendering.spec.ts.
  const SANDBOX_CSP = "sandbox; default-src 'none'";

  async function uploadWithContentType(
    request: APIRequestContext,
    contentType: string,
  ): Promise<{documentId: string; contentHash: string}> {
    const fileName = generateUniqueId();
    const res = await request.post(buildUrl('/documents'), {
      headers: defaultHeaders(),
      multipart: CREATE_DOCUMENT_REQUEST_WITH_CONTENT_TYPE(
        fileName,
        contentType,
      ),
    });
    await assertStatusCode(res, 201);
    const body = await res.json();
    return {documentId: body.documentId, contentHash: body.contentHash};
  }

  async function getDocumentContent(
    request: APIRequestContext,
    document: {documentId: string; contentHash: string},
    headers: Record<string, string> = defaultHeaders(),
  ) {
    return request.get(
      buildUrl(
        '/documents/{documentId}',
        {documentId: document.documentId},
        {contentHash: document.contentHash},
      ),
      {headers},
    );
  }

  test('Get Document Serves Active Content Types As Attachment', async ({
    request,
  }) => {
    // Types a browser would otherwise interpret in the application origin.
    const activeContentTypes = [
      'text/html',
      'application/javascript',
      'image/svg+xml',
      'application/xml',
      'application/json',
      'text/html; charset=utf-8',
      'image/png+html',
    ];

    for (const contentType of activeContentTypes) {
      await test.step(`${contentType} is served as attachment`, async () => {
        const document = await uploadWithContentType(request, contentType);
        const res = await getDocumentContent(request, document);

        await assertStatusCode(res, 200);
        expect(res.headers()['content-disposition']).toBe('attachment');
        expect(res.headers()['content-security-policy']).toBe(SANDBOX_CSP);
      });
    }
  });

  test('Get Document Serves Safe Content Types Inline', async ({request}) => {
    // The allowlist that keeps the existing Operate image and PDF previews working.
    const inlineSafeContentTypes = [
      'image/png',
      'image/jpeg',
      'image/gif',
      'image/webp',
      'application/pdf',
      'image/png; charset=utf-8',
      'IMAGE/PNG',
    ];

    for (const contentType of inlineSafeContentTypes) {
      await test.step(`${contentType} is served inline`, async () => {
        const document = await uploadWithContentType(request, contentType);
        const res = await getDocumentContent(request, document);

        await assertStatusCode(res, 200);
        expect(res.headers()['content-disposition']).toBe('inline');
        expect(res.headers()['content-security-policy']).toBe(SANDBOX_CSP);
      });
    }
  });

  test('Get Document Falls Back To Octet Stream Without A Usable Content Type', async ({
    request,
  }) => {
    await test.step('an unparseable content type is neutralised', async () => {
      const document = await uploadWithContentType(request, 'not-a-media-type');
      const res = await getDocumentContent(request, document);

      await assertStatusCode(res, 200);
      expect(res.headers()['content-type']).toContain(
        'application/octet-stream',
      );
      expect(res.headers()['content-disposition']).toBe('attachment');
      expect(res.headers()['content-security-policy']).toBe(SANDBOX_CSP);
    });

    await test.step('a document stored without metadata is neutralised', async () => {
      const fileName = generateUniqueId();
      const created = await request.post(buildUrl('/documents'), {
        headers: defaultHeaders(),
        multipart: CREATE_DOCUMENT_REQUEST_WITHOUT_METADATA(fileName),
      });
      await assertStatusCode(created, 201);
      const body = await created.json();
      const res = await getDocumentContent(request, {
        documentId: body.documentId,
        contentHash: body.contentHash,
      });

      await assertStatusCode(res, 200);
      expect(res.headers()['content-type']).toContain(
        'application/octet-stream',
      );
      expect(res.headers()['content-disposition']).toBe('attachment');
      expect(res.headers()['content-security-policy']).toBe(SANDBOX_CSP);
    });
  });

  test('Get Document Ignores Accept When Choosing The Disposition', async ({
    request,
  }) => {
    // A caller must not be able to negotiate an active type back into a render.
    const document = await uploadWithContentType(request, 'text/html');

    for (const accept of ['text/html', '*/*', 'application/octet-stream']) {
      await test.step(`Accept: ${accept} still yields attachment`, async () => {
        const res = await getDocumentContent(request, document, {
          ...defaultHeaders(),
          Accept: accept,
        });

        await assertStatusCode(res, 200);
        expect(res.headers()['content-disposition']).toBe('attachment');
      });
    }
  });

  test('Get Document Error Responses Carry No Document Headers', async ({
    request,
  }) => {
    // The headers belong to served content; a problem detail is not content.
    await test.step('404 for an unknown document', async () => {
      const res = await request.get(
        buildUrl(
          '/documents/{documentId}',
          {documentId: nonexistentId},
          {contentHash: 'deadbeef'},
        ),
        {headers: defaultHeaders()},
      );

      await assertStatusCode(res, 404);
      expect(res.headers()['content-disposition']).toBeUndefined();
      // The app's own policy still applies to a problem detail; what must not
      // appear is the document endpoint's sandbox, in any form.
      expect(res.headers()['content-security-policy'] ?? '').not.toContain(
        'sandbox',
      );
    });

    await test.step('400 when the content hash is missing', async () => {
      const document = await uploadWithContentType(request, 'text/html');
      const res = await request.get(
        buildUrl('/documents/{documentId}', {
          documentId: document.documentId,
        }),
        {headers: defaultHeaders()},
      );

      await assertStatusCode(res, 400);
      expect(res.headers()['content-disposition']).toBeUndefined();
      // The app's own policy still applies to a problem detail; what must not
      // appear is the document endpoint's sandbox, in any form.
      expect(res.headers()['content-security-policy'] ?? '').not.toContain(
        'sandbox',
      );
    });
  });
});
