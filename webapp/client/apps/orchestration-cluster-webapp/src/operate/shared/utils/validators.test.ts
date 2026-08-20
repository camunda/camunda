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
	validateTimeComplete,
	validateTimeRange,
	validateTimeCharacters,
} from './validators';

const ERRORS = {
	ids: i18n.t('operate.shared.validators.ids'),
	parentInstanceId: i18n.t('operate.shared.validators.parentInstanceId'),
	batchOperationKey: i18n.t('operate.shared.validators.batchOperationKey'),
	time: i18n.t('operate.shared.validators.time'),
	timeRange: i18n.t('operate.shared.validators.timeRange'),
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

	it('should validate time with delay', async () => {
		vi.runAllTimersAsync();
		const validate = validateTimeComplete('99:99:99', {});
		vi.runOnlyPendingTimers();
		await expect(validate).resolves.toBe(ERRORS.time);
	});

	it('should pass validating time', () => {
		expect(validateTimeComplete('17:30', {})).toBeUndefined();
		expect(validateTimeComplete('12:34:56', {})).toBeUndefined();
	});

	it('should validate invalid characters without delay', () => {
		expect(validateTimeCharacters('a')).toBe(ERRORS.time);
		expect(validateTimeCharacters(' ')).toBe(ERRORS.time);
		expect(validateTimeCharacters('xx:xx:xx')).toBe(ERRORS.time);
		expect(validateTimeCharacters('--')).toBe(ERRORS.time);

		expect(validateTimeCharacters(':')).toBeUndefined();
		expect(validateTimeCharacters('')).toBeUndefined();
	});
});

describe('validateTimeRange', () => {
	const mockMeta = {
		blur: vi.fn(),
		change: vi.fn(),
		focus: vi.fn(),
	};
	const FROM_TIME_META = {...mockMeta, name: 'fromTime'};
	const TO_TIME_META = {...mockMeta, name: 'toTime'};

	beforeEach(() => {
		vi.useFakeTimers({shouldAdvanceTime: true});
	});
	afterEach(() => {
		vi.clearAllTimers();
		vi.useRealTimers();
	});

	it('with one of the date/time fields undefined', () => {
		expect(validateTimeRange('12:00:00', {fromTime: '12:00:00'}, FROM_TIME_META)).toBe(undefined);
		expect(validateTimeRange('12:00:00', {fromTime: '12:00:00', toTime: '10:00:00'}, FROM_TIME_META)).toBe(undefined);
		expect(
			validateTimeRange('12:00:00', {fromTime: '12:00:00', toTime: '10:00:00', fromDate: '2023-03-29'}, FROM_TIME_META),
		).toBe(undefined);
	});

	it('with different days and invalid time range', () => {
		expect(
			validateTimeRange(
				'12:12:12',
				{fromDate: '2023-03-27', toDate: '2023-03-28', fromTime: '12:12:12', toTime: '11:11:11'},
				FROM_TIME_META,
			),
		).toBe(undefined);
		expect(
			validateTimeRange(
				'11:11:11',
				{fromDate: '2023-03-27', toDate: '2023-03-28', fromTime: '12:12:12', toTime: '11:11:11'},
				TO_TIME_META,
			),
		).toBe(undefined);
	});

	it('with same day and invalid time range', async () => {
		await expect(
			validateTimeRange(
				'12:12:12',
				{fromDate: '2023-03-27', toDate: '2023-03-27', fromTime: '12:12:12', toTime: '11:11:11'},
				FROM_TIME_META,
			),
		).resolves.toBe(ERRORS.timeRange);

		await expect(
			validateTimeRange(
				'11:11:11',
				{fromDate: '2023-03-27', toDate: '2023-03-27', fromTime: '12:12:12', toTime: '11:11:11'},
				TO_TIME_META,
			),
		).resolves.toBe(' ');
	});

	it('with same day and valid time range', () => {
		expect(
			validateTimeRange(
				'11:11:11',
				{fromDate: '2023-03-27', toDate: '2023-03-27', fromTime: '11:11:11', toTime: '12:12:12'},
				FROM_TIME_META,
			),
		).toBe(undefined);

		expect(
			validateTimeRange(
				'12:12:12',
				{fromDate: '2023-03-27', toDate: '2023-03-27', fromTime: '11:11:11', toTime: '12:12:12'},
				TO_TIME_META,
			),
		).toBe(undefined);
	});
});
