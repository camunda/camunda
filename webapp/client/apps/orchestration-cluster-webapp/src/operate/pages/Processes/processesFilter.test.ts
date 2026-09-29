/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	parseSearchWith,
	stringifySearchWith,
} from '@tanstack/react-router';
import {parseSearchValueSafe} from '#/shared/parseSearchValueSafe';
import {
	mapProcessInstancesFilter,
	mapProcessInstancesSort,
	processesSearchSchema,
	stripLegacyProcessFilters,
	type ProcessesSearch,
} from './processesFilter';

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
		const createProcessesSearchRouter = async (initialEntry: string) => {
			const rootRoute = createRootRoute();
			const processesRoute = createRoute({
				getParentRoute: () => rootRoute,
				path: '/operate/processes',
				validateSearch: processesSearchSchema,
				search: {middlewares: [stripLegacyProcessFilters]},
			});
			const router = createRouter({
				routeTree: rootRoute.addChildren([processesRoute]),
				history: createMemoryHistory({initialEntries: [initialEntry]}),
				parseSearch: parseSearchWith(parseSearchValueSafe),
				stringifySearch: stringifySearchWith(JSON.stringify, parseSearchValueSafe),
			});

			await router.load();
			return router;
		};
		const legacyEntry =
			'/operate/processes?processDefinitionId=orders&processDefinitionVersion=2&tenantId=%3Ctenant-A%3E&errorMessage=Connection%20timeout&incidentErrorHashCode=-481';

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

		it('should restore a numeric-looking legacy process ID after URL parsing', () => {
			expect(processesSearchSchema.parse({processDefinitionId: 123, processDefinitionVersion: 2})).toMatchObject({
				process: '123',
				version: 2,
			});
		});

		it('should map validated legacy incident links into tenant-scoped API filters', () => {
			const search = processesSearchSchema.parse({
				processDefinitionId: 'orders',
				processDefinitionVersion: '2',
				tenantId: '<tenant-A>',
				errorMessage: 'Connection timeout',
				incidentErrorHashCode: '-481',
				active: false,
				suspended: false,
			});

			expect(mapProcessInstancesFilter(search)).toMatchObject({
				hasIncident: true,
				processDefinitionId: {$eq: 'orders'},
				processDefinitionVersion: 2,
				tenantId: {$eq: '<tenant-A>'},
				errorMessage: {$in: ['Connection timeout']},
				incidentErrorHashCode: {$eq: -481},
			});
		});

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

		it.for([
			{action: 'choose all versions', changes: {version: undefined}, expected: {process: 'orders', version: undefined}},
			{
				action: 'clear the process',
				changes: {process: undefined, version: undefined, elementId: undefined},
				expected: {process: undefined, version: undefined},
			},
			{
				action: 'select another process',
				changes: {process: 'invoices', version: undefined, elementId: undefined},
				expected: {process: 'invoices', version: undefined},
			},
			{
				action: 'change the tenant',
				changes: {tenantId: '<tenant-B>', process: undefined, version: undefined, elementId: undefined},
				expected: {tenantId: '<tenant-B>', process: undefined, version: undefined},
			},
		] as const)('should not restore legacy filters when users $action', async ({changes, expected}) => {
			const router = await createProcessesSearchRouter(legacyEntry);
			expect(router.state.matches.at(-1)?.search).toMatchObject({
				process: 'orders',
				version: 2,
				tenantId: '<tenant-A>',
			});

			await router.navigate({to: '/operate/processes', search: (search) => ({...search, ...changes})});
			expect(router.state.matches.at(-1)?.search).toMatchObject({
				tenantId: '<tenant-A>',
				errorMessage: 'Connection timeout',
				incidentErrorHashCode: -481,
				...expected,
			});
			expect(router.state.location.href).not.toContain('processDefinitionId');
			expect(router.state.location.href).not.toContain('processDefinitionVersion');

			const reloaded = await createProcessesSearchRouter(router.state.location.href);
			expect(reloaded.state.matches.at(-1)?.search).toMatchObject({
				tenantId: '<tenant-A>',
				errorMessage: 'Connection timeout',
				incidentErrorHashCode: -481,
				...expected,
			});
		});

		it('should preserve legacy bookmark and cleared version through back and forward navigation', async () => {
			const router = await createProcessesSearchRouter(legacyEntry);

			await router.navigate({to: '/operate/processes', search: (search) => ({...search, version: undefined})});
			const clearedHref = router.state.location.href;
			router.history.back();
			await router.load();
			expect(router.state.matches.at(-1)?.search).toMatchObject({version: 2});
			expect(router.state.location.href).toContain('processDefinitionVersion=2');

			router.history.forward();
			await router.load();
			expect(router.state.matches.at(-1)?.search).toMatchObject({version: undefined});
			expect(router.state.location.href).toBe(clearedHref);
			expect(router.state.matches.at(-1)?.search).toMatchObject({
				process: 'orders',
				tenantId: '<tenant-A>',
				errorMessage: 'Connection timeout',
				incidentErrorHashCode: -481,
			});
		});

		it('should reset legacy bookmarks without restoring process, version, or tenant', async () => {
			const router = await createProcessesSearchRouter(legacyEntry);

			await router.navigate({to: '/operate/processes', search: {}});
			expect(router.state.matches.at(-1)?.search).toMatchObject({
				process: undefined,
				version: undefined,
			});
			expect(router.state.matches.at(-1)?.search).not.toHaveProperty('tenantId');
			expect(router.state.matches.at(-1)?.search).not.toHaveProperty('incidentErrorHashCode');
			expect(router.state.location.href).not.toContain('processDefinitionId');
			expect(router.state.location.href).not.toContain('processDefinitionVersion');
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
