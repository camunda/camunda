/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {safeJsonParse} from 'modules/utils';
import {untruncateJson} from 'modules/utils/editor/untruncateJSON';
import {mergePathname} from 'modules/request/mergePathname';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.10';

type DocumentType = 'image' | 'pdf' | 'json' | 'unknown';

type DocumentInfo = {
  fileName: string;
  link: string;
  type: DocumentType;
  contentType?: string | undefined;
  size?: number | undefined;
};

type DocumentParseResult =
  | {type: 'single'; document: DocumentInfo}
  | {type: 'list'; documents: DocumentInfo[]; isLowerBound: boolean};

const documentReferenceSchema = z
  .object({
    'camunda.document.type': z.literal('camunda'),
    documentId: z.string(),
    storeId: z.string().optional(),
    contentHash: z.string(),
    metadata: z
      .object({
        fileName: z.string().optional(),
        contentType: z.string().optional(),
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

const MIME_TYPE_MAP: Record<string, DocumentType> = {
  'image/jpeg': 'image',
  'image/png': 'image',
  'image/gif': 'image',
  'image/webp': 'image',
  'application/pdf': 'pdf',
  'application/json': 'json',
};

function getDocumentType(contentType: string | undefined): DocumentType {
  if (!contentType) {
    return 'unknown';
  }
  return MIME_TYPE_MAP[contentType] ?? 'unknown';
}

function toDocumentInfo(ref: DetectedDocumentReference): DocumentInfo {
  const link = mergePathname(
    getClientConfig().contextPath,
    endpoints.getDocument.getUrl(ref),
  );
  return {
    link,
    fileName: ref.metadata?.fileName ?? ref.documentId,
    type: getDocumentType(ref.metadata?.contentType),
    contentType: ref.metadata?.contentType,
    size: ref.metadata?.size,
  };
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

const UNITS = ['B', 'KiB', 'MiB', 'GiB'];
const BYTES_BASE = 1024;

/**
 * Formats a number of bytes into a human readable string with binary prefixes (up to GiB)
 * @param bytes - The number of bytes to format
 * @returns Formatted string (e.g. "1.5 MiB")
 */
function toHumanReadableBytes(bytes: number): string {
  if (bytes === 0) {
    return '0 B';
  }

  if (!Number.isFinite(bytes)) {
    return 'N/A';
  }

  const exponent = Math.min(
    Math.floor(Math.log(bytes) / Math.log(BYTES_BASE)),
    UNITS.length - 1,
  );

  const value = bytes / Math.pow(BYTES_BASE, exponent);
  const unit = UNITS[exponent];
  const formatted = value.toFixed(2).replace(/\.?0+$/, '');

  return `${formatted} ${unit}`;
}

export {parseDocumentVariable, toHumanReadableBytes};
export type {DocumentInfo, DocumentParseResult};
