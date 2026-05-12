/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {untruncateJson} from 'modules/utils/editor/untruncateJSON';

type DocumentInfo = {
  fileName: string;
  size: number | undefined;
};

type DocumentParseResult =
  | {type: 'single'; document: DocumentInfo}
  | {type: 'list'; documents: DocumentInfo[]; isLowerBound: boolean};

const documentReferenceSchema = z
  .object({
    'camunda.document.type': z.literal('camunda'),
    documentId: z.string(),
    metadata: z
      .object({
        fileName: z.string().optional(),
        size: z.number().optional(),
      })
      .catchall(z.unknown())
      .optional(),
  })
  .catchall(z.unknown());

type DetectedDocumentReference = z.infer<typeof documentReferenceSchema>;

function isDocumentReference(
  value: unknown,
): value is DetectedDocumentReference {
  return documentReferenceSchema.safeParse(value).success;
}

function toDocumentInfo(ref: DetectedDocumentReference): DocumentInfo {
  return {
    fileName: ref.metadata?.fileName ?? ref.documentId,
    size: ref.metadata?.size,
  };
}

function safeJsonParse(value: string): unknown {
  try {
    return JSON.parse(value);
  } catch {
    return undefined;
  }
}

function parseDocumentVariable(
  value: string,
  isTruncated: boolean,
): DocumentParseResult | null {
  const {completed, collectionDepth} = isTruncated
    ? untruncateJson(value)
    : {completed: value, collectionDepth: 0};

  const parsed = safeJsonParse(completed);

  if (isDocumentReference(parsed)) {
    return {type: 'single', document: toDocumentInfo(parsed)};
  }

  if (!Array.isArray(parsed) || parsed.length === 0) {
    return null;
  }

  const docs = parsed.filter(isDocumentReference);
  if (docs.length === 0) {
    return null;
  }

  if (!isTruncated && docs.length !== parsed.length) {
    return null;
  }

  if (docs.length === 1 && !isTruncated) {
    return {type: 'single', document: toDocumentInfo(docs[0]!)};
  }

  return {
    type: 'list',
    documents: docs.map(toDocumentInfo),
    isLowerBound: collectionDepth > 0 || docs.length < parsed.length,
  };
}

function formatFileSize(bytes: number): string {
  if (bytes < 1000) {
    return `${bytes} B`;
  }
  if (bytes < 1000 * 1000) {
    return `${Math.round(bytes / 1000)} KB`;
  }
  if (bytes < 1000 * 1000 * 1000) {
    const mb = bytes / (1000 * 1000);
    return `${mb >= 10 ? Math.round(mb) : mb.toFixed(1)} MB`;
  }
  const gb = bytes / (1000 * 1000 * 1000);
  return `${gb >= 10 ? Math.round(gb) : gb.toFixed(1)} GB`;
}

export {parseDocumentVariable, formatFileSize};
export type {DocumentParseResult};
