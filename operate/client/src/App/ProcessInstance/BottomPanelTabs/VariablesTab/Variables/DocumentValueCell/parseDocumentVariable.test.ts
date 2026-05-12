/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {parseDocumentVariable, formatFileSize} from './parseDocumentVariable';

const makeDocRef = (overrides: Record<string, unknown> = {}) => ({
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
        fileName: 'photo.png',
        size: 109748,
      },
    });
  });

  it('should detect an array with a single document as single', () => {
    const value = JSON.stringify([makeDocRef()]);
    const result = parseDocumentVariable(value, false);

    expect(result).toEqual({
      type: 'single',
      document: {
        fileName: 'photo.png',
        size: 109748,
      },
    });
  });

  it('should detect an array of document references', () => {
    const value = JSON.stringify([
      makeDocRef({
        metadata: {...makeDocRef().metadata, fileName: 'a.pdf', size: 1000},
      }),
      makeDocRef({
        metadata: {...makeDocRef().metadata, fileName: 'b.json', size: 500},
      }),
      makeDocRef({
        metadata: {...makeDocRef().metadata, fileName: 'c.txt', size: 200},
      }),
    ]);
    const result = parseDocumentVariable(value, false);

    expect(result).toEqual({
      type: 'list',
      documents: [
        {fileName: 'a.pdf', size: 1000},
        {fileName: 'b.json', size: 500},
        {fileName: 'c.txt', size: 200},
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
    const truncated = fullJson.slice(0, fullJson.length - 5);
    const result = parseDocumentVariable(truncated, true);

    expect(result).not.toBeNull();
    expect(result!.type).toBe('single');
    expect(
      (result as {type: 'single'; document: {fileName: string}}).document
        .fileName,
    ).toBe('photo.png');
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

    expect(result).not.toBeNull();
    expect(result!.type).toBe('list');
    const listResult = result as {
      type: 'list';
      documents: unknown[];
      isLowerBound: boolean;
    };
    expect(listResult.documents.length).toBeGreaterThanOrEqual(2);
    expect(listResult.isLowerBound).toBe(true);
  });

  it('should return null for a document reference missing required fields', () => {
    const incomplete = {
      'camunda.document.type': 'camunda',
      storeId: 'gcp',
    };
    const value = JSON.stringify(incomplete);
    const result = parseDocumentVariable(value, false);

    expect(result).toBeNull();
  });

  it('should detect a document reference without metadata', () => {
    const value = JSON.stringify({
      'camunda.document.type': 'camunda',
      storeId: 'in-memory',
      documentId: 'doc-no-meta',
      contentHash: 'sha256:abc',
    });
    const result = parseDocumentVariable(value, false);

    expect(result).toEqual({
      type: 'single',
      document: {
        fileName: 'doc-no-meta',
        size: undefined,
      },
    });
  });
});

describe('formatFileSize', () => {
  it('should format bytes', () => {
    expect(formatFileSize(0)).toBe('0 B');
    expect(formatFileSize(427)).toBe('427 B');
    expect(formatFileSize(999)).toBe('999 B');
  });

  it('should format kilobytes', () => {
    expect(formatFileSize(1000)).toBe('1 KB');
    expect(formatFileSize(346000)).toBe('346 KB');
    expect(formatFileSize(999999)).toBe('1000 KB');
  });

  it('should format megabytes', () => {
    expect(formatFileSize(1000000)).toBe('1.0 MB');
    expect(formatFileSize(2500000)).toBe('2.5 MB');
    expect(formatFileSize(15000000)).toBe('15 MB');
  });

  it('should format gigabytes', () => {
    expect(formatFileSize(1000000000)).toBe('1.0 GB');
    expect(formatFileSize(25000000000)).toBe('25 GB');
  });
});
