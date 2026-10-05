/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getMigrationFilter} from './getMigrationFilter';
import type {ProcessesSearch} from './processesFilter';

const RUNNING_SEARCH: ProcessesSearch = {
	process: 'invoice',
	version: 1,
	tenantId: 'tenant-a',
	active: true,
	incidents: true,
	completed: false,
	canceled: false,
	suspended: true,
};

describe('getMigrationFilter', () => {
	it('should only migrate the active instances among the selected states', () => {
		expect(
			getMigrationFilter({search: RUNNING_SEARCH, includeIds: [], excludeIds: [], processDefinitionKey: 'source-key'}),
		).toEqual({
			$or: [
				{state: {$eq: 'ACTIVE'}, hasIncident: false},
				{state: {$eq: 'SUSPENDED'}},
				{hasIncident: true, state: {$neq: 'SUSPENDED'}},
			],
			state: {$eq: 'ACTIVE'},
			processDefinitionId: {$eq: 'invoice'},
			processDefinitionVersion: 1,
			tenantId: {$eq: 'tenant-a'},
			processDefinitionKey: {$eq: 'source-key'},
		});
	});

	it.for([
		{states: 'only suspended', search: {...RUNNING_SEARCH, active: false, incidents: false}},
		{
			states: 'only finished',
			search: {...RUNNING_SEARCH, active: false, incidents: false, suspended: false, completed: true, canceled: true},
		},
	])('should not produce a migration scope for $states instances', ({search}) => {
		expect(getMigrationFilter({search, includeIds: [], excludeIds: [], processDefinitionKey: 'source-key'})).toBeNull();
	});

	it.for([
		{
			selection: 'included',
			includeIds: ['1', '2'],
			excludeIds: [],
			processInstanceKey: {$in: ['1', '2']},
		},
		{
			selection: 'excluded',
			includeIds: [],
			excludeIds: ['3'],
			processInstanceKey: {$in: ['1', '2', '3'], $notIn: ['3']},
		},
	])(
		'should keep the instance key filter when instances are $selection',
		({includeIds, excludeIds, processInstanceKey}) => {
			expect(
				getMigrationFilter({
					search: {...RUNNING_SEARCH, suspended: false, processInstanceKey: '1,2,3'},
					includeIds,
					excludeIds,
					processDefinitionKey: 'source-key',
				})?.processInstanceKey,
			).toEqual(processInstanceKey);
		},
	);
});
