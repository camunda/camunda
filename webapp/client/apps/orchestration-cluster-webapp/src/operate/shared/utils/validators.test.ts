/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, vi, beforeEach, afterEach} from 'vitest';
import i18n from 'i18next';
import {
	validateIdsCharacters,
	validateIdsLength,
	validatesIdsComplete,
	validateParentInstanceIdCharacters,
	validateParentInstanceIdComplete,
	validateParentInstanceIdNotTooLong,
	validateBatchOperationKeyCharacters,
	validateBatchOperationKeyComplete,
} from './validators';

const ERRORS = {
	ids: i18n.t('operate.shared.validators.ids'),
	parentInstanceId: i18n.t('operate.shared.validators.parentInstanceId'),
	batchOperationKey: i18n.t('operate.shared.validators.batchOperationKey'),
};

describe('validators', () => {
	beforeEach(() => {
		vi.useFakeTimers({shouldAdvanceTime: true});
	});
	afterEach(() => {
		vi.clearAllTimers();
		vi.useRealTimers();
	});

	it('should validate ids without delay', () => {
		const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
		expect(validateIdsCharacters('', {})).toBeUndefined();

		expect(validateIdsCharacters('2251799813685543', {})).toBeUndefined();
		expect(validateIdsCharacters('22517998136855430', {})).toBeUndefined();

		expect(validateIdsCharacters('2251799813685543a', {})).toBe(ERRORS.ids);
		expect(validateIdsCharacters('a', {})).toBe(ERRORS.ids);
		expect(validateIdsCharacters('-', {})).toBe(ERRORS.ids);

		expect(validateIdsCharacters('2251799813685543 2251799813685543', {})).toBeUndefined();
		expect(validateIdsCharacters('2251799813685543,2251799813685543', {})).toBeUndefined();
		expect(validateIdsCharacters('2251799813685543, 2251799813685543', {})).toBeUndefined();

		expect(validateIdsCharacters('2251799813685543 a 2251799813685543 ', {})).toBe(ERRORS.ids);

		expect(validateIdsLength('2251799813685543 2251799813685543 11111111111111111111', {})).toBe(ERRORS.ids);

		expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
	});

	it('should validate ids with delay', async () => {
		const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
		vi.runAllTimersAsync();

		await expect(validatesIdsComplete('1', {})).resolves.toBe(ERRORS.ids);
		await expect(validatesIdsComplete('1 1 1 ', {})).resolves.toBe(ERRORS.ids);
		await expect(validatesIdsComplete('225179981368554', {})).resolves.toBe(ERRORS.ids);
		expect(validatesIdsComplete('2251799813685543', {})).toBeUndefined();

		expect(setTimeoutSpy).toHaveBeenCalledTimes(3);
	});

	it('should validate parent instance id without delay', () => {
		const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
		expect(validateParentInstanceIdCharacters('', {})).toBeUndefined();
		expect(validateParentInstanceIdCharacters('2251799813685543', {})).toBeUndefined();

		expect(validateParentInstanceIdCharacters('2251799813685543a', {})).toBe(ERRORS.parentInstanceId);
		expect(validateParentInstanceIdCharacters('a', {})).toBe(ERRORS.parentInstanceId);
		expect(validateParentInstanceIdCharacters('-', {})).toBe(ERRORS.parentInstanceId);
		expect(validateParentInstanceIdCharacters('2251799813685543 2251799813685543', {})).toBe(ERRORS.parentInstanceId);

		expect(validateParentInstanceIdNotTooLong('11111111111111111111', {})).toBe(ERRORS.parentInstanceId);

		expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
	});

	it('should validate parent instance id with delay', async () => {
		const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
		vi.runAllTimersAsync();

		await expect(validateParentInstanceIdComplete('1', {})).resolves.toBe(ERRORS.parentInstanceId);
		await expect(validateParentInstanceIdComplete('225179981368554', {})).resolves.toBe(ERRORS.parentInstanceId);
		expect(validateParentInstanceIdComplete('2251799813685543', {})).toBeUndefined();

		expect(setTimeoutSpy).toHaveBeenCalledTimes(2);
	});

	it('should validate batchOperationKey without delay', () => {
		const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
		expect(validateBatchOperationKeyCharacters('', {})).toBeUndefined();
		expect(validateBatchOperationKeyCharacters('f', {})).toBeUndefined();
		expect(validateBatchOperationKeyCharacters('1', {})).toBeUndefined();
		expect(validateBatchOperationKeyCharacters('1f4d40c3-7cce-4e51-8abe-0cda8d42f04f', {})).toBeUndefined();
		expect(validateBatchOperationKeyCharacters('2251799813685871', {})).toBeUndefined();

		expect(validateBatchOperationKeyCharacters('&', {})).toBe(ERRORS.batchOperationKey);
		expect(validateBatchOperationKeyCharacters('g', {})).toBe(ERRORS.batchOperationKey);
		expect(validateBatchOperationKeyCharacters('a&', {})).toBe(ERRORS.batchOperationKey);
		expect(validateBatchOperationKeyCharacters('1f4d40c3-7cce-4e51-8abe-0cda8d42f04f$', {})).toBe(
			ERRORS.batchOperationKey,
		);

		expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
	});

	it('should validate batchOperationKey with delay', async () => {
		const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
		vi.runAllTimersAsync();

		await expect(validateBatchOperationKeyComplete('1f4d40c3-7cce-4e51-', {})).resolves.toBe(ERRORS.batchOperationKey);
		await expect(validateBatchOperationKeyComplete('0e8481e6-b652-41c9-a72a-f531c783122', {})).resolves.toBe(
			ERRORS.batchOperationKey,
		);
		await expect(validateBatchOperationKeyComplete('a', {})).resolves.toBe(ERRORS.batchOperationKey);
		await expect(validateBatchOperationKeyComplete('0', {})).resolves.toBe(ERRORS.batchOperationKey);
		await expect(validateBatchOperationKeyComplete('12345689', {})).resolves.toBe(ERRORS.batchOperationKey);
		expect(validateBatchOperationKeyComplete('1f4d40c3-7cce-4e51-8abe-0cda8d42f04f', {})).toBeUndefined();

		expect(setTimeoutSpy).toHaveBeenCalledTimes(5);
	});
});
