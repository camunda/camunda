/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {parseSearchValueSafe} from './parseSearchValueSafe';

describe('parseSearchValueSafe', () => {
	it('should keep a 64-bit key past MAX_SAFE_INTEGER as a string instead of rounding it', () => {
		expect(parseSearchValueSafe('2251799813685467123456789')).toBe('2251799813685467123456789');
	});

	it('should parse a safe integer as a number, matching JSON.parse', () => {
		expect(parseSearchValueSafe('2251799813685467')).toBe(2251799813685467);
	});

	it('should parse small integers as numbers', () => {
		expect(parseSearchValueSafe('42')).toBe(42);
	});

	it('should keep a leading-zero string as-is, matching JSON.parse throwing and falling back to the raw string', () => {
		expect(parseSearchValueSafe('007')).toBe('007');
	});

	it('should parse floats as numbers, matching JSON.parse', () => {
		expect(parseSearchValueSafe('3.14')).toBe(3.14);
	});

	it('should parse booleans as booleans, matching JSON.parse', () => {
		expect(parseSearchValueSafe('true')).toBe(true);
	});

	it('should parse a quoted string as its unquoted string content', () => {
		expect(parseSearchValueSafe('"2251799813685467123456789"')).toBe('2251799813685467123456789');
	});

	it('should throw for a non-JSON string, matching JSON.parse — callers rely on this to leave plain strings unparsed', () => {
		expect(() => parseSearchValueSafe('order-process')).toThrow();
	});
});
