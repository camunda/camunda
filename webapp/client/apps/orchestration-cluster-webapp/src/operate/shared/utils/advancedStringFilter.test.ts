/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect} from 'vitest';
import {encodeFilterOperation, splitEncodedFilterOperation} from './advancedStringFilter';

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
