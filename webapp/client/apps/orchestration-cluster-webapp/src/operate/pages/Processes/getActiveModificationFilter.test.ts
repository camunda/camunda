/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getActiveModificationFilter} from './getActiveModificationFilter';

const selected = {processInstanceKey: {$in: ['1', '2'], $notIn: ['3']}, processDefinitionId: {$eq: 'order'}};

describe('getActiveModificationFilter', () => {
	it.for([
		{
			description: 'active only',
			filter: {...selected, state: {$eq: 'ACTIVE' as const}, hasIncident: false},
			expected: {...selected, state: {$eq: 'ACTIVE'}, hasIncident: false},
		},
		{
			description: 'active and completed',
			filter: {...selected, state: {$in: ['ACTIVE' as const, 'COMPLETED' as const]}, hasIncident: false},
			expected: {...selected, state: {$eq: 'ACTIVE'}, hasIncident: false},
		},
		{
			description: 'incidents only',
			filter: {...selected, hasIncident: true},
			expected: {...selected, hasIncident: true, state: {$eq: 'ACTIVE'}},
		},
		{
			description: 'active and suspended',
			filter: {
				...selected,
				$or: [{state: {$eq: 'ACTIVE' as const}, hasIncident: false}, {state: {$eq: 'SUSPENDED' as const}}],
			},
			expected: {
				...selected,
				$or: [{state: {$eq: 'ACTIVE'}, hasIncident: false}, {state: {$eq: 'SUSPENDED'}}],
				state: {$eq: 'ACTIVE'},
			},
		},
		{
			description: 'an element filter',
			filter: {
				...selected,
				$or: [
					{elementId: {$eq: 'task'}, elementInstanceState: {$eq: 'ACTIVE' as const}, state: {$eq: 'ACTIVE' as const}},
					{elementId: {$eq: 'task'}, state: {$eq: 'COMPLETED' as const}},
				],
			},
			expected: {
				...selected,
				$or: [
					{elementId: {$eq: 'task'}, elementInstanceState: {$eq: 'ACTIVE'}, state: {$eq: 'ACTIVE'}},
					{elementId: {$eq: 'task'}, state: {$eq: 'COMPLETED'}},
				],
				state: {$eq: 'ACTIVE'},
			},
		},
	])('should narrow a selection with $description to its active instances', ({filter, expected}) => {
		expect(getActiveModificationFilter(filter)).toEqual(expected);
	});

	it.for([
		{description: 'suspended only', filter: {...selected, state: {$eq: 'SUSPENDED' as const}}},
		{description: 'completed only', filter: {...selected, state: {$eq: 'COMPLETED' as const}, hasIncident: false}},
		{
			description: 'completed and canceled',
			filter: {...selected, state: {$in: ['COMPLETED' as const, 'TERMINATED' as const]}, hasIncident: false},
		},
		{
			description: 'an element filter on completed and suspended instances',
			filter: {
				...selected,
				$or: [
					{
						elementId: {$eq: 'task'},
						elementInstanceState: {$eq: 'ACTIVE' as const},
						state: {$eq: 'SUSPENDED' as const},
					},
					{elementId: {$eq: 'task'}, state: {$eq: 'COMPLETED' as const}, hasIncident: false},
				],
			},
		},
	])('should match nothing for a selection with $description', ({filter}) => {
		expect(getActiveModificationFilter(filter)).toBeNull();
	});
});
