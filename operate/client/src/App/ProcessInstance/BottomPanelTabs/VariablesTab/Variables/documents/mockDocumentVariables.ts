/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Variable} from '@camunda/camunda-api-zod-schemas/8.10';

// Prototype-only fixture. Injected client-side into the variables list so the
// document-recognition UI has data to render without a backend.
//
// TODO: Replace with API call to GET /v2/variables/search and remove this
// fixture once the engine emits document references in real variables.

const baseFields = {
  tenantId: '<default>',
  isTruncated: false,
  scopeKey: 'mock-scope',
  processInstanceKey: 'mock-process-instance',
  rootProcessInstanceKey: null,
} as const;

// Simple reference — variable holding a single document reference object.
const claimPhotoVariable: Variable = {
  ...baseFields,
  variableKey: 'mock-doc-claim-photo',
  name: 'claimPhoto',
  value: JSON.stringify({
    'camunda.document.type': 'camunda',
    documentId: 'doc-claim-photo-001',
    storeId: 'in-memory',
    metadata: {
      fileName: 'claim-photo.jpg',
      contentType: 'image/jpeg',
      size: 354304,
      processDefinitionId: 'claim-process',
    },
  }),
};

const compareReportVariable: Variable = {
  ...baseFields,
  variableKey: 'mock-doc-compare-report',
  name: 'comparisonReport',
  value: JSON.stringify({
    'camunda.document.type': 'camunda',
    documentId: 'doc-compare-002',
    storeId: 'in-memory',
    metadata: {
      fileName: 'EN-Camunda-Compared-to-Alternatives-2024.pdf',
      contentType: 'application/pdf',
      size: 209920,
    },
  }),
};

const processDataVariable: Variable = {
  ...baseFields,
  variableKey: 'mock-doc-process-data',
  name: 'processData',
  value: JSON.stringify({
    'camunda.document.type': 'camunda',
    documentId: 'doc-process-data-003',
    storeId: 'in-memory',
    metadata: {
      fileName: 'process-data.json',
      contentType: 'application/json',
      size: 427,
    },
  }),
};

// Mixed object with nested document references (e.g. agent memory). Out of
// scope for the document UI — renders as a plain JSON variable.
const agentMemoryVariable: Variable = {
  ...baseFields,
  variableKey: 'mock-doc-agent-memory',
  name: 'agentMemory',
  value: JSON.stringify({
    agentId: 'support-copilot-1',
    conversationHistory: [
      {role: 'user', content: 'I need help with my claim'},
      {
        role: 'assistant',
        content: 'Sure, can you upload supporting documents?',
      },
    ],
    attachedDocuments: [
      {
        'camunda.document.type': 'camunda',
        documentId: 'doc-mem-001',
        storeId: 'in-memory',
        metadata: {
          fileName: 'customer-history.json',
          contentType: 'application/json',
          size: 3657,
        },
      },
    ],
    tools: ['lookup_customer', 'search_kb'],
    sessionStartedAt: '2026-05-07T10:14:00Z',
  }),
};

// List of references (forms file picker) — always an array, even for one file.
const idDocumentsVariable: Variable = {
  ...baseFields,
  variableKey: 'mock-doc-id-documents',
  name: 'idDocuments',
  value: JSON.stringify([
    {
      'camunda.document.type': 'camunda',
      documentId: 'doc-id-front',
      storeId: 'in-memory',
      metadata: {
        fileName: 'id-front.jpg',
        contentType: 'image/jpeg',
        size: 354304,
      },
    },
    {
      'camunda.document.type': 'camunda',
      documentId: 'doc-id-back',
      storeId: 'in-memory',
      metadata: {
        fileName: 'id-back.jpg',
        contentType: 'image/jpeg',
        size: 354304,
      },
    },
    {
      'camunda.document.type': 'camunda',
      documentId: 'doc-proof-of-address',
      storeId: 'in-memory',
      metadata: {
        fileName: 'proof-of-address.pdf',
        contentType: 'application/pdf',
        size: 209920,
      },
    },
  ]),
};

// Truncated list of references — backend chops the variable at the truncation
// threshold, so the UI can only surface a lower-bound count until the user
// expands the variable.
const caseFilesFullDocuments = Array.from({length: 12}).map((_, index) => ({
  'camunda.document.type': 'camunda',
  documentId: `doc-case-file-${(index + 1).toString().padStart(2, '0')}`,
  storeId: 'in-memory',
  metadata: {
    fileName: `case-file-${(index + 1).toString().padStart(2, '0')}.pdf`,
    contentType: 'application/pdf',
    size: 200000 + index * 1024,
  },
}));
const caseFilesFullValue = JSON.stringify(caseFilesFullDocuments);
const caseFilesVariable: Variable = {
  ...baseFields,
  isTruncated: true,
  variableKey: 'mock-doc-case-files',
  name: 'caseFiles',
  // Slice mid-array so the truncation discards the tail of the list and lands
  // somewhere inside an unfinished document reference.
  value: caseFilesFullValue.slice(0, 760),
};

// Regular non-document variables — keep the type filter meaningful.
const regularVariables: Variable[] = [
  {
    ...baseFields,
    variableKey: 'mock-customer-id',
    name: 'customerId',
    value: '"CUST-08423"',
  },
  {
    ...baseFields,
    variableKey: 'mock-claim-amount',
    name: 'claimAmount',
    value: '4250.0',
  },
  {
    ...baseFields,
    variableKey: 'mock-status',
    name: 'status',
    value: '"AWAITING_REVIEW"',
  },
];

const MOCK_DOCUMENT_VARIABLES: Variable[] = [
  claimPhotoVariable,
  compareReportVariable,
  processDataVariable,
  agentMemoryVariable,
  idDocumentsVariable,
  caseFilesVariable,
  ...regularVariables,
];

// Map document IDs to public assets so the preview modal can render real files.
const MOCK_DOCUMENT_ASSET_PATHS: Record<string, string> = {
  'doc-claim-photo-001': '/nature.jpg',
  'doc-compare-002': '/EN-Camunda-Compared-to-Alternatives-2024.pdf',
  'doc-process-data-003': '/process-data.json',
  'doc-mem-001': '/ai_agent_memory.json',
  'doc-id-front': '/nature.jpg',
  'doc-id-back': '/nature.jpg',
  'doc-proof-of-address': '/EN-Camunda-Compared-to-Alternatives-2024.pdf',
};

export {MOCK_DOCUMENT_VARIABLES, MOCK_DOCUMENT_ASSET_PATHS};
