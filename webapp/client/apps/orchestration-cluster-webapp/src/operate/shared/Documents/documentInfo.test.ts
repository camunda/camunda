/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {createDocumentReference} from '#/shared-test-modules/api-mocks/documents';
import {isDocumentReference, toDocumentInfo} from './documentInfo';

const PAST = '2000-01-01T00:00:00.000Z';
const FUTURE = '2999-01-01T00:00:00.000Z';

describe('toDocumentInfo', () => {
	it('should map a document reference to a document info with a store-scoped link', () => {
		expect(
			toDocumentInfo(
				createDocumentReference({
					documentId: 'doc-123',
					storeId: 'gcp',
					contentHash: 'sha256:abc',
					metadata: {fileName: 'photo.png', contentType: 'image/png', size: 2048},
				}),
			),
		).toEqual({
			link: `${window.location.origin}/v2/documents/doc-123?storeId=gcp&contentHash=sha256%3Aabc`,
			fileName: 'photo.png',
			type: 'image',
			contentType: 'image/png',
			size: 2048,
			isExpired: false,
		});
	});

	it('should have no link when the content hash is missing', () => {
		expect(toDocumentInfo(createDocumentReference({contentHash: null})).link).toBeNull();
	});

	it.for([
		{expiresAt: PAST, isExpired: true},
		{expiresAt: FUTURE, isExpired: false},
		{expiresAt: null, isExpired: false},
	])('should set isExpired to $isExpired when expiresAt is $expiresAt', ({expiresAt, isExpired}) => {
		expect(toDocumentInfo(createDocumentReference({metadata: {expiresAt}})).isExpired).toBe(isExpired);
	});

	it.for([
		['image/jpeg', 'image'],
		['image/png', 'image'],
		['image/gif', 'image'],
		['image/webp', 'image'],
		['application/pdf', 'pdf'],
		['application/json', 'json'],
		['text/plain', 'unknown'],
	] as const)('should map %s to the %s document type', ([contentType, type]) => {
		expect(toDocumentInfo(createDocumentReference({metadata: {contentType}})).type).toBe(type);
	});
});

describe('isDocumentReference', () => {
	it('should accept a valid document reference', () => {
		expect(isDocumentReference(createDocumentReference())).toBe(true);
	});

	it('should accept a connector reference that omits optional metadata fields', () => {
		expect(
			isDocumentReference({
				'camunda.document.type': 'camunda',
				storeId: 'in-memory',
				documentId: 'doc-123',
				contentHash: 'sha256:abc',
				metadata: {contentType: 'image/png', fileName: 'photo.png', size: 109748},
			}),
		).toBe(true);
	});

	it('should accept loosely formatted metadata values', () => {
		expect(
			isDocumentReference(
				createDocumentReference({
					metadata: {expiresAt: '2030-01-01T00:00:00+02:00', size: 1.5, processInstanceKey: 'not-a-key'},
				}),
			),
		).toBe(true);
	});

	it('should reject a document reference missing required fields', () => {
		expect(isDocumentReference({'camunda.document.type': 'camunda', storeId: 'gcp'})).toBe(false);
	});

	it('should reject a document reference without metadata', () => {
		expect(
			isDocumentReference({
				'camunda.document.type': 'camunda',
				storeId: 'in-memory',
				documentId: 'doc-no-meta',
				contentHash: 'sha256:abc',
			}),
		).toBe(false);
	});
});
