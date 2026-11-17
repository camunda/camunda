/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	parseSearchWith,
	stringifySearchWith,
} from '@tanstack/react-router';
import {parseSearchValueSafe} from '#/shared/parseSearchValueSafe';
import {processesSearchSchema} from '#/operate/pages/Processes/processesFilter';
import {it} from '#/vitest-modules/test-extend';
import {migrateLegacyProcessSearch, preserveProcessSearchStrings} from './migrateLegacyProcessSearch';

function readSearch(searchStr: string) {
	const url = preserveProcessSearchStrings({url: new URL(`/operate/processes${searchStr}`, 'http://localhost')});
	return parseSearchWith(parseSearchValueSafe)(url.search);
}

function migrate(searchStr: string) {
	return migrateLegacyProcessSearch({search: readSearch(searchStr)});
}

describe('migrateLegacyProcessSearch', () => {
	it('should return null when no legacy param is present', () => {
		expect(migrate('?process=order&version=2')).toBeNull();
	});

	it('should rename legacy params and keep the others', () => {
		expect(
			migrate(
				'?process=order&tenant=a&ids=1,2&parentInstanceId=3&flowNodeId=task&operationId=batch&batchOperationId=older&retriesLeft=true&startDateAfter=s1&startDateBefore=s2&endDateAfter=e1&endDateBefore=e2',
			),
		).toEqual({
			process: 'order',
			tenantId: 'a',
			processInstanceKey: '1,2',
			parentProcessInstanceKey: '3',
			elementId: 'task',
			batchOperationKey: 'batch',
			hasRetriesLeft: true,
			startDateFrom: 's1',
			startDateTo: 's2',
			endDateFrom: 'e1',
			endDateTo: 'e2',
		});
	});

	it('should keep the original text of values the router parsed as other types', () => {
		expect(migrate('?tenant=1.0&ids=1e3&flowNodeId=true')).toEqual({
			tenantId: '1.0',
			processInstanceKey: '1e3',
			elementId: 'true',
		});
	});

	it('should keep the current param when both names are present', () => {
		expect(
			migrate(
				'?ids=1&processInstanceKey=2&tenant=old&tenantId=1.00&operationId=old&batchOperationId=older&batchOperationKey=current&retriesLeft=true&hasRetriesLeft=false',
			),
		).toEqual({
			processInstanceKey: '2',
			tenantId: '1.00',
			batchOperationKey: 'current',
			hasRetriesLeft: false,
		});
	});

	it('should preserve raw string identifiers while leaving boolean and numeric filters typed', () => {
		expect(
			readSearch(
				'?process=1e3&tenantId=1.00&elementId=true&elementInstanceKey=false&anchorElementId=null&active=false&version=2',
			),
		).toEqual({
			process: '1e3',
			tenantId: '1.00',
			elementId: 'true',
			elementInstanceKey: 'false',
			anchorElementId: 'null',
			active: false,
			version: 2,
		});
	});

	it('should preserve every string field accepted by the Processes search schema', () => {
		const stringFields = Object.entries(processesSearchSchema.in.shape).flatMap(([key, schema]) => {
			const result = schema.safeParse('1e3');
			return result.success && result.data === '1e3' ? [[key, '1e3']] : [];
		});
		const searchStr = `?${new URLSearchParams(stringFields)}`;

		expect(readSearch(searchStr)).toEqual(Object.fromEntries(stringFields));
	});

	it('should preserve already serialized strings without adding another layer of quotes', () => {
		const search = {process: '1e3', tenantId: '1.00', elementId: 'true', businessId: '"quoted"'};
		const searchStr = stringifySearchWith(JSON.stringify, parseSearchValueSafe)(search);

		expect(readSearch(searchStr)).toEqual(search);
		expect(migrate(searchStr)).toBeNull();
	});

	it('should not rewrite search values outside the process routes', () => {
		for (const path of ['/operate/dashboard', '/tasklist/processes', '/admin/users', '/operate/processes-other']) {
			const url = new URL(`${path}?process=1e3&tenantId=1.00`, 'http://localhost');

			expect(preserveProcessSearchStrings({url}).href).toBe(`http://localhost${path}?process=1e3&tenantId=1.00`);
		}
	});

	it.for(['', '/camunda'])('should preserve identifiers with the router basepath "%s"', async (basepath) => {
		const rootRoute = createRootRoute();
		const processesRoute = createRoute({getParentRoute: () => rootRoute, path: '/operate/processes'});
		const router = createRouter({
			routeTree: rootRoute.addChildren([processesRoute]),
			basepath,
			history: createMemoryHistory({
				initialEntries: [`${basepath}/operate/processes?process=1e3&tenant=1.00&version=2&active=false#diagram`],
			}),
			parseSearch: parseSearchWith(parseSearchValueSafe),
			stringifySearch: stringifySearchWith(JSON.stringify, parseSearchValueSafe),
			rewrite: {input: preserveProcessSearchStrings},
		});

		await router.load();

		expect(router.state.location.search).toEqual({process: '1e3', tenant: '1.00', version: 2, active: false});
		expect(router.state.location.hash).toBe('diagram');

		await router.navigate({to: '/operate/processes', search: {process: 'false', version: 3}});

		expect(router.state.location.search).toEqual({process: 'false', version: 3});
		expect(router.state.location.publicHref).toBe(`${basepath}/operate/processes?process=%22false%22&version=3`);
		router.history.back();
		await router.load();
		expect(router.state.location.search).toEqual({process: '1e3', tenant: '1.00', version: 2, active: false});
	});
});
