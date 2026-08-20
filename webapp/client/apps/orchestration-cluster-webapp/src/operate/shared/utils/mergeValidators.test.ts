/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect} from 'vitest';
import {mergeValidators} from './mergeValidators';

describe('mergeValidators', () => {
	it('returns undefined when all validators pass', () => {
		const validator = mergeValidators(
			() => undefined,
			() => undefined,
		);

		expect(validator('value', {}, undefined)).toBeUndefined();
	});

	it('returns the first sync error, short-circuiting later validators', () => {
		const validator = mergeValidators(
			() => 'first error',
			() => 'second error',
		);

		expect(validator('value', {}, undefined)).toBe('first error');
	});

	it('resolves with an async error when no sync error is found', async () => {
		const validator = mergeValidators(
			() => undefined,
			() => Promise.resolve('async error'),
		);

		await expect(validator('value', {}, undefined)).resolves.toBe('async error');
	});
});
