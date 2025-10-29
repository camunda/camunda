/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  jsonHeaders,
  buildUrl,
  assertRequiredFields,
  defaultHeaders,
  assertEqualsForKeys,
  assertUnauthorizedRequest,
  assertUnsupportedMediaTypeRequest,
  assertBadRequest,
  assertNotFoundRequest,
  assertForbiddenRequest,
} from '../../../utils/http';
import {
  CREATE_DOC_INVALID_REQUEST,
  CREATE_DOCUMENT_LINK_REQUEST,
  CREATE_ON_FLY_DOCUMENT_REQUEST_BODY_WITH_METADATA,
  CREATE_ON_FLY_MULTIPLE_DOCUMENTS_REQUEST_BODY,
  CREATE_TXT_DOC_RESPONSE_BODY,
  CREATE_TXT_DOC_RESPONSE_WITH_METADATA,
  CREATE_TXT_DOCUMENT_REQUEST,
  documentRequiredFields,
  multipleDocumentsRequiredFields,
} from '../../../utils/beans/requestBeans';
import {
  defaultAssertionOptions,
  generateUniqueId,
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
      const name = generateUniqueId();
      const payload = CREATE_ON_FLY_DOCUMENT_REQUEST_BODY_WITH_METADATA(name);
      const res = await request.post(buildUrl('/documents'), {
        headers: defaultHeaders(),
        multipart: payload,
      });

      expect(res.status()).toBe(201);
      const json = await res.json();
      assertRequiredFields(json, documentRequiredFields);
      state[`documentId${nth}`] = json.documentId;
      state[`contentHash${nth}`] = json.contentHash;
      state[`storeId${nth}`] = json.storeId;
      state[`name${nth}`] = name;
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

  test('Create Document', async ({request}) => {
    const payload = CREATE_TXT_DOCUMENT_REQUEST();
    const expectedPostBody = CREATE_TXT_DOC_RESPONSE_BODY('helloworld', 12);

    const res = await request.post(buildUrl('/documents'), {
      headers: defaultHeaders(),
      multipart: payload,
    });

    expect(res.status()).toBe(201);
    const json = await res.json();
    assertRequiredFields(json, documentRequiredFields);
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

    expect(res.status()).toBe(201);
    const json = await res.json();
    assertRequiredFields(json, documentRequiredFields);
    assertEqualsForKeys(json, expectedPostBody, responseKeys);
    expect(json.documentId).toBe(uniqueId);
    expect(json.storeId).toBe(storeId);
  });

  test('Create Document With Metadata', async ({request}) => {
    const name = generateUniqueId();
    const payload = CREATE_ON_FLY_DOCUMENT_REQUEST_BODY_WITH_METADATA(name);
    const expectedPostBody = CREATE_TXT_DOC_RESPONSE_WITH_METADATA(name, 21);

    const res = await request.post(buildUrl('/documents'), {
      headers: defaultHeaders(),
      multipart: payload,
    });

    expect(res.status()).toBe(201);
    const json = await res.json();
    assertRequiredFields(json, documentRequiredFields);
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
      expect(res.status()).toBe(200);
      const text = await res.text();
      expect(text).toBe(`Hello World ${state['name1']}!`);
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
        expect(res.status()).toBe(204);
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
        expect(res.status()).toBe(404);
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Create Multiple Documents', async ({request}) => {
    const name = generateUniqueId();
    const payload = CREATE_ON_FLY_MULTIPLE_DOCUMENTS_REQUEST_BODY(name, 2);
    const expectedFile1 = CREATE_TXT_DOC_RESPONSE_BODY(`${name}1`, 22);
    const expectedFile2 = CREATE_TXT_DOC_RESPONSE_BODY(`${name}2`, 22);
    let json: Serializable = {};

    await test.step('Create Multiple Documents 201', async () => {
      const res = await request.post(buildUrl('/documents/batch'), {
        headers: defaultHeaders(),
        multipart: payload,
      });

      expect(res.status()).toBe(201);
      json = await res.json();
      assertRequiredFields(json, multipleDocumentsRequiredFields);
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
    const name = generateUniqueId();
    const payload = CREATE_ON_FLY_MULTIPLE_DOCUMENTS_REQUEST_BODY(name, 2);

    const res = await request.post(buildUrl('/documents/batch'), {
      headers: {},
      data: payload,
    });

    await assertUnauthorizedRequest(res);
  });

  test('Create Multiple Documents Invalid Header 415', async ({request}) => {
    const name = generateUniqueId();
    const payload = CREATE_ON_FLY_MULTIPLE_DOCUMENTS_REQUEST_BODY(name, 2);

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
});
