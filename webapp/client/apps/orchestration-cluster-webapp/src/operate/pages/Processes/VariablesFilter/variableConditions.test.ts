/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {smartTransformValue, toVariableEntry, validateCondition} from './variableConditions';

describe('variableConditions', () => {
	it.for([
		{operator: 'equals', raw: '42', expected: 42},
		{operator: 'equals', raw: 'true', expected: true},
		{operator: 'equals', raw: '"open"', expected: 'open'},
		{operator: 'equals', raw: 'hello', expected: 'hello'},
		{operator: 'equals', raw: 'Doe, John', expected: 'Doe, John'},
		{operator: 'oneOf', raw: ' 1, two ,3, ', expected: [1, 'two', 3]},
		{operator: 'oneOf', raw: ',,', expected: ''},
		{operator: 'oneOf', raw: '["a","b"]', expected: ['a', 'b']},
	] as const)('should interpret $operator $raw as typed JSON where possible', ({operator, raw, expected}) => {
		expect(smartTransformValue(raw, operator)).toEqual(expected);
	});

	it.for(['"open', '{"a":1', '[1, 2'])('should reject unparseable structural input %s', (raw) => {
		expect(() => smartTransformValue(raw, 'equals')).toThrow(`Invalid value: ${raw}`);
	});

	it.for([
		{operator: 'equals', value: 'open', expected: {$eq: '"open"'}},
		{operator: 'equals', value: '42', expected: {$eq: '42'}},
		{operator: 'equals', value: 'Doe, John', expected: {$eq: '"Doe, John"'}},
		{operator: 'notEqual', value: 'Doe, John', expected: {$neq: '"Doe, John"'}},
		{operator: 'notEqual', value: '"open"', expected: {$neq: '"open"'}},
		{operator: 'contains', value: '{"partial', expected: {$like: '*{"partial*'}},
		{operator: 'oneOf', value: '42, true, hello', expected: {$in: ['42', 'true', '"hello"']}},
		{operator: 'oneOf', value: '42', expected: {$in: ['42']}},
		{operator: 'exists', value: '', expected: {$exists: true}},
		{operator: 'doesNotExist', value: '', expected: {$exists: false}},
	] as const)('should map $operator $value to the search request entry', ({operator, value, expected}) => {
		expect(toVariableEntry({name: 'status', operator, value})).toEqual({name: 'status', value: expected});
	});

	it.for([
		{
			scenario: 'a blank name',
			condition: {name: ' ', operator: 'exists', value: ''},
			expected: {name: 'Variable name is required'},
		},
		{
			scenario: 'a blank value',
			condition: {name: 'x', operator: 'equals', value: ' '},
			expected: {value: 'Value is required'},
		},
		{
			scenario: 'an empty contains value',
			condition: {name: 'x', operator: 'contains', value: ''},
			expected: {value: 'Value is required'},
		},
		{
			scenario: 'an unparseable one-of value',
			condition: {name: 'x', operator: 'oneOf', value: '{broken'},
			expected: {value: 'Invalid value: {broken'},
		},
		{scenario: 'raw contains text', condition: {name: 'x', operator: 'contains', value: '{broken'}, expected: {}},
		{scenario: 'a valueless operator', condition: {name: 'x', operator: 'doesNotExist', value: ''}, expected: {}},
		{
			scenario: 'an empty row',
			condition: {name: '', operator: 'equals', value: ''},
			expected: {name: 'Variable name is required', value: 'Value is required'},
		},
	] as const)('should validate $scenario', ({condition, expected}) => {
		expect(validateCondition(condition)).toEqual(expected);
	});
});
