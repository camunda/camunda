/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {getInstanceMigration, getInstanceMigrationLocation} from './instanceMigration';

const INSTANCE = createProcessInstance({
	processInstanceKey: '2251799813685249',
	processDefinitionKey: 'source-key',
	processDefinitionId: 'invoice',
	processDefinitionName: 'Invoice',
	processDefinitionVersion: 2,
	processDefinitionVersionTag: 'v2',
	tenantId: 'tenant-a',
	hasIncident: true,
});

const STATE_BRANCHES = [
	{state: {$eq: 'ACTIVE'}, hasIncident: false},
	{state: {$eq: 'SUSPENDED'}},
	{hasIncident: true, state: {$neq: 'SUSPENDED'}},
];

describe('instanceMigration', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should hand over only the instance facts and rebuild its single-instance migration', () => {
		const {state} = getInstanceMigrationLocation(INSTANCE);

		expect(state.operateInstanceMigration).toEqual({
			processInstanceKey: '2251799813685249',
			processDefinitionKey: 'source-key',
			processDefinitionId: 'invoice',
			processDefinitionName: 'Invoice',
			processDefinitionVersion: 2,
			processDefinitionVersionTag: 'v2',
			tenantId: 'tenant-a',
		});
		expect(getInstanceMigration(state.operateInstanceMigration)).toEqual({
			source: {
				processDefinitionKey: 'source-key',
				processDefinitionId: 'invoice',
				name: 'Invoice',
				version: 2,
				versionTag: 'v2',
				tenantId: 'tenant-a',
				resourceName: null,
				hasStartForm: false,
				state: 'ACTIVE',
			},
			scope: {
				filter: {
					$or: STATE_BRANCHES,
					state: {$eq: 'ACTIVE'},
					processDefinitionId: {$eq: 'invoice'},
					processDefinitionVersion: 2,
					processInstanceKey: {$in: ['2251799813685249']},
					processDefinitionKey: {$eq: 'source-key'},
				},
				statisticsFilter: {$or: STATE_BRANCHES, processInstanceKey: {$in: ['2251799813685249']}},
				selectedCount: 1,
				isCountTruncated: false,
			},
		});
	});

	it.for([
		{handoff: 'missing', state: undefined},
		{handoff: 'empty', state: {}},
		{handoff: 'incomplete', state: {processInstanceKey: '2251799813685249'}},
	])('should not migrate a $handoff hand-over', ({state}) => {
		expect(getInstanceMigration(state)).toBeNull();
	});
});
