/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {describe, expect, vi} from 'vitest';
import {mergeValidators} from './mergeValidators';

describe('mergeValidators', () => {
	it('should return undefined when all validators pass', () => {
		const validator = mergeValidators(
			() => undefined,
			() => undefined,
		);

		expect(validator('value', {}, undefined)).toBeUndefined();
	});

	it('should return the first synchronous error', () => {
		const validator = mergeValidators(
			() => 'first error',
			() => 'second error',
		);

		expect(validator('value', {}, undefined)).toBe('first error');
	});

	it('should resolve an asynchronous error when synchronous validators pass', async () => {
		const validator = mergeValidators(
			() => undefined,
			() => Promise.resolve('async error'),
		);

		await expect(validator('value', {}, undefined)).resolves.toBe('async error');
	});

	it('should ignore asynchronous results when a synchronous error exists', () => {
		const asyncValidator = vi.fn(() => new Promise<string | undefined>(() => undefined));
		const validator = mergeValidators(() => 'sync error', asyncValidator);

		expect(validator('value', {}, undefined)).toBe('sync error');
		expect(asyncValidator).toHaveBeenCalledOnce();
	});
});
