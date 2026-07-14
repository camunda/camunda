/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DocumentReference} from '@camunda/camunda-api-zod-schemas/8.10';
import {relaxedDocumentReferenceSchema, toDocumentInfo} from './documentInfo';
import * as clientConfig from 'modules/utils/getClientConfig';

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

describe('toDocumentInfo', () => {
  it('should map a document reference to a document info', () => {
    const result = toDocumentInfo(makeDocRef());

    expect(result).toEqual({
      link: '/v2/documents/doc-123?storeId=in-memory&contentHash=sha256%3Aabc',
      fileName: 'photo.png',
      type: 'image',
      contentType: 'image/png',
      size: 109748,
      isExpired: false,
    });
  });

  it('should construct a document link with context path', () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      contextPath: '/some-context',
    });

    const result = toDocumentInfo(makeDocRef());

    expect(result.link).toBe(
      '/some-context/v2/documents/doc-123?storeId=in-memory&contentHash=sha256%3Aabc',
    );
  });

  it('should set link to null when contentHash is null', () => {
    const result = toDocumentInfo(makeDocRef({contentHash: null}));

    expect(result.link).toBeNull();
  });

  it('should set isExpired=true when expiresAt is in the past', () => {
    vi.setSystemTime('2026-06-01T00:00:00.000Z');
    const result = toDocumentInfo(
      makeDocRef({
        metadata: {
          ...makeDocRef().metadata,
          expiresAt: '2026-05-01T00:00:00.000Z',
        },
      }),
    );

    expect(result.isExpired).toBe(true);
  });

  it('should set isExpired=false when expiresAt is in the future', () => {
    vi.setSystemTime('2026-06-01T00:00:00.000Z');
    const result = toDocumentInfo(
      makeDocRef({
        metadata: {
          ...makeDocRef().metadata,
          expiresAt: '2026-06-04T00:00:00.000Z',
        },
      }),
    );

    expect(result.isExpired).toBe(false);
  });

  it('should set isExpired=false when expiresAt is null', () => {
    const result = toDocumentInfo(
      makeDocRef({metadata: {...makeDocRef().metadata, expiresAt: null}}),
    );

    expect(result.isExpired).toBe(false);
  });

  it('should map an image contentType to the image document type', () => {
    for (const contentType of [
      'image/jpeg',
      'image/png',
      'image/gif',
      'image/webp',
    ]) {
      const result = toDocumentInfo(
        makeDocRef({metadata: {...makeDocRef().metadata, contentType}}),
      );

      expect(result.type).toBe('image');
    }
  });

  it('should map application/json to the json document type', () => {
    const result = toDocumentInfo(
      makeDocRef({
        metadata: {...makeDocRef().metadata, contentType: 'application/json'},
      }),
    );

    expect(result.type).toBe('json');
  });

  it('should map application/pdf to the pdf document type', () => {
    const result = toDocumentInfo(
      makeDocRef({
        metadata: {...makeDocRef().metadata, contentType: 'application/pdf'},
      }),
    );

    expect(result.type).toBe('pdf');
  });

  it('should map an unrecognized contentType to the unknown document type', () => {
    const result = toDocumentInfo(
      makeDocRef({
        metadata: {...makeDocRef().metadata, contentType: 'text/plain'},
      }),
    );

    expect(result.type).toBe('unknown');
  });
});

describe('relaxedDocumentReferenceSchema', () => {
  it('should accept a valid document reference', () => {
    const value = makeDocRef();

     expect(relaxedDocumentReferenceSchema.safeParse(value).success).toBe(
      true,
    );
  });

  it('should accept a connector reference that omits optional metadata fields', () => {
    const reference = {
      'camunda.document.type': 'camunda',
      storeId: 'in-memory',
      documentId: 'doc-123',
      contentHash: 'sha256:abc',
      metadata: {
        contentType: 'image/png',
        fileName: 'photo.png',
        size: 109748,
      },
    };

    expect(relaxedDocumentReferenceSchema.safeParse(reference).success).toBe(
      true,
    );
  });

  it('should reject a document reference missing required fields', () => {
    const incomplete = {
      'camunda.document.type': 'camunda',
      storeId: 'gcp',
    };

    expect(relaxedDocumentReferenceSchema.safeParse(incomplete).success).toBe(
      false,
    );
  });

  it('should reject a document reference without metadata', () => {
    const reference = {
      'camunda.document.type': 'camunda',
      storeId: 'in-memory',
      documentId: 'doc-no-meta',
      contentHash: 'sha256:abc',
    };

    expect(relaxedDocumentReferenceSchema.safeParse(reference).success).toBe(
      false,
    );
  });
});
