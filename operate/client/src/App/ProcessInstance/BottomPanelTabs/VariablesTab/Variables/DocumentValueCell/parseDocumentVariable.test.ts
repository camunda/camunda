/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  parseDocumentVariable,
  toHumanReadableBytes,
} from './parseDocumentVariable';
import * as clientConfig from 'modules/utils/getClientConfig';

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
        link: '/v2/documents/doc-123?storeId=in-memory&contentHash=sha256%3Aabc',
        fileName: 'photo.png',
        type: 'image',
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
        link: '/v2/documents/doc-123?storeId=in-memory&contentHash=sha256%3Aabc',
        fileName: 'photo.png',
        type: 'image',
        size: 109748,
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
          type: 'unknown',
          size: 1000,
        },
        {
          link: '/v2/documents/doc-124?storeId=in-memory&contentHash=sha256%3Aabc',
          fileName: 'b.json',
          type: 'unknown',
          size: 500,
        },
        {
          link: '/v2/documents/doc-125?storeId=in-memory&contentHash=sha256%3Aabc',
          fileName: 'c.txt',
          type: 'unknown',
          size: 200,
        },
      ],
      isLowerBound: false,
    });
  });

  it('should construct a document link with context path', () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      contextPath: '/some-context',
    });

    const value = JSON.stringify(makeDocRef());
    const result = parseDocumentVariable(value, false);

    expect(result).toEqual({
      type: 'single',
      document: {
        link: '/some-context/v2/documents/doc-123?storeId=in-memory&contentHash=sha256%3Aabc',
        fileName: 'photo.png',
        type: 'image',
        size: 109748,
      },
    });
  });

  it('should parse a image document type for supported image formats', () => {
    for (const format of [
      'image/jpeg',
      'image/png',
      'image/gif',
      'image/webp',
    ]) {
      const value = JSON.stringify(
        makeDocRef({
          metadata: {...makeDocRef().metadata, contentType: format},
        }),
      );
      const result = parseDocumentVariable(value, false);

      expect(result).toEqual({
        type: 'single',
        document: expect.objectContaining({
          type: 'image',
        }),
      });
    }
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
        link: '/v2/documents/doc-no-meta?storeId=in-memory&contentHash=sha256%3Aabc',
        fileName: 'doc-no-meta',
        type: 'unknown',
        size: undefined,
      },
    });
  });
});

describe('toHumanReadableBytes', () => {
  it('should handle zero bytes', () => {
    expect(toHumanReadableBytes(0)).toBe('0 B');
  });

  it('should handle invalid input', () => {
    expect(toHumanReadableBytes(NaN)).toBe('N/A');
    expect(toHumanReadableBytes(Infinity)).toBe('N/A');
  });

  it('should format bytes correctly', () => {
    expect(toHumanReadableBytes(1024)).toBe('1 KiB');
    expect(toHumanReadableBytes(1024 * 1024)).toBe('1 MiB');
    expect(toHumanReadableBytes(1024 * 1024 * 1024)).toBe('1 GiB');
  });

  it('should format decimals correctly', () => {
    expect(toHumanReadableBytes(1536)).toBe('1.5 KiB');
    expect(toHumanReadableBytes(2560)).toBe('2.5 KiB');
    expect(toHumanReadableBytes(1536 * 1024)).toBe('1.5 MiB');
    expect(toHumanReadableBytes(1280)).toBe('1.25 KiB');
  });

  it('should handle small values', () => {
    expect(toHumanReadableBytes(1)).toBe('1 B');
    expect(toHumanReadableBytes(512)).toBe('512 B');
  });

  it('should cap large values at GiB', () => {
    const largeValue = Math.pow(1024, 4);
    expect(toHumanReadableBytes(largeValue)).toBe('1024 GiB');
  });
});
