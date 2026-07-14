/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {mergePathname} from 'modules/request/mergePathname';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {
  endpoints,
  documentMetadataSchema,
  documentReferenceSchema,
} from '@camunda/camunda-api-zod-schemas/8.10';

// connector-written references in variables may omit optional metadata keys.
const relaxedDocumentReferenceSchema = documentReferenceSchema.extend({
  metadata: documentMetadataSchema.partial({
    expiresAt: true,
    processDefinitionId: true,
    processInstanceKey: true,
    customProperties: true,
  }),
});
type DocumentReference = z.infer<typeof relaxedDocumentReferenceSchema>;

type DocumentType = 'image' | 'pdf' | 'json' | 'unknown';

type DocumentInfo = {
  fileName: string;
  link: string | null;
  type: DocumentType;
  contentType: string;
  size: number;
  isExpired: boolean;
};

const MIME_TYPE_MAP: Record<string, DocumentType> = {
  'image/jpeg': 'image',
  'image/png': 'image',
  'image/gif': 'image',
  'image/webp': 'image',
  'application/pdf': 'pdf',
  'application/json': 'json',
};

function getDocumentType(contentType: string): DocumentType {
  return MIME_TYPE_MAP[contentType] ?? 'unknown';
}

function toDocumentInfo(ref: DocumentReference): DocumentInfo {
  const link =
    ref.contentHash !== null
      ? mergePathname(
          getClientConfig().contextPath,
          endpoints.getDocument.getUrl({
            documentId: ref.documentId,
            storeId: ref.storeId,
            contentHash: ref.contentHash,
          }),
        )
      : null;

  const isExpired = ref.metadata.expiresAt
    ? Date.parse(ref.metadata.expiresAt) < Date.now()
    : false;

  return {
    link,
    fileName: ref.metadata.fileName ?? ref.documentId,
    type: getDocumentType(ref.metadata.contentType),
    contentType: ref.metadata.contentType,
    size: ref.metadata.size,
    isExpired,
  };
}

export {relaxedDocumentReferenceSchema, toDocumentInfo};
export type {DocumentInfo, DocumentReference};
