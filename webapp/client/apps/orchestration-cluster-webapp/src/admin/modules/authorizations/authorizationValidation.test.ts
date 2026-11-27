/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {
	AUTHORIZATION_WILDCARD,
	isValidProbeResourceId,
	isValidResourceId,
	isValidSecretResourceId,
} from './authorizationValidation';

describe('authorizationValidation', () => {
	describe('isValidResourceId', () => {
		it('should accept the wildcard even if the configured pattern forbids it', () => {
			// given
			const configuredPattern = '^[a-z]+$';

			// when
			const result = isValidResourceId(AUTHORIZATION_WILDCARD, configuredPattern);

			// then
			expect(result).toBe(true);
		});

		it('should validate non-wildcard IDs against the pattern', () => {
			// given
			const id = 'order process';

			// when
			const result = isValidResourceId(id);

			// then
			expect(result).toBe(false);
		});
	});

	describe('isValidSecretResourceId', () => {
		it.for(['*', 'camunda.secrets.my-secret_1'])('should accept %s', (id) => {
			// when
			const result = isValidSecretResourceId(id);

			// then
			expect(result).toBe(true);
		});

		it.for(['my-secret', 'camunda.secrets.', 'camunda.secrets.bad name', `camunda.secrets.${'a'.repeat(241)}`])(
			'should reject %s',
			(id) => {
				// when
				const result = isValidSecretResourceId(id);

				// then
				expect(result).toBe(false);
			},
		);
	});
	describe('isValidProbeResourceId', () => {
		it.for([
			'*',
			'validate-configuration/io.camunda:aws-credential:1',
			'a'.repeat(256),
			// Java's Character.isWhitespace does not count the no-break spaces, so the server accepts them.
			'probe\u00A0id',
		])('should accept %s', (id) => {
			// when
			const result = isValidProbeResourceId(id);

			// then
			expect(result).toBe(true);
		});

		it.for(['', 'a'.repeat(257), 'my probe', 'probe\tid', 'probe\u2003id', 'probe\u2028id', 'probe\u001Fid'])(
			'should reject %j',
			(id) => {
				// when
				const result = isValidProbeResourceId(id);

				// then
				expect(result).toBe(false);
			},
		);
	});
});
