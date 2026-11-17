/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getIdPattern, isValidId} from './identifierPattern';

describe('isValidId', () => {
	it.for(['my-id', 'my_id', 'my.id', 'my~id', 'my@id', 'my+id', 'MyId123'])('should accept "%s"', (value) => {
		expect(isValidId(value)).toBe(true);
	});

	it.for(['my id', 'my/id', 'my#id', 'my!id', ''])('should reject "%s"', (value) => {
		expect(isValidId(value)).toBe(false);
	});

	it('should accept exactly 256 characters', () => {
		expect(isValidId('a'.repeat(256))).toBe(true);
	});

	it('should reject more than 256 characters', () => {
		expect(isValidId('a'.repeat(257))).toBe(false);
	});

	it('should use the configured pattern when one is provided', () => {
		// given
		const configuredPattern = '^[a-z]+$';

		// when
		const accepted = isValidId('abc', configuredPattern);
		const rejected = isValidId('abc1', configuredPattern);

		// then
		expect(accepted).toBe(true);
		expect(rejected).toBe(false);
	});

	it('should fall back to the default pattern when the configured one is empty', () => {
		// given
		const configuredPattern = '';

		// when
		const pattern = getIdPattern(configuredPattern);

		// then
		expect(pattern.test('demo')).toBe(true);
		expect(pattern.test('a b')).toBe(false);
	});
});
