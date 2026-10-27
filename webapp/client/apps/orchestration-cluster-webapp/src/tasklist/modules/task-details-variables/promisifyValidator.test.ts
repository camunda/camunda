/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {promisifyValidator} from './promisifyValidator';

const noop = () => {};

describe('promisifyValidator', () => {
	beforeEach(() => {
		vi.useFakeTimers();
	});

	afterEach(() => {
		vi.clearAllTimers();
		vi.useRealTimers();
	});

	it('should return undefined synchronously when validation passes', () => {
		const validator = promisifyValidator(() => undefined, 1000);

		expect(validator('value', {}, undefined)).toBeUndefined();
		expect(vi.getTimerCount()).toBe(0);
	});

	it('should resolve an error after the debounce timeout', async () => {
		const validator = promisifyValidator(() => 'error', 1000);
		const result = validator('value', {}, undefined);
		const onResolve = vi.fn();
		void Promise.resolve(result).then(onResolve);

		await vi.advanceTimersByTimeAsync(999);
		expect(onResolve).not.toHaveBeenCalled();

		await vi.advanceTimersByTimeAsync(1);
		await expect(result).resolves.toBe('error');
	});

	it('should return an existing error synchronously when the field is inactive', () => {
		const validator = promisifyValidator(() => 'error', 1000);

		expect(
			validator(
				'value',
				{},
				{
					active: false,
					blur: noop,
					change: noop,
					error: 'error',
					focus: noop,
					name: 'field',
				},
			),
		).toBe('error');
		expect(vi.getTimerCount()).toBe(0);
	});

	it('should debounce an error while the field is active', async () => {
		const validator = promisifyValidator(() => 'error', 1000);
		const result = validator(
			'value',
			{},
			{
				active: true,
				blur: noop,
				change: noop,
				error: 'error',
				focus: noop,
				name: 'field',
			},
		);

		await vi.advanceTimersByTimeAsync(1000);

		await expect(result).resolves.toBe('error');
	});

	it('should pass all validation parameters to the wrapped validator', () => {
		const wrappedValidator = vi.fn(() => undefined);
		const validator = promisifyValidator(wrappedValidator, 1000);
		const values = {field: 'value'};
		const meta = {
			blur: noop,
			change: noop,
			focus: noop,
			name: 'field',
		};

		validator('value', values, meta);

		expect(wrappedValidator).toHaveBeenCalledWith('value', values, meta);
	});
});
