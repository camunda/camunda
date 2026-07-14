/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  relaxedDocumentReferenceSchema,
  toDocumentInfo,
  type DocumentInfo,
  type DocumentReference,
} from 'App/ProcessInstance/DocumentsView/documentInfo';
import {safeJsonParse} from 'modules/utils';
import {untruncateJson} from 'modules/utils/editor/untruncateJSON';

type DocumentParseResult =
  | {type: 'single'; document: DocumentInfo}
  | {type: 'list'; documents: DocumentInfo[]; isLowerBound: boolean};

function isDocumentReference(value: unknown): value is DocumentReference {
  return relaxedDocumentReferenceSchema.safeParse(value).success;
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

export {parseDocumentVariable};
export type {DocumentParseResult};
