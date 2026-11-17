/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DocumentReference} from '@camunda/camunda-api-zod-schemas/8.10';
import {parseDocumentVariable} from './parseDocumentVariable';

const makeDocRef = (
  overrides: Record<string, unknown> = {},
): DocumentReference => ({
  'camunda.document.type': 'camunda',
  storeId: 'in-memory',
  documentId: 'doc-123',
  contentHash: 'sha256:abc',
  metadata: {
    contentType: 'image/png',
    fileName: 'photo.png',
    expiresAt: null,
    size: 109748,
    processDefinitionId: null,
    processInstanceKey: null,
    customProperties: {},
  },
  ...overrides,
});

describe('parseDocumentVariable', () => {
  it('should detect a single document reference', () => {
    const value = JSON.stringify(makeDocRef());
    const result = parseDocumentVariable(value, false);

    expect(result).toEqual({
      type: 'single',
      document: {
        link: '/v2/documents/doc-123?storeId=in-memory&contentHash=sha256%3Aabc',
        fileName: 'photo.png',
        type: 'image',
        contentType: 'image/png',
        size: 109748,
        isExpired: false,
      },
    });
  });

  it('should detect a connector reference that omits optional metadata fields', () => {
    const value = JSON.stringify({
      'camunda.document.type': 'camunda',
      storeId: 'in-memory',
      documentId: 'doc-123',
      contentHash: 'sha256:abc',
      metadata: {
        contentType: 'image/png',
        fileName: 'photo.png',
        size: 109748,
      },
    });
    const result = parseDocumentVariable(value, false);

    expect(result).toEqual({
      type: 'single',
      document: {
        link: '/v2/documents/doc-123?storeId=in-memory&contentHash=sha256%3Aabc',
        fileName: 'photo.png',
        type: 'image',
        contentType: 'image/png',
        size: 109748,
        isExpired: false,
      },
    });
  });

  it('should detect an array with a single document as single', () => {
    const value = JSON.stringify([makeDocRef()]);
    const result = parseDocumentVariable(value, false);

    expect(result).toEqual({
      type: 'single',
      document: {
        link: '/v2/documents/doc-123?storeId=in-memory&contentHash=sha256%3Aabc',
        fileName: 'photo.png',
        type: 'image',
        contentType: 'image/png',
        size: 109748,
        isExpired: false,
      },
    });
  });

  it('should detect an array of document references', () => {
    const value = JSON.stringify([
      makeDocRef({
        documentId: 'doc-123',
        metadata: {
          ...makeDocRef().metadata,
          fileName: 'a.pdf',
          contentType: 'application/pdf',
          size: 1000,
        },
      }),
      makeDocRef({
        documentId: 'doc-124',
        metadata: {
          ...makeDocRef().metadata,
          fileName: 'b.json',
          contentType: 'application/json',
          size: 500,
        },
      }),
      makeDocRef({
        documentId: 'doc-125',
        metadata: {
          ...makeDocRef().metadata,
          fileName: 'c.txt',
          contentType: 'text/plain',
          size: 200,
        },
      }),
    ]);
    const result = parseDocumentVariable(value, false);

    expect(result).toEqual({
      type: 'list',
      documents: [
        {
          link: '/v2/documents/doc-123?storeId=in-memory&contentHash=sha256%3Aabc',
          fileName: 'a.pdf',
          type: 'pdf',
          contentType: 'application/pdf',
          size: 1000,
          isExpired: false,
        },
        {
          link: '/v2/documents/doc-124?storeId=in-memory&contentHash=sha256%3Aabc',
          fileName: 'b.json',
          type: 'json',
          contentType: 'application/json',
          size: 500,
          isExpired: false,
        },
        {
          link: '/v2/documents/doc-125?storeId=in-memory&contentHash=sha256%3Aabc',
          fileName: 'c.txt',
          type: 'unknown',
          contentType: 'text/plain',
          size: 200,
          isExpired: false,
        },
      ],
      isLowerBound: false,
    });
  });

  it('should return null for a plain string variable', () => {
    const value = '"hello world"';
    const result = parseDocumentVariable(value, false);

    expect(result).toBeNull();
  });

  it('should return null for a regular JSON object', () => {
    const value = JSON.stringify({foo: 'bar', baz: 123});
    const result = parseDocumentVariable(value, false);

    expect(result).toBeNull();
  });

  it('should return null for an empty array', () => {
    const value = '[]';
    const result = parseDocumentVariable(value, false);

    expect(result).toBeNull();
  });

  it('should return null for an array of non-document objects', () => {
    const value = JSON.stringify([{name: 'foo'}, {name: 'bar'}]);
    const result = parseDocumentVariable(value, false);

    expect(result).toBeNull();
  });

  it('should return null for a mixed array (some doc refs, some not)', () => {
    const value = JSON.stringify([makeDocRef(), {name: 'not-a-doc'}]);
    const result = parseDocumentVariable(value, false);

    expect(result).toBeNull();
  });

  it('should return null for invalid JSON', () => {
    const value = '{not valid json';
    const result = parseDocumentVariable(value, false);

    expect(result).toBeNull();
  });

  it('should detect a truncated single document reference', () => {
    const fullJson = JSON.stringify(makeDocRef());
    const truncated = fullJson.slice(0, fullJson.length - 2);
    const result = parseDocumentVariable(truncated, true);

    assert(
      result !== null && result.type === 'single',
      'Expected a single parsing result.',
    );
    expect(result!.document.fileName).toBe('photo.png');
  });

  it('should detect a truncated array of document references with lower bound', () => {
    const docs = [
      makeDocRef({
        metadata: {...makeDocRef().metadata, fileName: 'a.pdf', size: 1000},
      }),
      makeDocRef({
        metadata: {...makeDocRef().metadata, fileName: 'b.json', size: 500},
      }),
    ];
    const fullJson = JSON.stringify([...docs, makeDocRef()]);
    const truncated = fullJson.slice(0, fullJson.length - 30);
    const result = parseDocumentVariable(truncated, true);

    assert(
      result !== null && result.type === 'list',
      'Expected a list parsing result.',
    );
    expect(result.documents.length).toBeGreaterThanOrEqual(2);
    expect(result.isLowerBound).toBe(true);
  });
});
