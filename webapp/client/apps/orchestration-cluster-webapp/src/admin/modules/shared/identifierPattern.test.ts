/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {isCustomIdPattern, isValidId} from './identifierPattern';

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

	it('should reject more than 256 characters even if the configured pattern allows them', () => {
		// given
		const configuredPattern = '^[a-z]+$';

		// when
		const accepted = isValidId('a'.repeat(256), configuredPattern);
		const rejected = isValidId('a'.repeat(257), configuredPattern);

		// then
		expect(accepted).toBe(true);
		expect(rejected).toBe(false);
	});

	it('should fall back to the default pattern when the configured one is empty', () => {
		// given
		const configuredPattern = '';

		// when
		const accepted = isValidId('demo', configuredPattern);
		const rejected = isValidId('a b', configuredPattern);

		// then
		expect(accepted).toBe(true);
		expect(rejected).toBe(false);
	});

	it('should leave a pattern the browser cannot parse to the server, but keep the length limit', () => {
		// given
		const javaOnlyPattern = '^[a-z]++$';

		// when
		const accepted = isValidId('Any ID!', javaOnlyPattern);
		const rejected = isValidId('a'.repeat(257), javaOnlyPattern);

		// then
		expect(accepted).toBe(true);
		expect(rejected).toBe(false);
	});
});

describe('isValidId with Java patterns', () => {
	it('should read Unicode property classes the way the server does', () => {
		// given
		const lettersOnly = '^[\\p{L}]+$';

		// when
		const accepted = ['abc', 'Zoë'].map((id) => isValidId(id, lettersOnly));
		const rejected = isValidId('a1', lettersOnly);

		// then
		expect(accepted).toEqual([true, true]);
		expect(rejected).toBe(false);
	});

	it('should leave a Java-only property class to the server instead of rejecting valid IDs', () => {
		// given
		const javaOnlyPattern = '^\\p{javaLowerCase}+$';

		// when
		const result = isValidId('abc', javaOnlyPattern);

		// then
		expect(result).toBe(true);
	});
});

describe('isCustomIdPattern', () => {
	it('should not treat the default pattern the server serves as custom', () => {
		expect(isCustomIdPattern('^[a-zA-Z0-9_~@.+-]+$')).toBe(false);
	});

	it.for([null, undefined, ''])('should not treat %s as custom', (configuredPattern) => {
		expect(isCustomIdPattern(configuredPattern)).toBe(false);
	});

	it('should treat any other pattern as custom', () => {
		expect(isCustomIdPattern('^[a-z]+$')).toBe(true);
	});
});
