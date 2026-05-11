/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DocumentReference, DocumentRecognition} from './types';

const DISCRIMINATOR_KEY = 'camunda.document.type';
const DISCRIMINATOR_VALUE = 'camunda';
const DISCRIMINATOR_PATTERN =
  /"camunda\.document\.type"\s*:\s*"camunda"/g;

function isDocumentReference(value: unknown): value is DocumentReference {
  return (
    typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value) &&
    (value as Record<string, unknown>)[DISCRIMINATOR_KEY] ===
      DISCRIMINATOR_VALUE
  );
}

function recognizeDocumentValue(
  rawValue: string,
  isTruncated = false,
): DocumentRecognition {
  let parsed: unknown;
  try {
    parsed = JSON.parse(rawValue);
  } catch {
    if (isTruncated) {
      return recognizeTruncatedList(rawValue);
    }
    return {kind: 'none'};
  }

  if (isDocumentReference(parsed)) {
    return {kind: 'single', document: parsed};
  }

  if (Array.isArray(parsed) && parsed.length > 0) {
    const allDocuments = parsed.every(isDocumentReference);
    if (allDocuments) {
      return {kind: 'list', documents: parsed as DocumentReference[]};
    }
  }

  return {kind: 'none'};
}

// Truncated values can't be JSON-parsed. Match the discriminator key as a
// string heuristic to surface a lower-bound count. False positives are
// possible but unlikely in practice.
function recognizeTruncatedList(rawValue: string): DocumentRecognition {
  if (!rawValue.trimStart().startsWith('[')) {
    return {kind: 'none'};
  }
  const matches = rawValue.match(DISCRIMINATOR_PATTERN);
  if (matches === null || matches.length === 0) {
    return {kind: 'none'};
  }
  return {kind: 'truncated-list', partialCount: matches.length};
}

export {recognizeDocumentValue, isDocumentReference};
