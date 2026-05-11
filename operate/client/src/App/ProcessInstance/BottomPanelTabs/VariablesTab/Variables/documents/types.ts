/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Document reference shape produced by Camunda Forms file picker and the document
// service. Recognition is per-variable via the camunda.document.type discriminator.
type DocumentReference = {
  'camunda.document.type': 'camunda';
  documentId: string;
  storeId?: string;
  contentHash?: string;
  metadata: {
    fileName: string;
    contentType?: string;
    size?: number;
    expiresAt?: string;
    processDefinitionId?: string;
    processInstanceKey?: string;
  };
};

type DocumentRecognition =
  | {kind: 'none'}
  | {kind: 'single'; document: DocumentReference}
  | {kind: 'list'; documents: DocumentReference[]}
  | {kind: 'truncated-list'; partialCount: number};

export type {DocumentReference, DocumentRecognition};
