/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {endpoints} from '@camunda/camunda-api-zod-schemas/8.10';
import {requestWithThrow} from 'modules/request';

/**
 * Fetch a document's content from the Camunda Document Store.
 *
 * Uses `GET /v2/documents/{documentId}?storeId=...&contentHash=...`
 * and returns the raw text body (expected to be JSON for conversation docs).
 */
const fetchDocument = async ({
  documentId,
  storeId,
  contentHash,
}: {
  documentId: string;
  storeId?: string;
  contentHash?: string;
}) => {
  return requestWithThrow<string>({
    url: endpoints.getDocument.getUrl({documentId, storeId, contentHash}),
    method: endpoints.getDocument.method,
    responseType: 'text',
  });
};

export {fetchDocument};
