/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect} from 'vitest';
import {encodeFilterOperation, splitEncodedFilterOperation, decodeAdvancedStringFilter} from './advancedStringFilter';

describe('encodeFilterOperation', () => {
	it('should strip the $ prefix from the operator', () => {
		expect(encodeFilterOperation('$eq', 'order-123')).toBe('eq_order-123');
		expect(encodeFilterOperation('$like', 'order')).toBe('like_order');
	});
});

describe('splitEncodedFilterOperation', () => {
	it('should split on the first separator and restore the $ prefix', () => {
		expect(splitEncodedFilterOperation('eq_order_123')).toEqual({
			operator: '$eq',
			value: 'order_123',
		});
	});

	it('should return null when no separator is present', () => {
		expect(splitEncodedFilterOperation('eqorder')).toBeNull();
	});

	it('should return null for an unknown operator slug', () => {
		expect(splitEncodedFilterOperation('foo_value')).toBeNull();
	});
});

describe('decodeAdvancedStringFilter', () => {
	it('should coerce $exists to a real boolean, not the encoded string', () => {
		expect(decodeAdvancedStringFilter('exists_true')).toEqual({$exists: true});
		expect(decodeAdvancedStringFilter('exists_false')).toEqual({$exists: false});
	});

	it('should wrap $like values with wildcards', () => {
		expect(decodeAdvancedStringFilter('like_order')).toEqual({$like: '*order*'});
	});

	it('should split $in into an array of ids', () => {
		expect(decodeAdvancedStringFilter('in_1,2,3')).toEqual({$in: ['1', '2', '3']});
	});

	it('should reject $in when it decodes to an empty id (e.g. a trailing or leading comma)', () => {
		expect(decodeAdvancedStringFilter('in_1,2,')).toBeUndefined();
		expect(decodeAdvancedStringFilter('in_,1,2')).toBeUndefined();
		expect(decodeAdvancedStringFilter('in_,')).toBeUndefined();
	});

	it('should return undefined for a malformed encoded value', () => {
		expect(decodeAdvancedStringFilter('not-encoded')).toBeUndefined();
	});
});
