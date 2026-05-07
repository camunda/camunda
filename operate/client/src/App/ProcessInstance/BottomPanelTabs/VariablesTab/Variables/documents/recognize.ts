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

function isDocumentReference(value: unknown): value is DocumentReference {
  return (
    typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value) &&
    (value as Record<string, unknown>)[DISCRIMINATOR_KEY] ===
      DISCRIMINATOR_VALUE
  );
}

function collectEmbeddedDocuments(
  value: unknown,
  out: DocumentReference[],
): void {
  if (Array.isArray(value)) {
    value.forEach((item) => collectEmbeddedDocuments(item, out));
    return;
  }
  if (typeof value === 'object' && value !== null) {
    if (isDocumentReference(value)) {
      out.push(value);
      return;
    }
    Object.values(value).forEach((nested) =>
      collectEmbeddedDocuments(nested, out),
    );
  }
}

function recognizeDocumentValue(rawValue: string): DocumentRecognition {
  let parsed: unknown;
  try {
    parsed = JSON.parse(rawValue);
  } catch {
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

  const embedded: DocumentReference[] = [];
  collectEmbeddedDocuments(parsed, embedded);
  if (embedded.length > 0) {
    return {kind: 'embedded', documents: embedded};
  }

  return {kind: 'none'};
}

export {recognizeDocumentValue, isDocumentReference};
