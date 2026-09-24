/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {processesSearchSchema} from '../../../routes/_carbon/_auth/operate/processes/index';
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

	it('should scope matching process and incident filters to the selected tenant and error hash', () => {
		expect(
			mapProcessInstancesFilter({
				...NO_STATES,
				incidents: true,
				process: 'order-process',
				version: 2,
				tenantId: '<tenant-A>',
				errorMessage: 'Connection timeout',
				incidentErrorHashCode: -481,
			}),
		).toEqual({
			hasIncident: true,
			processDefinitionId: {$eq: 'order-process'},
			processDefinitionVersion: 2,
			tenantId: {$eq: '<tenant-A>'},
			errorMessage: {$in: ['Connection timeout']},
			incidentErrorHashCode: {$eq: -481},
			processInstanceKey: undefined,
			parentProcessInstanceKey: undefined,
			batchOperationKey: undefined,
			hasRetriesLeft: undefined,
			startDate: undefined,
			endDate: undefined,
			businessId: undefined,
		});
	});

	it('should preserve message-only bookmarks and the zero error hash', () => {
		expect(
			mapProcessInstancesFilter({...NO_STATES, incidents: true, errorMessage: 'Failure'})?.incidentErrorHashCode,
		).toBeUndefined();
		expect(
			mapProcessInstancesFilter({...NO_STATES, incidents: true, errorMessage: 'Failure', incidentErrorHashCode: 0})
				?.incidentErrorHashCode,
		).toEqual({$eq: 0});
		expect(
			mapProcessInstancesFilter({...NO_STATES, incidents: true, incidentErrorHashCode: -481})?.incidentErrorHashCode,
		).toEqual({$eq: -481});
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

	describe('Processes route search', () => {
		it.for([
			{processDefinitionId: 'orders', processDefinitionVersion: '2', expectedVersion: 2},
			{processDefinitionId: 'orders', processDefinitionVersion: 2, expectedVersion: 2},
			{processDefinitionId: 'orders', processDefinitionVersion: 'all', expectedVersion: undefined},
		] as const)(
			'should normalize saved legacy process and version URLs',
			({processDefinitionId, processDefinitionVersion, expectedVersion}) => {
				expect(
					processesSearchSchema.parse({processDefinitionId, processDefinitionVersion, tenantId: '<tenant-A>'}),
				).toMatchObject({
					process: 'orders',
					version: expectedVersion,
					tenantId: '<tenant-A>',
				});
			},
		);

		it('should prefer active process and version keys over saved aliases', () => {
			expect(
				processesSearchSchema.parse({
					process: 'current',
					version: 3,
					processDefinitionId: 'old',
					processDefinitionVersion: '2',
				}),
			).toMatchObject({process: 'current', version: 3});
		});

		it('should retain a valid process when a legacy version is invalid', () => {
			expect(
				processesSearchSchema.parse({processDefinitionId: 'orders', processDefinitionVersion: 'unknown'}),
			).toMatchObject({
				process: 'orders',
				version: undefined,
			});
		});

		it.for([
			{hash: '-481', expected: -481},
			{hash: '0', expected: 0},
			{hash: 0, expected: 0},
			{hash: '', expected: undefined},
			{hash: null, expected: undefined},
			{hash: 'invalid', expected: undefined},
		] as const)('should validate incident hashes without losing the display message', ({hash, expected}) => {
			const search = processesSearchSchema.parse({errorMessage: 'Connection timeout', incidentErrorHashCode: hash});
			expect(search.errorMessage).toBe('Connection timeout');
			expect(search.incidentErrorHashCode).toBe(expected);
		});
	});

	it.for([undefined, 'unknown+asc', 'startDate+unknown'])('should fall back to the default sort for %s', (sort) => {
		expect(mapProcessInstancesSort(sort)).toEqual([{field: 'startDate', order: 'desc'}]);
	});
});
