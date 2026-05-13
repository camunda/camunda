/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DocumentReference} from './types';
import {MOCK_CORRUPTED_DOCUMENT_IDS} from './mockDocumentVariables';

// A document is expired when its TTL has passed — the reference still exists
// in the variable but the underlying file has been purged from storage.
function isDocumentExpired(doc: DocumentReference): boolean {
  const expiresAt = doc.metadata.expiresAt;
  if (expiresAt === undefined) {
    return false;
  }
  return new Date(expiresAt).getTime() < Date.now();
}

// Corrupted documents have intact references but unreadable bytes. We assume
// the document service exposes this proactively (e.g. via a content-hash
// check or an integrity status flag).
//
// TODO: Replace MOCK_CORRUPTED_DOCUMENT_IDS lookup with the real signal from
// GET /v2/documents/{id} once it lands.
function isDocumentCorrupted(doc: DocumentReference): boolean {
  return MOCK_CORRUPTED_DOCUMENT_IDS.has(doc.documentId);
}

export {isDocumentExpired, isDocumentCorrupted};
