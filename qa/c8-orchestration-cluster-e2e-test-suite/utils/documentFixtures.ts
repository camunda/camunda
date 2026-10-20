/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from '@playwright/test';
import {buildUrl, defaultHeaders, assertStatusCode} from './http';
import {CREATE_DOCUMENT_REQUEST_WITH_CONTENT_TYPE} from './beans/requestBeans';
import {generateUniqueId} from './constants';

export type StoredDocument = {
  documentId: string;
  contentHash: string;
  fileName: string;
  /** Absolute, for cases that open the document in a browser. */
  url: string;
};

/** Shared by the API and browser specs so the upload path cannot drift apart. */
export async function uploadDocument(
  request: APIRequestContext,
  contentType: string,
  content?: string | Uint8Array<ArrayBuffer>,
  fileName: string = generateUniqueId(),
): Promise<StoredDocument> {
  const response = await request.post(buildUrl('/documents'), {
    headers: defaultHeaders(),
    multipart: CREATE_DOCUMENT_REQUEST_WITH_CONTENT_TYPE(
      fileName,
      contentType,
      content,
    ),
  });
  await assertStatusCode(response, 201);
  const document = await response.json();

  return {
    documentId: document.documentId,
    contentHash: document.contentHash,
    fileName,
    url: buildUrl(
      '/documents/{documentId}',
      {documentId: document.documentId},
      {contentHash: document.contentHash},
    ),
  };
}

export async function getDocumentContent(
  request: APIRequestContext,
  document: Pick<StoredDocument, 'documentId' | 'contentHash'>,
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
