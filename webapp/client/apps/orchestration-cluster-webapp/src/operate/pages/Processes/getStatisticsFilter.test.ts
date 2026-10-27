/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getStatisticsFilter} from './getStatisticsFilter';

const NONE = {active: false, incidents: false, completed: false, canceled: false, suspended: false};

describe('getStatisticsFilter', () => {
	it('should return undefined when nothing narrows the instances', () => {
		expect(getStatisticsFilter(NONE)).toBeUndefined();
	});

	it.for([
		{
			scenario: 'active instances without an incident',
			states: {active: true},
			expected: {state: {$eq: 'ACTIVE'}, hasIncident: false},
		},
		{
			scenario: 'incidents in any state',
			states: {incidents: true},
			expected: {hasIncident: true},
		},
		{
			scenario: 'active instances or incidents',
			states: {active: true, incidents: true},
			expected: {$or: [{state: {$eq: 'ACTIVE'}, hasIncident: false}, {hasIncident: true}]},
		},
		{
			scenario: 'every selected state',
			states: {active: true, completed: true, canceled: true},
			expected: {state: {$in: ['ACTIVE', 'COMPLETED', 'TERMINATED']}, hasIncident: false},
		},
		{
			scenario: 'completed instances or incidents',
			states: {completed: true, incidents: true},
			expected: {$or: [{state: {$eq: 'COMPLETED'}, hasIncident: false}, {hasIncident: true}]},
		},
		{
			scenario: 'suspended instances',
			states: {suspended: true},
			expected: {state: {$eq: 'SUSPENDED'}},
		},
		{
			scenario: 'active or suspended instances',
			states: {active: true, suspended: true},
			expected: {$or: [{state: {$eq: 'ACTIVE'}, hasIncident: false}, {state: {$eq: 'SUSPENDED'}}]},
		},
		{
			scenario: 'suspended instances or incidents on non-suspended instances',
			states: {incidents: true, suspended: true},
			expected: {$or: [{state: {$eq: 'SUSPENDED'}}, {hasIncident: true, state: {$neq: 'SUSPENDED'}}]},
		},
	] as const)('should filter to $scenario', ({states, expected}) => {
		expect(getStatisticsFilter({...NONE, ...states})).toEqual(expected);
	});

	it('should scope the statistics to the selected element', () => {
		expect(getStatisticsFilter({...NONE, active: true, elementId: 'task-1'})).toEqual({
			state: {$eq: 'ACTIVE'},
			hasIncident: false,
			elementId: {$eq: 'task-1'},
			elementInstanceState: {$eq: 'ACTIVE'},
		});
	});

	it('should filter by batch operation when no state is selected', () => {
		expect(getStatisticsFilter({...NONE, batchOperationKey: 'batch-1'})).toEqual({
			batchOperationKey: {$eq: 'batch-1'},
		});
	});

	it('should keep the optional instance criteria', () => {
		expect(
			getStatisticsFilter({
				...NONE,
				active: true,
				tenantId: 'tenant-a',
				processInstanceKey: '1, 2',
				parentProcessInstanceKey: '3',
				errorMessage: 'boom',
				incidentErrorHashCode: 42,
				hasRetriesLeft: true,
				businessId: 'eq_order-1',
				variable: [{name: 'orderId', operator: 'equals', value: '123'}],
			}),
		).toEqual({
			state: {$eq: 'ACTIVE'},
			hasIncident: false,
			tenantId: {$eq: 'tenant-a'},
			processInstanceKey: {$in: ['1', '2']},
			parentProcessInstanceKey: {$eq: '3'},
			errorMessage: {$in: ['boom']},
			incidentErrorHashCode: {$eq: 42},
			hasRetriesLeft: true,
			businessId: {$eq: 'order-1'},
			variables: [{name: 'orderId', value: {$eq: '123'}}],
		});
	});

	it('should drop the process definition fields the endpoint scopes by path', () => {
		expect(getStatisticsFilter({...NONE, active: true, process: 'order-process', version: 2})).toEqual({
			state: {$eq: 'ACTIVE'},
			hasIncident: false,
		});
	});
});
