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
