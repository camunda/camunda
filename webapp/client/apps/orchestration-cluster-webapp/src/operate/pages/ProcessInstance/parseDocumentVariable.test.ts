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
import {parseDocumentVariable} from './parseDocumentVariable';

const reference = (documentId: string, fileName = `${documentId}.pdf`) =>
	createDocumentReference({documentId, metadata: {fileName}});

const truncateInsideLastDocument = (value: string) => value.slice(0, value.lastIndexOf('"documentId"'));

describe('parseDocumentVariable', () => {
	it('should detect a single document reference', () => {
		expect(parseDocumentVariable(JSON.stringify(reference('doc-1')), false)).toEqual({
			type: 'single',
			document: {
				link: `${window.location.origin}/v2/documents/doc-1?storeId=in-memory&contentHash=sha256-xyz`,
				fileName: 'doc-1.pdf',
				type: 'pdf',
				contentType: 'application/pdf',
				size: 1024,
				isExpired: false,
			},
		});
	});

	it('should detect an array with a single document as single', () => {
		const result = parseDocumentVariable(JSON.stringify([reference('doc-1')]), false);

		expect(result?.type).toBe('single');
	});

	it('should detect an array of document references as an exact list', () => {
		const result = parseDocumentVariable(JSON.stringify([reference('a'), reference('b'), reference('c')]), false);

		expect(result).toMatchObject({
			type: 'list',
			documents: [{fileName: 'a.pdf'}, {fileName: 'b.pdf'}, {fileName: 'c.pdf'}],
			isLowerBound: false,
		});
	});

	it.for([
		['a plain string', '"hello world"'],
		['a number', '42'],
		['a regular object', '{"name":"Jane"}'],
		['an empty array', '[]'],
		['an array of non-document objects', '[{"a":1},{"b":2}]'],
		['a mixed array', JSON.stringify([reference('doc-1'), {a: 1}])],
		['a nested document reference', JSON.stringify({wrapper: reference('doc-1')})],
		['a string mentioning the document marker', JSON.stringify('please attach your camunda.document.type here')],
		['invalid JSON', '{not json'],
	])('should not detect documents in %s', ([, value = '']) => {
		expect(parseDocumentVariable(value, false)).toBeNull();
	});

	it('should detect a truncated single document reference', () => {
		const value = JSON.stringify(reference('doc-1', 'photo.png'));

		const result = parseDocumentVariable(value.slice(0, -2), true);

		expect(result).toMatchObject({type: 'single', document: {fileName: 'photo.png'}});
	});

	it('should detect a truncated array of document references as a lower bound list', () => {
		const value = JSON.stringify([reference('a'), reference('b'), reference('c')]);

		const result = parseDocumentVariable(truncateInsideLastDocument(value), true);

		expect(result).toMatchObject({
			type: 'list',
			documents: [{fileName: 'a.pdf'}, {fileName: 'b.pdf'}],
			isLowerBound: true,
		});
	});

	it('should keep a truncated array with a single complete document as a lower bound list', () => {
		const value = JSON.stringify([reference('a'), reference('b')]);

		const result = parseDocumentVariable(truncateInsideLastDocument(value), true);

		expect(result).toMatchObject({type: 'list', documents: [{fileName: 'a.pdf'}], isLowerBound: true});
	});
});
