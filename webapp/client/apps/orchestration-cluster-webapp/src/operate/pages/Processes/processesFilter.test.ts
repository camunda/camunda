/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {mapProcessInstancesFilter, mapProcessInstancesSort, type ProcessesSearch} from './processesFilter';

const NO_STATES: ProcessesSearch = {
	active: false,
	incidents: false,
	completed: false,
	canceled: false,
	suspended: false,
};

describe('mapProcessInstancesFilter', () => {
	it('should skip the query for empty batch operation and element filters without a selected state', () => {
		expect(mapProcessInstancesFilter({...NO_STATES, batchOperationKey: '', elementId: ''})).toBeUndefined();
	});

	it('should ignore an empty element filter', () => {
		expect(mapProcessInstancesFilter({...NO_STATES, active: true, elementId: ''})?.elementId).toBeUndefined();
	});

	it('should ignore an empty tenant filter', () => {
		expect(mapProcessInstancesFilter({...NO_STATES, active: true, tenantId: ''})?.tenantId).toBeUndefined();
	});

	it('should query for suspended instances alone', () => {
		expect(mapProcessInstancesFilter({...NO_STATES, suspended: true})).toEqual({state: {$eq: 'SUSPENDED'}});
	});

	it('should combine suspended with a selected state as separate branches', () => {
		expect(mapProcessInstancesFilter({...NO_STATES, active: true, suspended: true})).toEqual({
			$or: [{state: {$eq: 'ACTIVE'}, hasIncident: false}, {state: {$eq: 'SUSPENDED'}}],
		});
	});

	it('should exclude suspended instances from the incidents branch to avoid asserting two states at once', () => {
		expect(mapProcessInstancesFilter({...NO_STATES, incidents: true, suspended: true})).toEqual({
			$or: [{state: {$eq: 'SUSPENDED'}}, {hasIncident: true, state: {$neq: 'SUSPENDED'}}],
		});
	});

	it('should keep a suspended instance in the active-element bucket when mixed with a finished state and an element filter', () => {
		const filter = mapProcessInstancesFilter({...NO_STATES, completed: true, suspended: true, elementId: 'task-a'});
		expect(filter?.$or).toContainEqual({
			elementId: {$eq: 'task-a'},
			elementInstanceState: {$eq: 'ACTIVE'},
			state: {$eq: 'SUSPENDED'},
		});
	});
});

describe('mapProcessInstancesSort', () => {
	it.for([
		['processDefinitionName+asc', 'processDefinitionName', 'asc'],
		['processInstanceKey+desc', 'processInstanceKey', 'desc'],
		['processDefinitionVersion+asc', 'processDefinitionVersion', 'asc'],
		['businessId+desc', 'businessId', 'desc'],
		['tenantId+asc', 'tenantId', 'asc'],
		['startDate+desc', 'startDate', 'desc'],
		['endDate+asc', 'endDate', 'asc'],
		['parentProcessInstanceKey+desc', 'parentProcessInstanceKey', 'desc'],
	] as const)('should map the supported sort value %s', ([sort, field, order]) => {
		expect(mapProcessInstancesSort(sort)).toEqual([{field, order}]);
	});

	it.for([undefined, 'unknown+asc', 'startDate+unknown'])('should fall back to the default sort for %s', (sort) => {
		expect(mapProcessInstancesSort(sort)).toEqual([{field: 'startDate', order: 'desc'}]);
	});
});
