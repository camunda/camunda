/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {
	validateDuplicateNames,
	validateNameCharacters,
	validateNameComplete,
	validateValueComplete,
	validateValueJSON,
} from './validators';

const noop = () => {};
const meta = {
	blur: noop,
	change: noop,
	focus: noop,
};

describe('validators', () => {
	beforeEach(() => {
		vi.useFakeTimers();
	});

	afterEach(() => {
		vi.clearAllTimers();
		vi.useRealTimers();
	});

	describe('validateNameCharacters', () => {
		it.for(['abc', '123'])('should allow a name containing valid characters: %s', (variableName) => {
			expect(validateNameCharacters(variableName, {})).toBeUndefined();
		});

		it.for(['"', ' ', 'test ', '"test"', 'test\twith\ttab', 'line\nbreak', 'carriage\rreturn', 'form\ffeed'])(
			'should reject an invalid name: %s',
			(variableName) => {
				expect(validateNameCharacters(variableName, {})).toBe('Name is invalid');
			},
		);
	});

	describe('validateNameComplete', () => {
		it.for(['abc', 'true', '123'])('should allow a non-empty name: %s', (variableName) => {
			expect(
				validateNameComplete(
					variableName,
					{newVariables: [{name: variableName, value: '1'}]},
					{
						...meta,
						name: 'newVariables[0].name',
					},
				),
			).toBeUndefined();
		});

		it.for(['', ' ', '           '])(
			'should reject an empty name when the variable has a value',
			async (variableName) => {
				const result = validateNameComplete(
					variableName,
					{newVariables: [{name: variableName, value: '1'}]},
					{
						...meta,
						name: 'newVariables[0].name',
					},
				);

				await vi.advanceTimersByTimeAsync(1000);

				await expect(result).resolves.toBe('Name has to be filled');
			},
		);

		it('should allow an empty name when the variable value is empty', () => {
			expect(
				validateNameComplete(
					'',
					{newVariables: [{name: '', value: ''}]},
					{
						...meta,
						name: 'newVariables[0].name',
					},
				),
			).toBeUndefined();
		});

		it('should skip name completeness validation when new variables are absent', () => {
			expect(validateNameComplete('', {}, {...meta, name: 'newVariables[0].name'})).toBeUndefined();
		});
	});

	describe('validateDuplicateNames', () => {
		it('should allow a unique variable name', () => {
			expect(
				validateDuplicateNames(
					'test3',
					{
						'#test1': 'value1',
						newVariables: [
							{name: 'test2', value: 'value2'},
							{name: 'test3', value: 'value3'},
						],
					},
					{...meta, name: 'newVariables[1].name'},
				),
			).toBeUndefined();
		});

		it('should reject a name that duplicates an existing variable', async () => {
			const result = validateDuplicateNames(
				'test1',
				{
					'#test1': 'value1',
					newVariables: [{name: 'test1', value: 'value2'}],
				},
				{...meta, name: 'newVariables[0].name', active: true},
			);

			await vi.advanceTimersByTimeAsync(1000);

			await expect(Promise.resolve(result)).resolves.toBe('Name must be unique');
		});

		it.for([
			{state: 'active', fieldMeta: {active: true}},
			{state: 'showing the duplicate error', fieldMeta: {error: 'Name must be unique'}},
			{state: 'validating', fieldMeta: {validating: true}},
		])('should reject duplicate new variable names while the field is $state', async ({fieldMeta}) => {
			const result = validateDuplicateNames(
				'test2',
				{
					newVariables: [
						{name: 'test2', value: 'value2'},
						{name: 'test2', value: 'value3'},
					],
				},
				{...meta, ...fieldMeta, name: 'newVariables[0].name'},
			);

			await vi.advanceTimersByTimeAsync(1000);

			await expect(Promise.resolve(result)).resolves.toBe('Name must be unique');
		});

		it('should not report duplicate new variable names for an inactive untouched field', () => {
			expect(
				validateDuplicateNames(
					'test2',
					{
						newVariables: [
							{name: 'test2', value: 'value2'},
							{name: 'test2', value: 'value3'},
						],
					},
					{...meta, name: 'newVariables[0].name'},
				),
			).toBeUndefined();
		});
	});

	describe('validateValueComplete', () => {
		it.for(['"abc"', '123', 'true', '{"name":"value"}', '[1,2,3]'])('should allow a valid JSON value: %s', (value) => {
			expect(
				validateValueComplete(
					value,
					{newVariables: [{name: 'test', value}]},
					{
						...meta,
						name: 'newVariables[0].value',
					},
				),
			).toBeUndefined();
		});

		it('should allow an empty value when the variable name is empty', () => {
			expect(
				validateValueComplete(
					'',
					{newVariables: [{name: '', value: ''}]},
					{
						...meta,
						name: 'newVariables[0].value',
					},
				),
			).toBeUndefined();
		});

		it.for(['abc', '"abc', '{name: "value"}', '[[0]', '() => {}'])(
			'should reject a non-JSON value: %s',
			async (value) => {
				const result = validateValueComplete(
					value,
					{newVariables: [{name: 'test', value}]},
					{
						...meta,
						name: 'newVariables[0].value',
					},
				);

				await vi.advanceTimersByTimeAsync(1000);

				await expect(result).resolves.toBe('Value has to be JSON or a literal');
			},
		);
	});

	describe('validateValueJSON', () => {
		it.for(['"abc"', '123', 'true', '{"name":"value"}', '[1,2,3]'])(
			'should allow JSON editor content containing valid JSON: %s',
			(value) => {
				expect(validateValueJSON(value, {})).toBeUndefined();
			},
		);

		it.for(['abc', '"abc', '{name: "value"}', '[[0]', '() => {}'])(
			'should reject JSON editor content containing invalid JSON: %s',
			async (value) => {
				const result = validateValueJSON(value, {});

				await vi.advanceTimersByTimeAsync(1000);

				await expect(result).resolves.toBe('Value has to be JSON or a literal');
			},
		);
	});
});
