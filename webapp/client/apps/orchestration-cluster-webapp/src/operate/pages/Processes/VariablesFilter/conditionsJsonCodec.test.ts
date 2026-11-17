/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {parseConditionsJson, serializeConditions} from './conditionsJsonCodec';
import {toVariableEntry, type VariableCondition} from './variableConditions';

describe('conditionsJsonCodec', () => {
	it('should serialize every operator to the API variable filter shape and skip empty rows', () => {
		expect(
			JSON.parse(
				serializeConditions([
					{name: 'status', operator: 'equals', value: '"open"'},
					{name: 'retries', operator: 'notEqual', value: '0'},
					{name: 'note', operator: 'contains', value: 'foo'},
					{name: 'region', operator: 'oneOf', value: 'eu, us'},
					{name: 'flag', operator: 'exists', value: ''},
					{name: 'missing', operator: 'doesNotExist', value: ''},
					{name: '', operator: 'equals', value: ''},
				]),
			),
		).toEqual([
			{name: 'status', value: '"open"'},
			{name: 'retries', value: {$neq: '0'}},
			{name: 'note', value: {$like: '*foo*'}},
			{name: 'region', value: {$in: ['eu', 'us']}},
			{name: 'flag', value: {$exists: true}},
			{name: 'missing', value: {$exists: false}},
		]);
	});

	it('should round-trip conditions between Fields and JSON', () => {
		const conditions: VariableCondition[] = [
			{name: 'status', operator: 'equals', value: '"open"'},
			{name: 'retries', operator: 'notEqual', value: '0'},
			{name: 'note', operator: 'contains', value: 'foo'},
			{name: 'region', operator: 'oneOf', value: '["eu","us"]'},
			{name: 'flag', operator: 'exists', value: ''},
			{name: 'missing', operator: 'doesNotExist', value: ''},
		];

		expect(parseConditionsJson(serializeConditions(conditions))).toEqual({ok: true, conditions});
	});

	it('should keep numeric one-of values numeric through a Fields to JSON round trip', () => {
		const condition: VariableCondition = {name: 'count', operator: 'oneOf', value: '1, 2'};
		const result = parseConditionsJson(serializeConditions([condition]));

		expect(result.ok && result.conditions.map(toVariableEntry)).toEqual([toVariableEntry(condition)]);
		expect(toVariableEntry(condition)).toEqual({name: 'count', value: {$in: ['1', '2']}});
	});

	it('should read the $eq operator and an empty document', () => {
		expect(parseConditionsJson('[{"name":"status","value":{"$eq":"open"}}]')).toEqual({
			ok: true,
			conditions: [{name: 'status', operator: 'equals', value: 'open'}],
		});
		expect(parseConditionsJson('  ')).toEqual({ok: true, conditions: []});
	});

	it.for([
		{scenario: 'invalid JSON syntax', text: '[{', error: 'Invalid JSON syntax'},
		{
			scenario: 'an empty name',
			text: '[{"name":"","value":"x"}]',
			error: 'Condition #1: Variable name is required (at name)',
		},
		{
			scenario: 'an unsupported $notIn operator',
			text: '[{"name":"a","value":"x"},{"name":"b","value":{"$notIn":["x"]}}]',
			error: "Condition #2: '$notIn' is not yet supported in the UI",
		},
		{
			scenario: 'a non-array document',
			text: '{"name":"a","value":"x"}',
			error: 'Invalid input: expected array, received object',
		},
		{
			scenario: 'an unknown value operator',
			text: '[{"name":"a","value":{"$gt":1}}]',
			error: 'Condition #1: Invalid input (at value)',
		},
	])('should report $scenario', ({text, error}) => {
		expect(parseConditionsJson(text)).toEqual({ok: false, error});
	});
});
