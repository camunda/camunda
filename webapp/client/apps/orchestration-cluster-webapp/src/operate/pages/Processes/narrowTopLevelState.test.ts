/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {narrowTopLevelState} from './narrowTopLevelState';

describe('narrowTopLevelState', () => {
	it.for([
		{
			description: 'finished states are dropped leaving one allowed state',
			filter: {state: {$in: ['ACTIVE' as const, 'COMPLETED' as const, 'TERMINATED' as const]}, hasIncident: false},
			allowedStates: ['ACTIVE'],
			expected: {state: {$eq: 'ACTIVE'}, hasIncident: false},
		},
		{
			description: 'one of two states is allowed',
			filter: {state: {$in: ['ACTIVE' as const, 'COMPLETED' as const]}},
			allowedStates: ['ACTIVE', 'SUSPENDED'],
			expected: {state: {$eq: 'ACTIVE'}},
		},
		{
			description: 'several allowed states remain',
			filter: {state: {$in: ['ACTIVE' as const, 'SUSPENDED' as const, 'COMPLETED' as const]}},
			allowedStates: ['ACTIVE', 'SUSPENDED'],
			expected: {state: {$in: ['ACTIVE', 'SUSPENDED']}},
		},
		{
			description: 'the original order of the remaining states is kept',
			filter: {state: {$in: ['SUSPENDED' as const, 'COMPLETED' as const, 'ACTIVE' as const]}},
			allowedStates: ['ACTIVE', 'SUSPENDED'],
			expected: {state: {$in: ['SUSPENDED', 'ACTIVE']}},
		},
	])('should narrow a state $in when $description', ({filter, allowedStates, expected}) => {
		expect(narrowTopLevelState(filter, allowedStates)).toEqual(expected);
	});

	it.for([
		{
			description: 'there is no state',
			filter: {hasIncident: false},
			allowedStates: ['ACTIVE'],
		},
		{
			description: 'no $in state is allowed',
			filter: {state: {$in: ['COMPLETED' as const, 'TERMINATED' as const]}},
			allowedStates: ['ACTIVE'],
		},
		{
			description: 'every $in state is already allowed',
			filter: {state: {$in: ['ACTIVE' as const, 'SUSPENDED' as const]}},
			allowedStates: ['ACTIVE', 'SUSPENDED'],
		},
		{
			description: 'the state is an $eq',
			filter: {state: {$eq: 'COMPLETED' as const}},
			allowedStates: ['ACTIVE'],
		},
		{
			description: 'the state is a $neq',
			filter: {state: {$neq: 'COMPLETED' as const}},
			allowedStates: ['ACTIVE'],
		},
		{
			description: 'the state is an $exists',
			filter: {state: {$exists: true}},
			allowedStates: ['ACTIVE'],
		},
		{
			description: 'the state has other operators next to $in',
			filter: {state: {$in: ['ACTIVE' as const, 'COMPLETED' as const], $neq: 'SUSPENDED' as const}},
			allowedStates: ['ACTIVE'],
		},
		{
			description: 'the state is only inside $or',
			filter: {$or: [{state: {$in: ['ACTIVE' as const, 'COMPLETED' as const]}}]},
			allowedStates: ['ACTIVE'],
		},
	])('should leave the filter unchanged when $description', ({filter, allowedStates}) => {
		const original = structuredClone(filter);

		expect(narrowTopLevelState(filter, allowedStates)).toEqual(original);
	});

	it('should keep the other fields and $or branches untouched', () => {
		const or = [{state: {$in: ['ACTIVE' as const, 'COMPLETED' as const]}}, {hasIncident: true}];

		expect(
			narrowTopLevelState(
				{
					state: {$in: ['ACTIVE' as const, 'COMPLETED' as const]},
					processDefinitionId: {$eq: 'order'},
					$or: or,
				},
				['ACTIVE'],
			),
		).toEqual({
			state: {$eq: 'ACTIVE'},
			processDefinitionId: {$eq: 'order'},
			$or: [{state: {$in: ['ACTIVE', 'COMPLETED']}}, {hasIncident: true}],
		});
	});

	it('should not mutate the input filter', () => {
		const filter = {
			state: {$in: ['ACTIVE' as const, 'COMPLETED' as const, 'TERMINATED' as const]},
			hasIncident: false,
		};
		const original = structuredClone(filter);

		const result = narrowTopLevelState(filter, ['ACTIVE']);

		expect(result).not.toBe(filter);
		expect(filter).toEqual(original);
	});
});
